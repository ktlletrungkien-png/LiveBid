package com.livebid.server.udp;

import com.google.gson.Gson;
import com.livebid.common.Protocol;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.udp.DiscoveryReply;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trả lời gói UDP broadcast "server ở đâu?" của desktop client.
 *
 * <p>Client gửi chuỗi {@link Protocol#DISCOVERY_REQUEST} ("LIVEBID_DISCOVER") tới cổng
 * {@code server.udpDiscoveryPort} (mặc định 5001); server trả về JSON của {@link DiscoveryReply}. Gói trả
 * lời <b>không chứa IP</b>: client lấy IP từ địa chỉ nguồn của gói nhận được, nhờ vậy cùng một server
 * chạy được ở nhiều card mạng mà không phải tự đoán IP.
 *
 * <p>Chỉ đúng chuỗi mới được trả lời; gói khác bị bỏ qua im lặng để không khuếch đại lưu lượng.
 */
public final class UdpDiscoveryResponder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UdpDiscoveryResponder.class);

    /** Gói discovery rất nhỏ; bộ đệm 512 byte là quá đủ và tránh cấp phát lớn cho mỗi gói. */
    private static final int BUFFER_SIZE = 512;

    private final String bindHost;
    private final int configuredPort;
    private final DiscoveryReply reply;
    private final Gson gson = JsonCodec.gson();

    private DatagramSocket socket;
    private Thread worker;
    private volatile boolean running;

    /**
     * @param bindHost địa chỉ cần bind, {@code null}/rỗng = mọi card mạng
     * @param port     cổng cần bind; {@code 0} để hệ điều hành chọn cổng (dùng trong test)
     * @param reply    nội dung trả lời; không chứa IP
     */
    public UdpDiscoveryResponder(String bindHost, int port, DiscoveryReply reply) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Cổng UDP không hợp lệ: " + port);
        }
        this.bindHost = bindHost;
        this.configuredPort = port;
        this.reply = Objects.requireNonNull(reply, "reply");
    }

    /**
     * Bind socket và chạy luồng daemon {@code udp-discovery}. Chỉ gọi một lần.
     *
     * @throws IOException nếu không bind được cổng (đang bị chiếm, thiếu quyền...)
     */
    public synchronized void start() throws IOException {
        if (worker != null) {
            throw new IllegalStateException("UdpDiscoveryResponder đã chạy");
        }
        InetSocketAddress address = (bindHost == null || bindHost.isBlank())
                ? new InetSocketAddress(configuredPort)
                : new InetSocketAddress(bindHost, configuredPort);
        socket = new DatagramSocket(address);
        running = true;
        worker = new Thread(this::runLoop, "udp-discovery");
        worker.setDaemon(true);
        worker.start();
        log.info("UDP discovery lắng nghe tại {}:{}", displayHost(), socket.getLocalPort());
    }

    /** Cổng thực tế đang dùng; trước khi {@link #start()} là cổng cấu hình (có thể là 0). */
    public synchronized int port() {
        return socket == null ? configuredPort : socket.getLocalPort();
    }

    private void runLoop() {
        DatagramSocket current = socket;
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                current.receive(packet);
                String message = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        StandardCharsets.UTF_8).trim();
                if (!Protocol.DISCOVERY_REQUEST.equals(message)) {
                    continue;
                }
                byte[] payload = gson.toJson(reply).getBytes(StandardCharsets.UTF_8);
                current.send(new DatagramPacket(payload, payload.length, packet.getSocketAddress()));
            } catch (SocketException e) {
                if (running) {
                    log.warn("UDP discovery dừng do lỗi socket: {}", e.getMessage());
                }
                break;
            } catch (IOException e) {
                // Một gói lỗi không được làm chết cả luồng phục vụ.
                if (running) {
                    log.warn("Lỗi khi xử lý gói UDP discovery: {}", e.getMessage());
                }
            } catch (RuntimeException e) {
                if (running) {
                    log.warn("Lỗi không mong đợi trong UDP discovery: {}", e.toString());
                }
            }
        }
        log.debug("Luồng UDP discovery đã kết thúc");
    }

    private String displayHost() {
        return bindHost == null || bindHost.isBlank() ? "0.0.0.0" : bindHost;
    }

    /** Đóng socket để vòng lặp {@code receive} thoát và luồng kết thúc. Gọi nhiều lần vẫn an toàn. */
    @Override
    public synchronized void close() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        Thread current = worker;
        worker = null;
        if (current != null) {
            try {
                current.join(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
