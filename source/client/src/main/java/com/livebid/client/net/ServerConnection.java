package com.livebid.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.livebid.common.Protocol;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.ErrorInfo;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageKind;
import com.livebid.common.protocol.MessageType;
import com.livebid.common.protocol.ProtocolException;
import com.livebid.common.transport.FrameTooLargeException;
import com.livebid.common.transport.LineFrameDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Một kết nối TCP tới server LiveBid: gửi REQUEST, ghép RESPONSE theo {@code requestId}, nhận EVENT.
 *
 * <p>Lớp này <b>không phụ thuộc Swing</b> nên test được bằng một {@code ServerSocket} giả. Giao diện chỉ
 * việc gọi {@link #request}/{@link #call} và nghe listener.
 *
 * <p><b>Luồng:</b>
 *
 * <ul>
 *   <li>{@code client-read} (daemon): đọc byte, tách khung bằng {@link LineFrameDecoder} rồi
 *       {@link JsonCodec#decode(String)}. Chỉ luồng này gọi {@code feed} vì bộ tách không an toàn đa luồng.</li>
 *   <li>{@code client-write} (daemon): lấy thông điệp từ hàng đợi, ghi {@code JSON + '\n'} và flush.</li>
 *   <li>{@code client-heartbeat} (daemon): gửi {@code PING} mỗi {@value #HEARTBEAT_MILLIS} ms để server
 *       không đóng kết nối vì im lặng quá 45 giây.</li>
 * </ul>
 *
 * <p>Mỗi yêu cầu có timeout {@value #REQUEST_TIMEOUT_MILLIS} ms. Mất kết nối thì mọi future đang chờ hoàn
 * thành lỗi {@link IOException} và trạng thái chuyển {@link ConnectionState#DISCONNECTED}.
 *
 * <p><b>Listener được gọi trên luồng mạng</b> — tầng giao diện phải tự chuyển về EDT bằng
 * {@code SwingUtilities.invokeLater}.
 */
public final class ServerConnection implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(ServerConnection.class.getName());

    /** Thời gian tối đa để mở socket (mili giây). */
    public static final int CONNECT_TIMEOUT_MILLIS = 3_000;

    /** Thời gian tối đa chờ RESPONSE của một yêu cầu (mili giây). */
    public static final long REQUEST_TIMEOUT_MILLIS = 10_000;

    /** Chu kỳ gửi PING (mili giây); server đóng kết nối im lặng quá 45 giây. */
    public static final long HEARTBEAT_MILLIS = 15_000;

    private static final int READ_BUFFER_BYTES = 8 * 1024;

    private final Map<String, CompletableFuture<Message>> pending = new ConcurrentHashMap<>();
    private final BlockingQueue<Message> outgoing = new LinkedBlockingQueue<>();
    private final List<Consumer<Message>> eventListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ConnectionState>> stateListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ErrorInfo>> errorListeners = new CopyOnWriteArrayList<>();

    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.DISCONNECTED);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Socket socket;
    private volatile Thread readThread;
    private volatile Thread writeThread;
    private volatile ScheduledExecutorService heartbeat;

    /** Trạng thái hiện tại; ban đầu là {@link ConnectionState#DISCONNECTED}. */
    public ConnectionState state() {
        return state.get();
    }

    public boolean isConnected() {
        return state.get() == ConnectionState.CONNECTED;
    }

    /**
     * Mở kết nối TCP. Chỉ gọi khi đang {@link ConnectionState#DISCONNECTED}.
     *
     * @param host IP hoặc hostname của server
     * @param port cổng TCP của server
     * @throws IOException khi không kết nối được trong {@value #CONNECT_TIMEOUT_MILLIS} ms
     */
    public void connect(String host, int port) throws IOException {
        Objects.requireNonNull(host, "host");
        String target = host.trim();
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Chưa nhập địa chỉ server");
        }
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("Cổng TCP không hợp lệ: " + port);
        }
        synchronized (this) {
            if (state.get() != ConnectionState.DISCONNECTED) {
                throw new IllegalStateException("Đang có kết nối hoặc đang kết nối; hãy close() trước");
            }
            setState(ConnectionState.CONNECTING);
        }

        Socket opened = new Socket();
        try {
            opened.connect(new InetSocketAddress(target, port), CONNECT_TIMEOUT_MILLIS);
            opened.setTcpNoDelay(true);
        } catch (IOException | RuntimeException e) {
            closeQuietly(opened);
            setState(ConnectionState.DISCONNECTED);
            throw e;
        }

        outgoing.clear();
        socket = opened;
        running.set(true);
        writeThread = newDaemon(this::writeLoop, "client-write");
        readThread = newDaemon(this::readLoop, "client-read");
        setState(ConnectionState.CONNECTED);
        writeThread.start();
        readThread.start();
        startHeartbeat();
    }

    /** Gửi REQUEST với {@code requestId} mới (UUID). */
    public CompletableFuture<Message> request(MessageType type, Object payload) {
        return request(type, UUID.randomUUID().toString(), payload);
    }

    /**
     * Gửi REQUEST với {@code requestId} cho trước — dùng khi cần gửi lại đúng yêu cầu cũ.
     *
     * @return future hoàn thành khi RESPONSE cùng {@code requestId} về, hoặc lỗi sau 10 giây / khi mất kết nối
     */
    public CompletableFuture<Message> request(MessageType type, String requestId, Object payload) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(requestId, "requestId");
        if (type.isEvent()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(type + " là sự kiện, client không gửi"));
        }
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IOException("Chưa kết nối tới server"));
        }
        CompletableFuture<Message> promise = new CompletableFuture<>();
        if (pending.putIfAbsent(requestId, promise) != null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Đang chờ RESPONSE của requestId " + requestId));
        }
        if (!outgoing.offer(Message.request(type, requestId, payload))) {
            pending.remove(requestId, promise);
            return CompletableFuture.failedFuture(new IOException("Hàng đợi gửi đã đầy"));
        }
        return promise.orTimeout(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .whenComplete((message, error) -> pending.remove(requestId, promise));
    }

    /**
     * Gửi REQUEST và đọc payload của RESPONSE thành {@code resultType}.
     *
     * <p>RESPONSE {@code success=false} hoàn thành future bằng {@link RemoteErrorException} mang đúng
     * {@link ErrorInfo} của server.
     */
    public <T> CompletableFuture<T> call(MessageType type, Object payload, Class<T> resultType) {
        Objects.requireNonNull(resultType, "resultType");
        return request(type, payload).thenApply(message -> {
            if (!message.isSuccess()) {
                throw new CompletionException(new RemoteErrorException(errorOf(message)));
            }
            try {
                return message.payloadAs(resultType);
            } catch (ProtocolException e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Như {@link #call(MessageType, Object, Class)} nhưng nhận {@link Type} để đọc kết quả có generic —
     * ví dụ {@code new TypeToken<PageResult<AuctionView>>() { }.getType()}.
     *
     * <p>Cần bản này vì {@code PageResult<AuctionView>} và {@code List<BidView>} mất thông tin generic khi
     * chỉ truyền {@code Class}.
     */
    public <T> CompletableFuture<T> call(MessageType type, Object payload, Type resultType) {
        Objects.requireNonNull(resultType, "resultType");
        return request(type, payload).thenApply(message -> {
            if (!message.isSuccess()) {
                throw new CompletionException(new RemoteErrorException(errorOf(message)));
            }
            JsonElement element = message.payload();
            if (element == null || element.isJsonNull()) {
                return null;
            }
            try {
                return JsonCodec.gson().fromJson(element, resultType);
            } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
                throw new CompletionException(
                        new ProtocolException(ErrorCode.INVALID_MESSAGE, "payload không đúng định dạng", e));
            }
        });
    }

    /** Nghe EVENT server đẩy (AUCTION_UPDATED, AUCTION_FINISHED...). Gọi trên luồng mạng. */
    public void addEventListener(Consumer<Message> listener) {
        eventListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Nghe thay đổi trạng thái kết nối. Gọi trên luồng mạng. */
    public void addStateListener(Consumer<ConnectionState> listener) {
        stateListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Nghe lỗi chung server gửi mà không gắn với yêu cầu nào. Gọi trên luồng mạng. */
    public void addErrorListener(Consumer<ErrorInfo> listener) {
        errorListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Đóng kết nối, hoàn thành lỗi mọi yêu cầu đang chờ. Gọi nhiều lần vẫn an toàn. */
    @Override
    public void close() {
        handleDisconnect();
    }

    // ------------------------------------------------------------------ luồng đọc

    private void readLoop() {
        LineFrameDecoder decoder = new LineFrameDecoder(Protocol.MAX_MESSAGE_BYTES);
        byte[] buffer = new byte[READ_BUFFER_BYTES];
        Socket current = socket;
        try (InputStream in = current.getInputStream()) {
            int read;
            while (running.get() && (read = in.read(buffer)) >= 0) {
                decoder.feed(buffer, 0, read, this::onFrame);
            }
        } catch (FrameTooLargeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Server gửi thông điệp vượt " + e.limitBytes() + " byte; đóng kết nối");
        } catch (IOException e) {
            if (running.get()) {
                LOG.log(System.Logger.Level.DEBUG, "Luồng đọc kết thúc: " + e.getMessage());
            }
        } finally {
            handleDisconnect();
        }
    }

    /** Xử lý một khung đã tách; không bao giờ ném lỗi để luồng đọc sống tiếp. */
    private void onFrame(String frame) {
        Message message;
        try {
            message = JsonCodec.decode(frame);
        } catch (ProtocolException | RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Bỏ qua thông điệp không đọc được: " + e.getMessage());
            return;
        }
        MessageKind kind = message.kind();
        if (kind == MessageKind.RESPONSE) {
            onResponse(message);
        } else if (kind == MessageKind.EVENT) {
            notifyEvent(message);
        } else {
            LOG.log(System.Logger.Level.DEBUG, "Bỏ qua thông điệp client không mong đợi: " + kind);
        }
    }

    private void onResponse(Message message) {
        String requestId = message.requestId();
        if (requestId != null) {
            CompletableFuture<Message> waiting = pending.remove(requestId);
            if (waiting != null) {
                waiting.complete(message);
            } else {
                LOG.log(System.Logger.Level.DEBUG, "RESPONSE không có yêu cầu đang chờ: " + requestId);
            }
            return;
        }
        if (!message.isSuccess() && message.error() != null) {
            notifyError(message.error());
        }
    }

    // ------------------------------------------------------------------ luồng ghi

    private void writeLoop() {
        Socket current = socket;
        try (OutputStream out = current.getOutputStream()) {
            while (running.get()) {
                Message message = outgoing.take();
                byte[] frame = (JsonCodec.encode(message) + "\n").getBytes(StandardCharsets.UTF_8);
                out.write(frame);
                out.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (running.get()) {
                LOG.log(System.Logger.Level.DEBUG, "Luồng ghi kết thúc: " + e.getMessage());
            }
            handleDisconnect();
        }
    }

    // ------------------------------------------------------------------ heartbeat

    private void startHeartbeat() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "client-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeat = scheduler;
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_MILLIS, HEARTBEAT_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat() {
        if (!isConnected()) {
            return;
        }
        request(MessageType.PING, null).whenComplete((message, error) -> {
            if (error != null) {
                LOG.log(System.Logger.Level.DEBUG, "PING thất bại: " + error);
            }
        });
    }

    // ------------------------------------------------------------------ ngắt kết nối

    /** Đóng socket và dọn dẹp đúng một lần; các lần sau không làm gì. */
    private void handleDisconnect() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        closeQuietly(socket);
        socket = null;
        ScheduledExecutorService scheduler = heartbeat;
        heartbeat = null;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        Thread writer = writeThread;
        if (writer != null) {
            writer.interrupt();
        }
        failPending("Mất kết nối tới server");
        setState(ConnectionState.DISCONNECTED);
    }

    private void failPending(String reason) {
        IOException error = new IOException(reason);
        for (String requestId : List.copyOf(pending.keySet())) {
            CompletableFuture<Message> future = pending.remove(requestId);
            if (future != null) {
                future.completeExceptionally(error);
            }
        }
    }

    // ------------------------------------------------------------------ listener

    private void setState(ConnectionState next) {
        ConnectionState previous = state.getAndSet(next);
        if (previous == next) {
            return;
        }
        for (Consumer<ConnectionState> listener : stateListeners) {
            try {
                listener.accept(next);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Listener trạng thái ném lỗi: " + e);
            }
        }
    }

    private void notifyEvent(Message message) {
        for (Consumer<Message> listener : eventListeners) {
            try {
                listener.accept(message);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Listener sự kiện ném lỗi: " + e);
            }
        }
    }

    private void notifyError(ErrorInfo error) {
        for (Consumer<ErrorInfo> listener : errorListeners) {
            try {
                listener.accept(error);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Listener lỗi ném lỗi: " + e);
            }
        }
    }

    // ------------------------------------------------------------------ tiện ích

    private static ErrorInfo errorOf(Message message) {
        ErrorInfo error = message.error();
        return error != null
                ? error
                : new ErrorInfo(ErrorCode.INTERNAL_ERROR, "Server trả lỗi không rõ", null);
    }

    private static Thread newDaemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Đang đóng vì lỗi; không còn gì để làm.
            }
        }
    }
}
