package com.livebid.common.payload;

/**
 * UPDATE_PROFILE (TCP/WebSocket) và body của REST {@code PUT /api/me}: người dùng tự sửa thông tin của mình.
 *
 * <p>{@code displayName} bắt buộc (1–50 ký tự). {@code newPassword} để trống = giữ mật khẩu cũ; nếu có thì phải
 * kèm {@code currentPassword} đúng. Không có {@code userId}: luôn sửa tài khoản đang đăng nhập trên kết nối.
 */
public record UpdateProfilePayload(String displayName, String currentPassword, String newPassword) {
}
