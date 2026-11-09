package com.livebid.server.model;

import com.livebid.common.dto.Role;

/** Dữ liệu để thêm user mới (chưa có id). */
public record NewUser(
        String username,
        String displayName,
        String passwordHash,
        String passwordSalt,
        int passwordIterations,
        Role role,
        long createdAt) {
}
