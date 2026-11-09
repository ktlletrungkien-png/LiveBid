package com.livebid.common.protocol;

/** Ba loại thông điệp trên kênh TCP/WebSocket. */
public enum MessageKind {
    /** Client → server: yêu cầu làm một việc. Luôn có {@code requestId}. */
    REQUEST,
    /** Server → client: trả lời đúng một REQUEST (cùng {@code requestId}). */
    RESPONSE,
    /** Server → client: server chủ động đẩy, không gắn với yêu cầu nào. */
    EVENT
}
