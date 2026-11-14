package com.livebid.server.core;

import com.livebid.common.protocol.Message;

/**
 * Một kết nối tới client, không phân biệt TCP hay WebSocket. {@code AuctionService} và {@code RoomManager}
 * chỉ làm việc với interface này.
 *
 * <ul>
 *   <li>TCP: {@code ClientSession} (package {@code server.tcp}, Kiên) cài đặt.</li>
 *   <li>WebSocket: lớp bọc {@code WsContext} (package {@code server.http}, Hoè) cài đặt.</li>
 * </ul>
 */
public interface ClientChannel {

    /** Mã duy nhất của kết nối, dùng làm khóa trong RoomManager và log. */
    String id();

    Transport transport();

    /** Địa chỉ phía client, chỉ để log. */
    String remoteAddress();

    /**
     * Đưa thông điệp vào hàng đợi gửi của kết nối này rồi trả về ngay.
     *
     * <p><b>Không được chặn</b>: hàm này được gọi từ executor nghiệp vụ. Hàng đợi có giới hạn; nếu đầy
     * thì đóng kết nối (client quá chậm) thay vì chờ.
     */
    void send(Message message);

    /** Đóng kết nối; gọi nhiều lần không lỗi. */
    void close(String reason);

    boolean isOpen();

    /**
     * Người dùng đã đăng nhập trên kết nối này; {@code null} nếu chưa. Một kết nối đại diện cho đúng một tài
     * khoản, đóng kết nối là mất đăng nhập. Đặt ở interface để cùng một router xử lý được cả TCP và WebSocket.
     */
    UserPrincipal principal();

    /** Gán sau khi đăng nhập thành công (LOGIN/REGISTER/AUTH); {@code null} khi LOGOUT. */
    void setPrincipal(UserPrincipal principal);

    enum Transport {
        TCP,
        WEBSOCKET
    }
}
