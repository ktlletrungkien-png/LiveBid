package com.livebid.server.core;

import com.livebid.common.dto.Role;
import com.livebid.common.dto.UserView;

/**
 * Người dùng đã đăng nhập trên một kết nối. Chỉ server tạo ra đối tượng này (sau LOGIN/AUTH);
 * không bao giờ dựng từ dữ liệu client gửi lên.
 */
public record UserPrincipal(long userId, String username, String displayName, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public UserView toView() {
        return new UserView(userId, username, displayName, role);
    }
}
