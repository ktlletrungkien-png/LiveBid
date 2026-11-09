package com.livebid.server.core;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Đăng ký, đăng nhập, token cho web. <b>Hoè cài đặt</b> (package {@code server.auth});
 * tầng TCP (Kiên) và REST/WebSocket (Hoè) gọi vào.
 *
 * <p>Quy tắc:
 * <ul>
 *   <li>username: 3–30 ký tự [a-zA-Z0-9_], lưu chữ thường, không trùng; password ≥ 8 ký tự.</li>
 *   <li>Đăng ký công khai luôn tạo USER.</li>
 *   <li>Mật khẩu băm bằng PBKDF2WithHmacSHA256, salt riêng; băm chạy ở worker riêng, không chạy trên
 *       executor nghiệp vụ. Không log mật khẩu.</li>
 * </ul>
 *
 * <p>Lỗi: future hoàn thành với {@link ServiceException} mang mã {@code VALIDATION_ERROR},
 * {@code USERNAME_TAKEN} hoặc {@code INVALID_CREDENTIALS}.
 */
public interface AuthService {

    CompletableFuture<UserPrincipal> register(String username, String password, String displayName);

    CompletableFuture<UserPrincipal> login(String username, String password);

    /** Cấp token cho web (REST + WebSocket AUTH). Lưu trong bộ nhớ ở bản đầu. */
    String issueToken(UserPrincipal user);

    /** Token → người dùng; rỗng nếu token sai hoặc đã thu hồi. */
    Optional<UserPrincipal> resolveToken(String token);

    void revokeToken(String token);
}
