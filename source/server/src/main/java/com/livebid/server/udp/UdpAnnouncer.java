package com.livebid.server.udp;

import com.google.gson.Gson;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.udp.AuctionAnnouncement;
import com.livebid.server.core.AuctionLifecycleListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gửi UDP multicast "phiên sắp bắt đầu" tới nhóm {@code server.multicastGroup:server.multicastPort}
 * (mặc định {@code 239.255.42.99:5002}).
 *
 * <p>Được gọi trên <b>luồng executor nghiệp vụ</b> nên {@link #auctionStartingSoon(AuctionView)} chỉ đưa
 * việc gửi sang một luồng riêng ({@code udp-announcer}) rồi trả về ngay; mọi lỗi mạng chỉ được ghi log,
 * không bao giờ ném ra ngoài.
 *
 * <p>Thông báo này chỉ để hiển thị: mất gói là chấp nhận được vì dữ liệu chuẩn luôn lấy lại qua TCP/REST.
 *
 * <p><b>Hạn chế đã biết:</b> máy có nhiều card mạng (VPN, máy ảo, Wi-Fi + Ethernet) có thể gửi multicast
 * ra sai card mạng; khi đó client không nhận được thông báo. Cách xử lý là cấu hình lại card mạng hoặc bỏ
 * qua thông báo này — không ảnh hưởng tính đúng của phiên đấu giá.
 */
public final class UdpAnnouncer implements AuctionLifecycleListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UdpAnnouncer.class);

    private final InetAddress group;
    private final int port;
    private final int ttl;
    private final Gson gson = JsonCodec.gson();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "udp-announcer");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * @param group địa chỉ nhóm multicast (dùng {@code 127.0.0.1} trong test loopback)
     * @param port  cổng đích
     * @param ttl   thời gian sống của gói; 1 = chỉ trong mạng LAN
     */
    public UdpAnnouncer(InetAddress group, int port, int ttl) {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("Cổng multicast không hợp lệ: " + port);
        }
        if (ttl < 0 || ttl > 255) {
            throw new IllegalArgumentException("TTL không hợp lệ: " + ttl);
        }
        this.group = Objects.requireNonNull(group, "group");
        this.port = port;
        this.ttl = ttl;
    }

    @Override
    public void auctionStartingSoon(AuctionView auction) {
        if (auction == null || closed.get()) {
            return;
        }
        try {
            executor.execute(() -> send(auction));
        } catch (RejectedExecutionException e) {
            // Executor đã đóng giữa lúc kiểm tra và lúc gửi — bỏ qua, không ảnh hưởng phiên.
            log.debug("Bỏ qua thông báo phiên {}: {}", auction.auctionId(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Không đưa được thông báo phiên {} vào luồng gửi: {}", auction.auctionId(), e.toString());
        }
    }

    private void send(AuctionView auction) {
        try (MulticastSocket socket = new MulticastSocket()) {
            socket.setTimeToLive(ttl);
            byte[] payload = gson.toJson(new AuctionAnnouncement(auction.auctionId(), auction.title(),
                    auction.startsAt())).getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(payload, payload.length, group, port));
            log.info("Đã gửi multicast phiên {} tới {}:{}", auction.auctionId(), group.getHostAddress(), port);
        } catch (IOException | RuntimeException e) {
            log.warn("Không gửi được multicast phiên {}: {}", auction.auctionId(), e.getMessage());
        }
    }

    /** Dừng luồng gửi. Gọi nhiều lần vẫn an toàn; gọi {@code auctionStartingSoon} sau đó không ném lỗi. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }
}
