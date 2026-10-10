package com.iflytek.skillhub.auth.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "permission")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "group_code", length = 64)
    private String groupCode;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
