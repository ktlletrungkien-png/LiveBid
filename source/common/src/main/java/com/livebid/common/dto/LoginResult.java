package com.livebid.common.dto;

/**
 * Kết quả LOGIN/REGISTER. Qua TCP, {@code token} để null (kết nối TCP tự giữ trạng thái đăng nhập);
 * qua REST, client dùng {@code token} cho header {@code Authorization: Bearer} và lệnh AUTH của WebSocket.
 */
public record LoginResult(UserView user, String token) {
}
