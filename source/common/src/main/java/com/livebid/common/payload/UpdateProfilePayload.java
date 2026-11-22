package com.livebid.common.payload;

/**
 * UPDATE_PROFILE: displayName 1–50 ký tự. Muốn đổi mật khẩu thì gửi cả currentPassword và newPassword
 * (≥ 8 ký tự); để trống hoặc null cả hai nghĩa là không đổi mật khẩu.
 */
public record UpdateProfilePayload(String displayName, String currentPassword, String newPassword) {
}
