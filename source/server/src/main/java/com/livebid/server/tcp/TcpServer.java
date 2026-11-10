package com.livebid.server.tcp;

import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.protocol.Message;
import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mở cổng TCP và nhận kết nối từ desktop client. Mỗi kết nối được giao cho một {@link ClientSession}
 * (luồng đọc + luồng ghi riêng), nên một client chậm hay lỗi không ảnh hưởng client khác.
 *
 * <p>Giới hạn số kết nối: vượt {@code maxConnections} thì gửi {@code SERVER_BUSY} rồi đóng ngay.
 */
public final class TcpServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    /** Cấu hình tầng TCP. {@code idleTimeoutMillis} = 0 nghĩa là không giới hạn thời gian chờ. */
    public record Settings(String host, int port, int maxMessageBytes, int idleTimeoutMillis, int maxConnections) {
    }

    private final Settings settings;
    private final MessageRouter router;
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong nextSessionId = new AtomicLong(1);
    private volatile Consumer<ClientSession> disconnectListener = session -> { };

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public TcpServer(Settings settings, MessageRouter router) {
        this.settings = Objects.requireNonNull(settings);
        this.router = Objects.requireNonNull(router);
    }

    /**
     * Gọi khi một phiên đóng (ví dụ để {@code AuctionService.onDisconnect} cho rời mọi phòng).
     * Đặt trước {@link #start()}.
     */
    public void setDisconnectListener(Consumer<ClientSession> listener) {
        this.disconnectListener = Objects.requireNonNull(listener);
    }

    /** Bind cổng và bắt đầu nhận kết nối trên một luồng riêng. */
    public synchronized void start() throws IOException {
        if (running) {
            throw new IllegalStateException("TcpServer đã chạy");
        }
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        try {
            socket.bind(new InetSocketAddress(settings.host(), settings.port()));
        } catch (BindException e) {
            socket.close();
            BindException clear = new BindException("Cổng TCP " + settings.port()
                    + " đang được dùng (có server khác đang chạy?)");
            clear.initCause(e);
            throw clear;
        }
        serverSocket = socket;
        running = true;
        acceptThread = Thread.ofPlatform().name("tcp-accept").start(this::acceptLoop);
        log.info("TCP server lắng nghe tại {}:{}", settings.host(), localPort());
    }

    /** Cổng thật đang lắng nghe (hữu ích khi cấu hình cổng 0 trong test). */
    public int localPort() {
        return serverSocket.getLocalPort();
    }

    public int connectionCount() {
        return sessions.size();
    }

    /** Ảnh chụp các phiên đang mở. */
    public List<ClientSession> sessions() {
        return List.copyOf(sessions.values());
    }

    /** Chặn đến khi server dừng. */
    public void awaitTermination() throws InterruptedException {
        Thread thread = acceptThread;
        if (thread != null) {
            thread.join();
        }
    }

    /** Dừng nhận kết nối và đóng mọi phiên. Gọi nhiều lần không lỗi. */
    @Override
    public synchronized void close() {
        if (!running) {
            return;
        }
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // đang dừng
        }
        sessions.values().forEach(session -> session.close("server dừng"));
        log.info("TCP server đã dừng");
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (running) {
                    log.error("Lỗi nhận kết nối TCP", e);
                    continue;
                }
                return;
            }
            try {
                handleNewConnection(socket);
            } catch (IOException e) {
                log.warn("Không khởi tạo được kết nối từ {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
                closeQuietly(socket);
            }
        }
    }

    private void handleNewConnection(Socket socket) throws IOException {
        if (sessions.size() >= settings.maxConnections()) {
            log.warn("Từ chối {}: đã đủ {} kết nối", socket.getRemoteSocketAddress(), settings.maxConnections());
            rejectBusy(socket);
            return;
        }
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(settings.idleTimeoutMillis());

        String id = "tcp-" + nextSessionId.getAndIncrement();
        ClientSession session = new ClientSession(id, socket, router, settings.maxMessageBytes(), this::onSessionClosed);
        sessions.put(id, session);
        log.info("Kết nối mới {} từ {}", id, session.remoteAddress());
        session.start();
    }

    private void onSessionClosed(ClientSession session) {
        sessions.remove(session.id());
        try {
            disconnectListener.accept(session);
        } catch (RuntimeException e) {
            log.error("Lỗi khi xử lý ngắt kết nối {}", session.id(), e);
        }
    }

    private static void rejectBusy(Socket socket) {
        try (socket) {
            Message busy = Message.failure((String) null, null, ErrorCode.SERVER_BUSY,
                    "Server đang quá tải, thử lại sau", null);
            OutputStream out = socket.getOutputStream();
            out.write((JsonCodec.encode(busy) + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // client đã đi, không cần báo
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // bỏ qua
        }
    }
}
