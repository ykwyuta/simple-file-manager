package com.example.filemanager.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the migrations themselves, on a database this test owns.
 *
 * <p>
 * The application's other tests run <em>after</em> migration and would pass
 * against any schema Flyway happened to produce. These assert what the
 * migrations do -- including the upgrade path for a database that predates
 * Flyway, which no other test can reach once a schema already exists.
 */
class SchemaMigrationTest {

    /** A throwaway H2 database, named per test so runs never share state. */
    private DataSource freshDatabase(String name) {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName(name + ";DB_CLOSE_DELAY=-1")
                .build();
    }

    private Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
    }

    private List<String> appliedMigrations(Flyway flyway) {
        List<String> applied = new ArrayList<>();
        for (MigrationInfo info : flyway.info().applied()) {
            applied.add(info.getVersion().getVersion() + ":" + info.getType());
        }
        return applied;
    }

    @Test
    void anEmptyDatabaseGetsEveryMigration() {
        Flyway flyway = flyway(freshDatabase("migration-empty"));
        flyway.migrate();

        // No baseline marker: an empty database runs V1 rather than assuming it.
        assertEquals(List.of("1:SQL", "2:SQL"), appliedMigrations(flyway));
    }

    @Test
    void migrationsAreIdempotentAcrossRestarts() {
        DataSource dataSource = freshDatabase("migration-restart");
        flyway(dataSource).migrate();

        // Starting the application again must be a no-op, not an error.
        assertDoesNotThrow(() -> flyway(dataSource).migrate());
        assertEquals("2", flyway(dataSource).info().current().getVersion().getVersion());
    }

    @Test
    void theMigratedSchemaHasTheDocumentedIndexes() throws SQLException {
        DataSource dataSource = freshDatabase("migration-indexes");
        flyway(dataSource).migrate();

        Set<String> indexes = new HashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            for (String table : List.of("FILES", "FILE_HISTORY")) {
                try (ResultSet rs = connection.getMetaData()
                        .getIndexInfo(null, null, table, false, false)) {
                    while (rs.next()) {
                        String name = rs.getString("INDEX_NAME");
                        if (name != null) {
                            indexes.add(name.toUpperCase());
                        }
                    }
                }
            }
        }

        // docs/metadata_schema.md specified these; they were documented but never
        // created until the migrations were introduced.
        for (String expected : List.of("IDX_FILES_PARENT", "IDX_FILES_OWNER_USER",
                "IDX_FILES_OWNER_GROUP", "IDX_FILES_NAME", "IDX_FILES_DELETED_AT",
                "IDX_FILE_HISTORY_FILE")) {
            assertTrue(indexes.contains(expected), expected + " missing; have " + indexes);
        }
    }

    @Test
    void everyExpectedTableAndColumnExists() throws SQLException {
        DataSource dataSource = freshDatabase("migration-columns");
        flyway(dataSource).migrate();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String table : List.of("USERS", "GROUPS", "USER_GROUP", "FILES", "FILE_HISTORY")) {
                try (ResultSet rs = metaData.getTables(null, null, table, null)) {
                    assertTrue(rs.next(), "table " + table + " missing");
                }
            }

            Set<String> fileColumns = new HashSet<>();
            try (ResultSet rs = metaData.getColumns(null, null, "FILES", null)) {
                while (rs.next()) {
                    fileColumns.add(rs.getString("COLUMN_NAME").toUpperCase());
                }
            }
            assertTrue(fileColumns.containsAll(List.of("SIZE_BYTES", "CONTENT_TYPE",
                    "VERSIONING_ENABLED", "DELETED_AT", "STORAGE_KEY")), fileColumns.toString());
        }
    }

    /**
     * The upgrade path for a deployment that predates Flyway.
     *
     * <p>
     * Such a database already has the V1 tables, so V1 must not be replayed
     * against it: it is baselined at V1 and picks up from V2. This is the only
     * path that carries live data through a schema change.
     */
    @Test
    void aDatabaseThatPredatesFlywayIsBaselinedAndUpgraded() throws SQLException {
        DataSource dataSource = freshDatabase("migration-legacy");

        // Stand up the pre-Flyway schema and put rows in it, including the null
        // versioning_enabled that V2 has to cope with.
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(readMigration("V1__baseline_schema.sql"));
            statement.execute("INSERT INTO groups (name) VALUES ('admins')");
            statement.execute("INSERT INTO users (username, password) VALUES ('admin', 'hash')");
            statement.execute("INSERT INTO files (name, is_directory, permissions, owner_user_id,"
                    + " owner_group_id, created_at, updated_at, versioning_enabled)"
                    + " VALUES ('legacy.txt', FALSE, 644, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)");
            statement.execute("INSERT INTO files (name, is_directory, permissions, owner_user_id,"
                    + " owner_group_id, created_at, updated_at, versioning_enabled)"
                    + " VALUES ('versioned', TRUE, 755, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE)");
        }

        Flyway flyway = flyway(dataSource);
        flyway.migrate();

        // Baselined at 1, so V1 is recorded as already present and only V2 runs.
        assertEquals(List.of("1:BASELINE", "2:SQL"), appliedMigrations(flyway));

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            // The rows are still there, and the null was backfilled rather than
            // rejected -- adding NOT NULL without the backfill would have failed.
            try (ResultSet rs = statement.executeQuery(
                    "SELECT name, versioning_enabled FROM files ORDER BY name")) {
                assertTrue(rs.next());
                assertEquals("legacy.txt", rs.getString(1));
                assertFalse(rs.getBoolean(2), "null must become false, not stay null");
                assertTrue(rs.next());
                assertEquals("versioned", rs.getString(1));
                assertTrue(rs.getBoolean(2), "an existing true must be preserved");
                assertFalse(rs.next());
            }

            // And the new columns are available on the pre-existing rows.
            try (ResultSet rs = statement.executeQuery(
                    "SELECT size_bytes, content_type FROM files WHERE name = 'legacy.txt'")) {
                assertTrue(rs.next());
                rs.getLong(1);
                assertTrue(rs.wasNull(), "no size is known for rows uploaded before this change");
                assertNull(rs.getString(2));
            }
        }
    }

    private String readMigration(String fileName) {
        try (var in = getClass().getResourceAsStream("/db/migration/" + fileName)) {
            assertNotNull(in, fileName + " not found on the classpath");
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
