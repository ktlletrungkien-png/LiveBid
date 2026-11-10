package com.livebid.server.tcp;

import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageKind;
import com.livebid.common.protocol.MessageType;
import com.livebid.common.protocol.ProtocolException;
import com.livebid.server.core.ServiceException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nhận một dòng JSON từ {@link ClientSession}, kiểm tra phong bì rồi chuyển tới handler của lệnh đó.
 * Mỗi REQUEST nhận đúng một RESPONSE cùng {@code requestId}, kể cả khi lỗi.
 *
 * <p>Router chạy trên luồng đọc của kết nối nên <b>không được chặn</b>: handler trả về
 * {@link CompletableFuture} (thường là future của {@code AuctionService}/{@code AuthService}), router gửi
 * phản hồi khi future hoàn thành. Thứ tự xử lý nghiệp vụ do executor của service quyết định, không phải ở đây.
 *
 * <p>Đổi lỗi thành RESPONSE {@code success=false}:
 * <ul>
 *   <li>JSON lỗi, thiếu trường, sai phiên bản, lệnh lạ, payload sai kiểu → {@code INVALID_MESSAGE} /
 *       {@code UNSUPPORTED_VERSION}; kết nối vẫn mở.</li>
 *   <li>{@link ServiceException} → mã lỗi và {@code details} của nó.</li>
 *   <li>Lỗi khác → {@code INTERNAL_ERROR}, chi tiết chỉ ghi log, không gửi cho client.</li>
 * </ul>
 */
public final class MessageRouter {

    private static final Logger log = LoggerFactory.getLogger(MessageRouter.class);

    /** Xử lý một loại lệnh. Kết quả của future là payload của RESPONSE thành công (có thể null). */
    @FunctionalInterface
    public interface Handler {
        CompletableFuture<?> handle(ClientSession session, Message request) throws ProtocolException;
    }

    private final Map<MessageType, Handler> handlers = new EnumMap<>(MessageType.class);

    /** {@code clock}: nguồn giờ server cho PING (thay được trong test). */
    public MessageRouter(Clock clock) {
        Objects.requireNonNull(clock);
        register(MessageType.PING, (session, request) ->
                CompletableFuture.completedFuture(Map.of("serverTime", clock.millis())));
    }

    /** Đăng ký (hoặc thay) handler cho một lệnh. Chỉ gọi khi khởi động, trước khi mở cổng TCP. */
    public void register(MessageType type, Handler handler) {
        if (type.isEvent()) {
            throw new IllegalArgumentException(type + " là sự kiện, không phải lệnh");
        }
        handlers.put(type, Objects.requireNonNull(handler));
    }

    /** Xử lý một khung đã tách bởi {@link LineFrameDecoder}. Không ném lỗi. */
    public void dispatch(ClientSession session, String frame) {
        Message request;
        try {
            request = JsonCodec.decode(frame);
        } catch (ProtocolException e) {
            session.send(Message.failure((String) null, null, e.code(), e.getMessage(), null));
            return;
        }

        if (request.kind() != MessageKind.REQUEST) {
            reply(session, request, ErrorCode.INVALID_MESSAGE, "Client chỉ được gửi REQUEST");
            return;
        }
        MessageType type = request.typeEnum();
        if (type == null || type.isEvent()) {
            reply(session, request, ErrorCode.INVALID_MESSAGE, "Lệnh không tồn tại: " + request.type());
            return;
        }
        Handler handler = handlers.get(type);
        if (handler == null) {
            reply(session, request, ErrorCode.INTERNAL_ERROR, "Server chưa hỗ trợ lệnh " + type);
            return;
        }

        CompletableFuture<?> result;
        try {
            result = handler.handle(session, request);
        } catch (ProtocolException | RuntimeException e) {
            result = CompletableFuture.failedFuture(e);
        }
        result.whenComplete((payload, error) -> session.send(error == null
                ? Message.success(request, payload)
                : toFailure(session, request, error)));
    }

    private static void reply(ClientSession session, Message request, ErrorCode code, String text) {
        session.send(Message.failure(request, code, text, null));
    }

    private static Message toFailure(ClientSession session, Message request, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof ServiceException e) {
            return Message.failure(request, e.code(), e.getMessage(), e.details());
        }
        if (cause instanceof ProtocolException e) {
            return Message.failure(request, e.code(), e.getMessage(), null);
        }
        log.error("Lỗi khi xử lý {} từ {}", request.type(), session.id(), cause);
        return Message.failure(request, ErrorCode.INTERNAL_ERROR, "Lỗi phía server", null);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable t = error;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
