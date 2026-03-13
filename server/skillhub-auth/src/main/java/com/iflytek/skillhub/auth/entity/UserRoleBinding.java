package com.iflytek.skillhub.auth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_role_binding",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"}))
public class UserRoleBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected UserRoleBinding() {}

    public UserRoleBinding(String userId, Role role) {
        this.userId = userId;
        this.role = role;
    }

    @PrePersist
    void prePersist() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Role getRole() { return role; }
}
