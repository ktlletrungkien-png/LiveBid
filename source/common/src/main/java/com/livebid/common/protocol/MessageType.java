package com.livebid.common.protocol;

/**
 * Tên lệnh (REQUEST/RESPONSE) và tên sự kiện (EVENT).
 *
 * <p>Trên đường truyền, trường {@code type} là chuỗi. Dùng {@link #parse(String)} để đổi sang enum;
 * chuỗi lạ trả về {@code null} và phía nhận phải trả lỗi {@link ErrorCode#INVALID_MESSAGE}.
 */
public enum MessageType {

    // ---- Lệnh (client gửi) ----
    PING(false),
    /** Chỉ dùng trên WebSocket: gửi token lấy từ REST /api/login. */
    AUTH(false),
    REGISTER(false),
    LOGIN(false),
    LOGOUT(false),
    LIST_AUCTIONS(false),
    GET_AUCTION(false),
    JOIN_AUCTION(false),
    LEAVE_AUCTION(false),
    PLACE_BID(false),
    GET_BID_HISTORY(false),
    GET_MY_AUCTIONS(false),
    CREATE_AUCTION(false),
    UPDATE_AUCTION(false),

    // ---- Sự kiện (server đẩy) ----
    AUCTION_STARTED(true),
    AUCTION_UPDATED(true),
    AUCTION_EXTENDED(true),
    AUCTION_FINISHED(true);

    private final boolean event;

    MessageType(boolean event) {
        this.event = event;
    }

    public boolean isEvent() {
        return event;
    }

    /** Đổi chuỗi sang enum; trả {@code null} nếu không có lệnh/sự kiện này. */
    public static MessageType parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MessageType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
