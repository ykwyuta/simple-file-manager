package com.example.filemanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "users")
public class User implements UserDetails {

    /** Members of this group bypass file permission checks. */
    public static final String ADMIN_GROUP = "admins";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "ユーザー名を入力してください。")
    @Size(max = 64, message = "ユーザー名は64文字以内で入力してください。")
    @Pattern(regexp = "[A-Za-z0-9._@-]+",
            message = "ユーザー名に使用できるのは英数字と . _ @ - のみです。")
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @NotBlank(message = "パスワードを入力してください。")
    @Column(nullable = false)
    private String password;

    @ManyToMany(cascade = { CascadeType.MERGE }, fetch = FetchType.EAGER)
    @JoinTable(name = "user_group", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<Group> groups = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<Group> getGroups() {
        return groups;
    }

    public void setGroups(Set<Group> groups) {
        this.groups = groups;
    }

    /** True when this user belongs to the administrator group. */
    @JsonIgnore
    public boolean isAdmin() {
        return groups.stream().anyMatch(g -> ADMIN_GROUP.equals(g.getName()));
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return groups.stream()
                .map(group -> new SimpleGrantedAuthority("ROLE_" + group.getName().toUpperCase()))
                .collect(Collectors.toSet());
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return true;
    }

    /**
     * Identity is the persistent id.
     *
     * <p>
     * Lazy associations hand back Hibernate proxies, so reference equality
     * silently fails when comparing a loaded association to the authenticated
     * principal. Unsaved instances (id == null) are only equal to themselves.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User)) {
            return false;
        }
        Long otherId = ((User) other).getId();
        return id != null && id.equals(otherId);
    }

    @Override
    public int hashCode() {
        return User.class.hashCode();
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "'}";
    }
}
