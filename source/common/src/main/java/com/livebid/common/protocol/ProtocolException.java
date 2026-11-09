package com.livebid.common.protocol;

/** Thông điệp nhận được không hợp lệ ở mức giao thức (JSON lỗi, sai phiên bản, thiếu trường...). */
public class ProtocolException extends Exception {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public ProtocolException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ProtocolException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
