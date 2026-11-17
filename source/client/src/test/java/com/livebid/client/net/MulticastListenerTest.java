package com.livebid.client.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.udp.AuctionAnnouncement;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra {@link MulticastListener} qua loopback: gửi unicast tới socket đã bind sẵn trên cổng do hệ điều
 * hành chọn. Không tham gia nhóm multicast thật, không cần server, không cần mạng ngoài.
 */
class MulticastListenerTest {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
    private static final long WAIT_SECONDS = 2;

    private final BlockingQueue<AuctionAnnouncement> received = new LinkedBlockingQueue<>();

    private MulticastListener listener;

    @AfterEach
    void cleanup() {
        if (listener != null) {
            listener.close();
        }
    }

    @Test
    void nhanThongBaoGuiToiCongDaBind() throws Exception {
        int port = startListener();

        send(port, JsonCodec.gson().toJson(new AuctionAnnouncement(42, "Tranh Đông Hồ", 1_700_000_000_000L)));

        AuctionAnnouncement announcement = received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(announcement, "Phải nhận được thông báo gửi tới cổng đang nghe");
        assertEquals(42, announcement.auctionId());
        assertEquals("Tranh Đông Hồ", announcement.title());
        assertEquals(1_700_000_000_000L, announcement.startsAt());
    }

    @Test
    void boQuaGoiRac() throws Exception {
        int port = startListener();

        send(port, "khong-phai-json");
        send(port, "{\"auctionId\":0}");
        send(port, "   ");
        send(port, JsonCodec.gson().toJson(new AuctionAnnouncement(7, "Đồng hồ", 1_700_000_000_000L)));

        AuctionAnnouncement announcement = received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(announcement, "Gói rác không được làm mất gói đúng phía sau");
        assertEquals(7, announcement.auctionId());
        assertNull(received.poll(200, TimeUnit.MILLISECONDS), "Không gói rác nào được lọt ra callback");
    }

    @Test
    void titleThieuThiThanhChuoiRong() throws Exception {
        int port = startListener();

        send(port, "{\"auctionId\":5,\"startsAt\":123}");

        AuctionAnnouncement announcement = received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(announcement);
        assertEquals(5, announcement.auctionId());
        assertEquals("", announcement.title());
        assertEquals(123, announcement.startsAt());
    }

    @Test
    void dongRoiThiKhongNhanNua() throws Exception {
        int port = startListener();
        send(port, JsonCodec.gson().toJson(new AuctionAnnouncement(1, "Một", 1L)));
        assertNotNull(received.poll(WAIT_SECONDS, TimeUnit.SECONDS));

        listener.close();
        received.clear();
        send(port, JsonCodec.gson().toJson(new AuctionAnnouncement(2, "Hai", 2L)));

        assertNull(received.poll(300, TimeUnit.MILLISECONDS), "Sau close() không được nhận thêm");
        listener.close(); // Gọi lần hai vẫn an toàn.
    }

    @Test
    void loiTrongCallbackKhongLamChetLuongNhan() throws Exception {
        BlockingQueue<AuctionAnnouncement> seen = new LinkedBlockingQueue<>();
        MulticastSocket socket = new MulticastSocket(new InetSocketAddress(LOOPBACK, 0));
        int port = socket.getLocalPort();
        listener = new MulticastListener(LOOPBACK, port, announcement -> {
            seen.add(announcement);
            throw new IllegalStateException("callback hỏng có chủ đích");
        }, socket);
        listener.start();

        send(port, JsonCodec.gson().toJson(new AuctionAnnouncement(1, "Một", 1L)));
        assertNotNull(seen.poll(WAIT_SECONDS, TimeUnit.SECONDS));
        send(port, JsonCodec.gson().toJson(new AuctionAnnouncement(2, "Hai", 2L)));

        assertNotNull(seen.poll(WAIT_SECONDS, TimeUnit.SECONDS),
                "Callback ném lỗi ở gói đầu không được làm chết luồng nhận");
    }

    @Test
    void tuChoiCongKhongHopLe() {
        assertThrows(IllegalArgumentException.class, () -> new MulticastListener(LOOPBACK, 0, a -> {
        }));
        assertThrows(IllegalArgumentException.class, () -> new MulticastListener(LOOPBACK, 70_000, a -> {
        }));
    }

    /** Mở socket loopback trên cổng ngẫu nhiên, gắn listener vào socket đó rồi chạy. */
    private int startListener() throws IOException {
        MulticastSocket socket = new MulticastSocket(new InetSocketAddress(LOOPBACK, 0));
        int port = socket.getLocalPort();
        listener = new MulticastListener(LOOPBACK, port, received::add, socket);
        listener.start();
        return port;
    }

    private static void send(int port, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket sender = new DatagramSocket()) {
            sender.send(new DatagramPacket(data, data.length, LOOPBACK, port));
        }
    }
}
