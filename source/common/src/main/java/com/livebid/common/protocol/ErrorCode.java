package com.livebid.common.protocol;

/** Mã lỗi dùng chung cho TCP, WebSocket và REST (REST đổi sang mã HTTP bằng {@link #httpStatus()}). */
public enum ErrorCode {
    /** JSON sai, thiếu trường, lệnh không tồn tại. */
    INVALID_MESSAGE(400),
    /** {@code protocolVersion} không được hỗ trợ. */
    UNSUPPORTED_VERSION(400),
    /** Chưa đăng nhập. */
    UNAUTHORIZED(401),
    /** Đã đăng nhập nhưng không đủ quyền. */
    FORBIDDEN(403),
    /** Dữ liệu sai miền giá trị (giá âm, tên quá dài...). */
    VALIDATION_ERROR(400),
    USERNAME_TAKEN(409),
    INVALID_CREDENTIALS(401),
    AUCTION_NOT_FOUND(404),
    AUCTION_NOT_STARTED(409),
    AUCTION_FINISHED(409),
    /** Đặt giá khi chưa JOIN_AUCTION. */
    NOT_IN_ROOM(409),
    /** Giá thấp hơn mức tối thiểu; {@code details.minimumAllowed} chứa mức tối thiểu. */
    BID_TOO_LOW(409),
    /** Sửa phiên với {@code expectedVersion} đã cũ. */
    VERSION_CONFLICT(409),
    SERVER_BUSY(503),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
