package com.example.filemanager.repository;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.Permission;
import com.example.filemanager.domain.User;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The permission predicate is what makes paging honest, so it is exercised
 * against a real database rather than asserted on in the abstract.
 *
 * <p>
 * Filtering used to happen in Java after the page had already been fetched,
 * which reported the unfiltered total and produced short pages.
 */
@DataJpaTest
class FileSpecificationTest {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    private User owner;
    private User teammate;
    private User outsider;
    private User administrator;
    private Group team;

    @BeforeEach
    void setUp() {
        team = groupRepository.save(named(new Group(), "team"));
        Group admins = groupRepository.save(named(new Group(), User.ADMIN_GROUP));

        owner = saveUser("owner", team);
        teammate = saveUser("teammate", team);
        outsider = saveUser("outsider", groupRepository.save(named(new Group(), "others")));
        administrator = saveUser("root", admins);
    }

    private Group named(Group group, String name) {
        group.setName(name);
        return group;
    }

    private User saveUser(String username, Group group) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("x");
        user.setGroups(Stream.of(group).collect(Collectors.toSet()));
        return userRepository.save(user);
    }

    private FileEntity file(String name, int permissions) {
        FileEntity file = new FileEntity();
        file.setName(name);
        file.setDirectory(false);
        file.setPermissions(permissions);
        file.setOwner(owner);
        file.setGroup(team);
        return fileRepository.save(file);
    }

    private List<String> readableBy(User user) {
        return fileRepository.findAll(
                FileSpecification.isNotDeleted().and(FileSpecification.isReadableBy(user)))
                .stream().map(FileEntity::getName).sorted().collect(Collectors.toList());
    }

    @Test
    void ownerSeesOnlyWhatTheOwnerDigitAllows() {
        file("owner-readable", 400);
        file("owner-blind", 44);

        assertEquals(List.of("owner-readable"), readableBy(owner));
    }

    @Test
    void groupMembersSeeWhatTheGroupDigitAllows() {
        file("group-readable", 40);
        file("group-blind", 404);

        assertEquals(List.of("group-readable"), readableBy(teammate));
    }

    @Test
    void outsidersSeeOnlyWhatTheOthersDigitAllows() {
        file("world-readable", 4);
        file("private", 770);

        assertEquals(List.of("world-readable"), readableBy(outsider));
    }

    @Test
    void groupDigitTakesPrecedenceOverOthersDigit() {
        // 707: group is denied even though "others" would allow it. Getting
        // this wrong in SQL would list files group members cannot open.
        file("group-denied", 707);

        assertEquals(List.of(), readableBy(teammate));
        assertEquals(List.of("group-denied"), readableBy(outsider));
    }

    @Test
    void ownerDigitTakesPrecedenceOverGroupAndOthers() {
        file("owner-denied", 77);

        assertEquals(List.of(), readableBy(owner));
        assertEquals(List.of("owner-denied"), readableBy(teammate));
    }

    @Test
    void administratorsSeeEverything() {
        file("private", 700);
        file("also-private", 600);

        assertEquals(List.of("also-private", "private"), readableBy(administrator));
    }

    @Test
    void writePredicateMatchesTheWriteBit() {
        file("read-only", 444);
        file("writable", 644);

        List<String> writable = fileRepository.findAll(
                FileSpecification.isNotDeleted()
                        .and(FileSpecification.isAllowedFor(owner, Permission.WRITE)))
                .stream().map(FileEntity::getName).collect(Collectors.toList());

        assertEquals(List.of("writable"), writable);
    }

    @Test
    void deletedFilesAreExcludedUnlessAskedFor() {
        FileEntity live = file("live", 700);
        FileEntity gone = file("gone", 700);
        gone.setDeletedAt(java.time.LocalDateTime.now());
        fileRepository.save(gone);

        assertEquals(List.of("live"), readableBy(owner));
        assertNotNull(live.getId());

        List<String> deleted = fileRepository.findAll(
                FileSpecification.isDeleted().and(FileSpecification.isReadableBy(owner)))
                .stream().map(FileEntity::getName).collect(Collectors.toList());
        assertEquals(List.of("gone"), deleted);
    }

    @Test
    void searchEscapesLikeWildcards() {
        file("100%-report", 700);
        file("1000-report", 700);

        List<String> hits = fileRepository.findAll(
                FileSpecification.isNotDeleted()
                        .and(FileSpecification.nameContains("100%-report"))
                        .and(FileSpecification.isReadableBy(owner)))
                .stream().map(FileEntity::getName).collect(Collectors.toList());

        // "%" is a literal here, not "match anything".
        assertEquals(List.of("100%-report"), hits);
    }

    @Test
    void paginationCountsOnlyRowsTheUserCanSee() {
        for (int i = 0; i < 25; i++) {
            file("private-" + i, 700);
        }
        file("shared-a", 444);
        file("shared-b", 444);

        var page = fileRepository.findAll(
                FileSpecification.isNotDeleted().and(FileSpecification.isReadableBy(outsider)),
                PageRequest.of(0, 20));

        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getTotalPages());
        assertEquals(2, page.getContent().size());
    }
}
