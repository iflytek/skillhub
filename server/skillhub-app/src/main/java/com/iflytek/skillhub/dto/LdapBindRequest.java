package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Binds an LDAP identity to the currently authenticated account using the user's LDAP
 * credentials as proof of directory-identity ownership.
 */
public record LdapBindRequest(
    @NotBlank(message = "LDAP 用户名不能为空")
    String username,
    @NotBlank(message = "LDAP 密码不能为空")
    String password
) {}
