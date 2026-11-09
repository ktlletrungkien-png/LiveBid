package com.livebid.common.dto;

/** Thông tin tài khoản được phép gửi cho client (không có mật khẩu). */
public record UserView(long id, String username, String displayName, Role role) {
}
