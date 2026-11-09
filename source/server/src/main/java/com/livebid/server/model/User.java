package com.livebid.server.model;

import com.livebid.common.dto.Role;

/** Một dòng bảng {@code users}. Chỉ dùng trong server; không gửi cho client. */
public record User(
        long id,
        String username,
        String displayName,
        String passwordHash,
        String passwordSalt,
        int passwordIterations,
        Role role,
        long createdAt) {
}
