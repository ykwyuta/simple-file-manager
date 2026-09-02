package com.example.filemanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "files", indexes = {
        // Mirrors the index strategy in docs/metadata_schema.md.
        @Index(name = "idx_files_parent", columnList = "parent_folder_id"),
        @Index(name = "idx_files_owner_user", columnList = "owner_user_id"),
        @Index(name = "idx_files_owner_group", columnList = "owner_group_id"),
        @Index(name = "idx_files_name", columnList = "name"),
        @Index(name = "idx_files_deleted_at", columnList = "deleted_at")
})
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_directory", nullable = false)
    private boolean isDirectory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_group_id", nullable = false)
    private Group group;

    @Column(nullable = false)
    private Integer permissions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private FileEntity parent;

    @Column(name = "storage_key")
    private String storageKey;

    /** Byte length of the stored object. Null for directories. */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** MIME type reported at upload time. Null for directories. */
    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "custom_tags", columnDefinition = "TEXT")
    private String customTags;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Whether new versions are kept for files in this folder.
     *
     * <p>
     * Defaults to {@code FALSE} rather than staying null: a null here made
     * every folder page that had never toggled versioning fail to render with
     * "cannot convert from null to boolean".
     */
    @Column(name = "versioning_enabled", nullable = false)
    private Boolean versioningEnabled = Boolean.FALSE;

    @Column(name = "is_locked")
    private boolean isLocked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locked_by_user_id")
    private User lockedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public Integer getPermissions() {
        return permissions;
    }

    public void setPermissions(Integer permissions) {
        this.permissions = permissions;
    }

    public FileEntity getParent() {
        return parent;
    }

    public void setParent(FileEntity parent) {
        this.parent = parent;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getCustomTags() {
        return customTags;
    }

    public void setCustomTags(String customTags) {
        this.customTags = customTags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Boolean getVersioningEnabled() {
        return versioningEnabled;
    }

    public void setVersioningEnabled(Boolean versioningEnabled) {
        this.versioningEnabled = versioningEnabled == null ? Boolean.FALSE : versioningEnabled;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    public User getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(User lockedBy) {
        this.lockedBy = lockedBy;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }
}
