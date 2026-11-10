package com.livebid.tests.udp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.udp.AuctionAnnouncement;
import com.livebid.server.udp.UdpAnnouncer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra {@link UdpAnnouncer}. Dùng {@code 127.0.0.1} làm "nhóm" để gửi unicast thật tới một
 * {@link DatagramSocket} trên cổng ngẫu nhiên — ổn định trên mọi máy, không cần multicast thật.
 */
class UdpAnnouncerTest {

    private static final InetAddress LOOPBACK;

    static {
        try {
            LOOPBACK = InetAddress.getByName("127.0.0.1");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static AuctionView auction(long auctionId, String title, long startsAt) {
        return new AuctionView(auctionId, title, "Mô tả", AuctionStatus.SCHEDULED,
                100_000L, 10_000L, 100_000L, false, null, null,
                startsAt, startsAt + 300_000L, 0, 1L, startsAt);
    }

    @Test
    void guiThongBaoVaTraVeNgay() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(0, LOOPBACK)) {
            receiver.setSoTimeout(2_000);
            UdpAnnouncer announcer = new UdpAnnouncer(LOOPBACK, receiver.getLocalPort(), 1);
            try {
                long startedAt = System.nanoTime();
                announcer.auctionStartingSoon(auction(7, "Bàn phím cơ", 1_760_000_000_000L));
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                assertTrue(elapsedMillis < 100,
                        "auctionStartingSoon phải trả về ngay, thực tế " + elapsedMillis + " ms");

                DatagramPacket packet = new DatagramPacket(new byte[512], 512);
                receiver.receive(packet);
                String json = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        StandardCharsets.UTF_8);
                AuctionAnnouncement announcement = JsonCodec.gson().fromJson(json, AuctionAnnouncement.class);
                assertEquals(7, announcement.auctionId());
                assertEquals("Bàn phím cơ", announcement.title());
                assertEquals(1_760_000_000_000L, announcement.startsAt());
            } finally {
                announcer.close();
            }
        }
    }

    @Test
    void goiSauKhiDongKhongNemLoi() throws Exception {
        UdpAnnouncer announcer = new UdpAnnouncer(LOOPBACK, 5002, 1);
        announcer.close();

        assertDoesNotThrow(() -> announcer.auctionStartingSoon(auction(1, "Đã đóng", 0L)));
        assertDoesNotThrow(announcer::close, "close() gọi lại phải an toàn");
    }
}
