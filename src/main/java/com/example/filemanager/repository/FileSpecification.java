package com.example.filemanager.repository;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.Permission;
import com.example.filemanager.domain.User;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class FileSpecification {

  private FileSpecification() {
  }

  public static Specification<FileEntity> isNotDeleted() {
    return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
  }

  public static Specification<FileEntity> isDeleted() {
    return (root, query, cb) -> cb.isNotNull(root.get("deletedAt"));
  }

  public static Specification<FileEntity> hasParent(FileEntity parent) {
    return (root, query, cb) -> parent == null
        ? cb.isNull(root.get("parent"))
        : cb.equal(root.get("parent"), parent);
  }

  public static Specification<FileEntity> nameContains(String name) {
    return (root, query, cb) -> {
      if (!StringUtils.hasText(name)) {
        return cb.conjunction();
      }
      return cb.like(cb.lower(root.get("name")), "%" + escapeLike(name.toLowerCase()) + "%", '\\');
    };
  }

  public static Specification<FileEntity> tagsContain(String tags) {
    return (root, query, cb) -> {
      if (!StringUtils.hasText(tags)) {
        return cb.conjunction();
      }
      return cb.like(cb.lower(root.get("customTags")), "%" + escapeLike(tags.toLowerCase()) + "%", '\\');
    };
  }

  /**
   * Restricts results to the rows {@code user} may act on with
   * {@code required}.
   *
   * <p>
   * This is the database-side twin of
   * {@link com.example.filemanager.service.PermissionService}. Filtering in SQL
   * rather than in Java is what lets paging report honest totals — a page
   * filtered after the fact reports the unfiltered count and shows short pages.
   *
   * <p>
   * The permission triplet is stored as one decimal integer (e.g. 644), so each
   * digit is isolated with {@code mod} and subtraction only. That keeps the
   * predicate portable across H2 and PostgreSQL, which is not true of integer
   * division.
   */
  public static Specification<FileEntity> isAllowedFor(User user, Permission required) {
    return (root, query, cb) -> {
      if (user == null) {
        return cb.disjunction();
      }
      if (user.isAdmin()) {
        return cb.conjunction();
      }

      Expression<Integer> permissions = root.get("permissions");
      Expression<Integer> ones = cb.mod(permissions, 10);
      Expression<Integer> tens = cb.diff(cb.mod(permissions, 100), ones);
      Expression<Integer> hundreds = cb.diff(permissions, cb.mod(permissions, 100));

      // Exactly one digit applies, in this order: owner, then group, then
      // others -- mirroring how PermissionService short-circuits. Letting more
      // than one digit match would grant access through the group digit to an
      // owner the owner digit denies.
      Predicate isOwner = cb.equal(root.get("owner").get("id"), user.getId());
      Set<Long> groupIds = user.getGroups().stream().map(Group::getId).collect(Collectors.toSet());
      Predicate inGroup = groupIds.isEmpty()
          ? cb.disjunction()
          : root.get("group").get("id").in(groupIds);

      Predicate ownerMatch = cb.and(isOwner, hundreds.in(scaled(required, 100)));
      Predicate groupMatch = cb.and(cb.not(isOwner), inGroup, tens.in(scaled(required, 10)));
      Predicate otherMatch = cb.and(cb.not(isOwner), cb.not(inGroup), ones.in(scaled(required, 1)));

      return cb.or(ownerMatch, groupMatch, otherMatch);
    };
  }

  public static Specification<FileEntity> isReadableBy(User user) {
    return isAllowedFor(user, Permission.READ);
  }

  /** The digit values carrying {@code required}, scaled to their position. */
  private static List<Integer> scaled(Permission required, int factor) {
    return java.util.stream.IntStream.rangeClosed(0, 7)
        .filter(digit -> (digit & required.value) == required.value)
        .map(digit -> digit * factor)
        .boxed()
        .collect(Collectors.toList());
  }

  /** Escapes LIKE metacharacters so a search for "100%" means literally that. */
  private static String escapeLike(String input) {
    return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
