-- Changes from the security and usability review.
--
-- Written to run against a database that has been live: every statement is
-- safe on populated tables, and the NOT NULL constraints are only applied
-- after existing rows have been given a value.

-- The file list showed no size and downloads were always octet-stream, because
-- neither was recorded. Nullable: rows uploaded before this migration have no
-- recorded size, and guessing one would be worse than showing nothing.
ALTER TABLE files ADD COLUMN size_bytes BIGINT;
ALTER TABLE files ADD COLUMN content_type VARCHAR(255);

-- versioning_enabled was a nullable Boolean for a two-state concept. A null
-- could not be coerced in the template's boolean expression, so opening any
-- folder that had never toggled versioning failed to render.
UPDATE files SET versioning_enabled = FALSE WHERE versioning_enabled IS NULL;
ALTER TABLE files ALTER COLUMN versioning_enabled SET DEFAULT FALSE;
ALTER TABLE files ALTER COLUMN versioning_enabled SET NOT NULL;

-- Names are validated to 64 characters now; narrowing the columns keeps the
-- database in agreement with the application rather than trusting it.
ALTER TABLE users ALTER COLUMN username SET DATA TYPE VARCHAR(64);
ALTER TABLE groups ALTER COLUMN name SET DATA TYPE VARCHAR(64);

-- The index strategy in docs/metadata_schema.md was documented but never
-- created. Listing, search, navigation and the deletion job all filter on
-- these columns, and listing and search now filter permissions in SQL too.
CREATE INDEX idx_files_parent ON files (parent_folder_id);
CREATE INDEX idx_files_owner_user ON files (owner_user_id);
CREATE INDEX idx_files_owner_group ON files (owner_group_id);
CREATE INDEX idx_files_name ON files (name);
CREATE INDEX idx_files_deleted_at ON files (deleted_at);

-- Version lookups are always scoped to one file, never resolved by version id
-- alone: doing that let a caller graft another user's stored object onto a
-- file they owned.
CREATE INDEX idx_file_history_file ON file_history (file_entity_id);
