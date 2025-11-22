package com.example.filemanager.repository;

import com.example.filemanager.domain.FileEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class FileSpecification {

  public static Specification<FileEntity> isNotDeleted() {
    return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
  }

  public static Specification<FileEntity> nameContains(String name) {
    return (root, query, cb) -> {
      if (!StringUtils.hasText(name)) {
        return cb.conjunction(); // Always true if name is empty
      }
      return cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    };
  }

  public static Specification<FileEntity> tagsContain(String tags) {
    return (root, query, cb) -> {
      if (!StringUtils.hasText(tags)) {
        return cb.conjunction(); // Always true if tags is empty
      }
      // Assuming tags are stored as a comma-separated string
      Predicate predicate = cb.like(cb.lower(root.get("customTags")), "%" + tags.toLowerCase() + "%");

      return predicate;
    };
  }
}
