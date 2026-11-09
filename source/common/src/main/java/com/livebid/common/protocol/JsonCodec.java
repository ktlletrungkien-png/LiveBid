package com.livebid.common.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.livebid.common.Protocol;

/**
 * Đọc/ghi JSON dùng chung cho mọi module. Không tự tạo {@code new Gson()} ở chỗ khác,
 * để cả nhóm có cùng một cấu hình.
 *
 * <p>{@link #encode(Message)} luôn trả về JSON <b>một dòng</b> (Gson thoát ký tự xuống dòng trong chuỗi),
 * nên an toàn cho khung "mỗi thông điệp một dòng" của TCP. Ký tự {@code '\n'} kết thúc do tầng
 * framing thêm vào, không nằm trong chuỗi trả về.
 */
public final class JsonCodec {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private JsonCodec() {
    }

    public static Gson gson() {
        return GSON;
    }

    /** Thông điệp → JSON một dòng (chưa có '\n'). */
    public static String encode(Message message) {
        return GSON.toJson(message);
    }

    /**
     * JSON một dòng → thông điệp, kèm kiểm tra phong bì.
     *
     * @throws ProtocolException {@code INVALID_MESSAGE} nếu JSON lỗi hoặc thiếu trường bắt buộc;
     *                           {@code UNSUPPORTED_VERSION} nếu sai phiên bản giao thức
     */
    public static Message decode(String json) throws ProtocolException {
        Message message;
        try {
            message = GSON.fromJson(json, Message.class);
        } catch (JsonParseException | IllegalStateException e) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "JSON không hợp lệ", e);
        }
        if (message == null) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "Thông điệp rỗng");
        }
        if (message.protocolVersion() == null) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "Thiếu protocolVersion");
        }
        if (message.protocolVersion() != Protocol.PROTOCOL_VERSION) {
            throw new ProtocolException(ErrorCode.UNSUPPORTED_VERSION,
                    "Chỉ hỗ trợ protocolVersion " + Protocol.PROTOCOL_VERSION);
        }
        if (message.kind() == null) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "Thiếu hoặc sai kind");
        }
        if (message.type() == null || message.type().isBlank()) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "Thiếu type");
        }
        if (message.kind() != MessageKind.EVENT
                && (message.requestId() == null || message.requestId().isBlank())) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "Thiếu requestId");
        }
        return message;
    }

    /** Đối tượng bất kỳ → cây JSON (dùng cho payload, details). */
    public static JsonElement toTree(Object value) {
        return GSON.toJsonTree(value);
    }

    /** Cây JSON → đối tượng; trả {@code null} nếu {@code element} null. */
    public static <T> T fromTree(JsonElement element, Class<T> type) {
        return element == null || element.isJsonNull() ? null : GSON.fromJson(element, type);
    }
}
