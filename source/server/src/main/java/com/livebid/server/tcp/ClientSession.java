package com.livebid.server.tcp;

import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.protocol.Message;
import com.livebid.server.core.ClientChannel;
import com.livebid.server.core.UserPrincipal;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Một kết nối TCP từ desktop client. Mỗi phiên có:
 *
 * <ul>
 *   <li><b>Một luồng đọc</b>: đọc byte, tách khung bằng {@link LineFrameDecoder}, đưa từng thông điệp cho
 *       {@link MessageRouter}.</li>
 *   <li><b>Một hàng đợi gửi có giới hạn + một luồng ghi duy nhất</b>: {@link #send} chỉ bỏ thông điệp vào
 *       hàng đợi rồi trả về ngay, nên executor nghiệp vụ không bao giờ phải chờ socket. Chỉ một luồng ghi
 *       socket nên hai thông điệp không bao giờ bị trộn byte vào nhau. Hàng đợi đầy (client quá chậm)
 *       thì đóng kết nối thay vì chờ.</li>
 *   <li><b>Trạng thái đăng nhập</b> ({@link #principal()}): một kết nối đại diện cho đúng một tài khoản;
 *       đóng kết nối là mất đăng nhập.</li>
 * </ul>
 *
 * <p>Lỗi trên một kết nối (JSON hỏng, thông điệp quá lớn, mất mạng) chỉ đóng kết nối đó.
 */
public final class ClientSession implements ClientChannel {

    private static final Logger log = LoggerFactory.getLogger(ClientSession.class);

    /** Số thông điệp tối đa chờ gửi cho một client. */
    public static final int SEND_QUEUE_CAPACITY = 256;

    private static final int READ_CHUNK_BYTES = 8192;

    /** Đánh dấu trong hàng đợi: luồng ghi gửi hết những gì đứng trước rồi đóng kết nối. */
    private static final Message CLOSE_AFTER_FLUSH = new Message(null, null, null, null, null, null, null);

    private final String id;
    private final Socket socket;
    private final String remoteAddress;
    private final MessageRouter router;
    private final int maxMessageBytes;
    private final Consumer<ClientSession> onClosed;

    private final BlockingQueue<Message> sendQueue = new ArrayBlockingQueue<>(SEND_QUEUE_CAPACITY);
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile String closeReason = "đóng kết nối";
    private volatile UserPrincipal principal;
    private volatile Thread writerThread;

    /**
     * @param onClosed gọi đúng một lần khi phiên đóng (để TcpServer bỏ khỏi danh sách, rời phòng...)
     */
    public ClientSession(String id, Socket socket, MessageRouter router, int maxMessageBytes,
                         Consumer<ClientSession> onClosed) {
        this.id = Objects.requireNonNull(id);
        this.socket = Objects.requireNonNull(socket);
        this.router = Objects.requireNonNull(router);
        this.maxMessageBytes = maxMessageBytes;
        this.onClosed = Objects.requireNonNull(onClosed);
        this.remoteAddress = String.valueOf(socket.getRemoteSocketAddress());
    }

    /** Khởi động luồng ghi rồi luồng đọc. */
    public void start() {
        writerThread = Thread.ofPlatform().daemon(true).name("tcp-write-" + id).start(this::writeLoop);
        Thread.ofPlatform().daemon(true).name("tcp-read-" + id).start(this::readLoop);
    }

    // ------------------------------------------------------------------ ClientChannel

    @Override
    public String id() {
        return id;
    }

    @Override
    public Transport transport() {
        return Transport.TCP;
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
            socket.close();
        } catch (IOException ignored) {
            // đang đóng, không cần xử lý thêm
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

    // ------------------------------------------------------------------ đăng nhập

    /** Người dùng đã đăng nhập trên kết nối này; null nếu chưa. */
    public UserPrincipal principal() {
        return principal;
    }

    /** Gán sau LOGIN/REGISTER thành công; {@code null} khi LOGOUT. */
    public void setPrincipal(UserPrincipal principal) {
        this.principal = principal;
    }

    // ------------------------------------------------------------------ luồng đọc / ghi

    private void readLoop() {
        LineFrameDecoder decoder = new LineFrameDecoder(maxMessageBytes);
        byte[] chunk = new byte[READ_CHUNK_BYTES];
        try {
            InputStream in = socket.getInputStream();
            int n;
            while ((n = in.read(chunk)) != -1) {
                decoder.feed(chunk, 0, n, frame -> router.dispatch(this, frame));
            }
            close("client đóng kết nối");
        } catch (FrameTooLargeException e) {
            log.warn("{} ({}) gửi thông điệp quá {} byte", id, remoteAddress, e.limitBytes());
            send(Message.failure((String) null, null, ErrorCode.INVALID_MESSAGE, e.getMessage(), null));
            closeAfterFlush("thông điệp quá lớn");
        } catch (SocketTimeoutException e) {
            close("quá thời gian không hoạt động");
        } catch (IOException e) {
            close(closed.get() ? closeReason : "lỗi đọc: " + e.getMessage());
        }
    }

    private void writeLoop() {
        try {
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            while (true) {
                Message message = sendQueue.take();
                if (message == CLOSE_AFTER_FLUSH) {
                    out.flush();
                    close(closeReason);
                    return;
                }
                out.write((JsonCodec.encode(message) + "\n").getBytes(StandardCharsets.UTF_8));
                if (sendQueue.isEmpty()) {
                    out.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            close(closed.get() ? closeReason : "lỗi ghi: " + e.getMessage());
        }
    }

    /** Gửi nốt các thông điệp đang chờ (ví dụ thông báo lỗi) rồi mới đóng. */
    private void closeAfterFlush(String reason) {
        closeReason = reason;
        if (closed.get() || !sendQueue.offer(CLOSE_AFTER_FLUSH)) {
            close(reason);
        }
    }
}
