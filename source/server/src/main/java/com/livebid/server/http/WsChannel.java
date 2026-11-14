package com.livebid.server.http;

import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.protocol.Message;
import com.livebid.server.core.ClientChannel;
import com.livebid.server.core.UserPrincipal;
import io.javalin.websocket.WsContext;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Một kết nối WebSocket từ trình duyệt, cài đặt {@link ClientChannel} để dùng chung {@code MessageRouter}
 * và {@code AuctionService} với TCP.
 *
 * <p>{@link #send} <b>không chặn</b>: chỉ bỏ thông điệp vào hàng đợi (256 phần tử) rồi trả về ngay, vì hàm này
 * được gọi từ executor nghiệp vụ và executor không được chờ mạng. <b>Một luồng ghi riêng</b>
 * ({@code ws-write-<id>}) lấy thông điệp ra và gửi, vì Jetty không cho hai luồng gửi đồng thời trên cùng một
 * session. Hàng đợi đầy (trình duyệt nhận quá chậm) thì đóng kết nối thay vì chờ.
 *
 * <p>Trạng thái đăng nhập ({@link #principal()}) thuộc về kết nối: đóng kết nối là mất đăng nhập, phải gửi
 * lại {@code AUTH} khi kết nối lại.
 */
final class WsChannel implements ClientChannel {

    private static final Logger log = LoggerFactory.getLogger(WsChannel.class);

    /** Số thông điệp tối đa chờ gửi cho một trình duyệt. */
    static final int SEND_QUEUE_CAPACITY = 256;

    /** Mã đóng WebSocket "bình thường" (RFC 6455). */
    private static final int CLOSE_NORMAL = 1000;
    /** Lý do đóng tối đa 123 byte theo RFC 6455; cắt ngắn để chắc chắn không vượt. */
    private static final int MAX_REASON_CHARS = 60;

    private static final AtomicLong COUNTER = new AtomicLong();

    private final String id = "ws-" + COUNTER.incrementAndGet();
    private final WsContext context;
    private final String sessionId;
    private final String remoteAddress;
    private final Consumer<WsChannel> onClosed;

    private final BlockingQueue<Message> sendQueue = new ArrayBlockingQueue<>(SEND_QUEUE_CAPACITY);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile String closeReason = "đóng kết nối";
    private volatile UserPrincipal principal;
    private volatile Thread writerThread;

    /**
     * @param onClosed gọi đúng một lần khi kênh đóng (bỏ khỏi danh sách, rời mọi phòng đấu giá)
     */
    WsChannel(WsContext context, Consumer<WsChannel> onClosed) {
        this.context = Objects.requireNonNull(context);
        this.onClosed = Objects.requireNonNull(onClosed);
        this.sessionId = context.sessionId();
        this.remoteAddress = String.valueOf(context.session.getRemoteAddress());
    }

    /** Khởi động luồng ghi. */
    void start() {
        writerThread = Thread.ofPlatform().daemon(true).name("ws-write-" + id).start(this::writeLoop);
    }

    /** Mã session của Jetty, dùng làm khóa tra cứu kênh từ sự kiện của Javalin. */
    String sessionId() {
        return sessionId;
    }

    // ------------------------------------------------------------------ ClientChannel

    @Override
    public String id() {
        return id;
    }

    @Override
    public Transport transport() {
        return Transport.WEBSOCKET;
    }

    @Override
    public String remoteAddress() {
        return remoteAddress;
    }

    @Override
    public void send(Message message) {
        Objects.requireNonNull(message);
        if (closed.get()) {
            return;
        }
        if (!sendQueue.offer(message)) {
            log.warn("{} ({}) nhận chậm, hàng đợi gửi đầy {} thông điệp", id, remoteAddress, SEND_QUEUE_CAPACITY);
            close("client nhận quá chậm");
        }
    }

    @Override
    public void close(String reason) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeReason = reason;
        log.info("Đóng {} ({}): {}", id, remoteAddress, reason);
        try {
            context.closeSession(CLOSE_NORMAL, truncate(reason));
        } catch (RuntimeException e) {
            // Session có thể đã đóng từ phía trình duyệt; không còn gì để làm.
            log.debug("Đóng session {} lỗi: {}", id, e.toString());
        }
        Thread writer = writerThread;
        if (writer != null) {
            writer.interrupt();
        }
        onClosed.accept(this);
    }

    @Override
    public boolean isOpen() {
        return !closed.get();
    }

    @Override
    public UserPrincipal principal() {
        return principal;
    }

    @Override
    public void setPrincipal(UserPrincipal principal) {
        this.principal = principal;
    }

    // ------------------------------------------------------------------ luồng ghi

    private void writeLoop() {
        try {
            while (true) {
                Message message = sendQueue.take();
                context.send(JsonCodec.encode(message));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            close(closed.get() ? closeReason : "lỗi ghi: " + e.getMessage());
        }
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return "";
        }
        return reason.length() <= MAX_REASON_CHARS ? reason : reason.substring(0, MAX_REASON_CHARS);
    }
}
