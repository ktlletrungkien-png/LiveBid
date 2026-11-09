package com.livebid.common.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.livebid.common.Protocol;
import java.util.Map;
import java.util.UUID;

/**
 * Phong bì của mọi thông điệp TCP/WebSocket.
 *
 * <pre>
 * {"protocolVersion":1,"kind":"REQUEST","type":"PLACE_BID","requestId":"...","payload":{...}}
 * {"protocolVersion":1,"kind":"RESPONSE","type":"PLACE_BID","requestId":"...","success":true,"payload":{...}}
 * {"protocolVersion":1,"kind":"RESPONSE","type":"PLACE_BID","requestId":"...","success":false,"error":{...}}
 * {"protocolVersion":1,"kind":"EVENT","type":"AUCTION_UPDATED","payload":{...}}
 * </pre>
 *
 * <p>Trường null không được ghi ra JSON. {@code type} để dạng chuỗi để thông điệp có lệnh lạ vẫn đọc được
 * và trả lỗi {@code INVALID_MESSAGE}; dùng {@link #typeEnum()} để lấy enum.
 *
 * <p>Luôn tạo thông điệp bằng các hàm {@code request/success/failure/event} bên dưới.
 */
public record Message(
        Integer protocolVersion,
        MessageKind kind,
        String type,
        String requestId,
        JsonElement payload,
        Boolean success,
        ErrorInfo error) {

    // ------------------------------------------------------------------ tạo thông điệp

    /** REQUEST với {@code requestId} ngẫu nhiên (UUID). {@code payload} có thể null. */
    public static Message request(MessageType type, Object payload) {
        return request(type, UUID.randomUUID().toString(), payload);
    }

    public static Message request(MessageType type, String requestId, Object payload) {
        return new Message(Protocol.PROTOCOL_VERSION, MessageKind.REQUEST, type.name(), requestId,
                tree(payload), null, null);
    }

    /** RESPONSE thành công cho {@code request}. */
    public static Message success(Message request, Object payload) {
        return new Message(Protocol.PROTOCOL_VERSION, MessageKind.RESPONSE, request.type(), request.requestId(),
                tree(payload), Boolean.TRUE, null);
    }

    /** RESPONSE thất bại cho {@code request}. {@code details} có thể null. */
    public static Message failure(Message request, ErrorCode code, String text, Map<String, ?> details) {
        return failure(request.type(), request.requestId(), code, text, details);
    }

    /**
     * RESPONSE thất bại khi không đọc được yêu cầu gốc (ví dụ JSON lỗi): {@code type}, {@code requestId}
     * có thể null.
     */
    public static Message failure(String type, String requestId, ErrorCode code, String text, Map<String, ?> details) {
        JsonObject detailJson = details == null ? null : JsonCodec.toTree(details).getAsJsonObject();
        return new Message(Protocol.PROTOCOL_VERSION, MessageKind.RESPONSE, type, requestId,
                null, Boolean.FALSE, new ErrorInfo(code, text, detailJson));
    }

    /** EVENT do server chủ động đẩy. */
    public static Message event(MessageType type, Object payload) {
        if (!type.isEvent()) {
            throw new IllegalArgumentException(type + " không phải sự kiện");
        }
        return new Message(Protocol.PROTOCOL_VERSION, MessageKind.EVENT, type.name(), null,
                tree(payload), null, null);
    }

    // ------------------------------------------------------------------ đọc thông điệp

    /** Enum của {@code type}, hoặc {@code null} nếu là lệnh lạ. */
    public MessageType typeEnum() {
        return MessageType.parse(type);
    }

    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }

    /**
     * Đọc payload thành kiểu cụ thể, ví dụ {@code msg.payloadAs(PlaceBidPayload.class)}.
     *
     * @throws ProtocolException {@code INVALID_MESSAGE} nếu payload sai cấu trúc
     */
    public <T> T payloadAs(Class<T> type) throws ProtocolException {
        try {
            return JsonCodec.fromTree(payload, type);
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "payload không đúng định dạng", e);
        }
    }

    private static JsonElement tree(Object payload) {
        return payload == null ? null : JsonCodec.toTree(payload);
    }
}
