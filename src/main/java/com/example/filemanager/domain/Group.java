package com.example.filemanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "グループ名を入力してください。")
    @Size(max = 64, message = "グループ名は64文字以内で入力してください。")
    @Pattern(regexp = "[A-Za-z0-9._-]+",
            message = "グループ名に使用できるのは英数字と . _ - のみです。")
    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @ManyToMany(mappedBy = "groups")
    private Set<User> users = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonIgnore
    public Set<User> getUsers() {
        return users;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    /** Identity is the persistent id; see {@link User#equals(Object)}. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Group)) {
            return false;
        }
        Long otherId = ((Group) other).getId();
        return id != null && id.equals(otherId);
    }

    @Override
    public int hashCode() {
        return Group.class.hashCode();
    }

    @Override
    public String toString() {
        return "Group{id=" + id + ", name='" + name + "'}";
    }
}
