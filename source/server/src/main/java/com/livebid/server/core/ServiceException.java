package com.livebid.server.core;

import com.livebid.common.protocol.ErrorCode;
import java.util.Map;

/**
 * Lỗi nghiệp vụ có mã. Các service trả lỗi bằng cách cho {@code CompletableFuture} hoàn thành với
 * exception này; tầng TCP/WebSocket đổi thành RESPONSE {@code success=false}, REST đổi thành mã HTTP
 * qua {@link ErrorCode#httpStatus()}.
 */
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ServiceException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ServiceException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? null : Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    /** Có thể null. */
    public Map<String, Object> details() {
        return details;
    }
}
