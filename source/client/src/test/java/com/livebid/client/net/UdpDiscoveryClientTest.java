package com.livebid.client.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.Protocol;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.udp.DiscoveryReply;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra {@link UdpDiscoveryClient} qua loopback với một responder giả, cổng do hệ điều hành chọn.
 * Không gửi broadcast thật, không cần server, không cần mạng ngoài.
 */
class UdpDiscoveryClientTest {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private final UdpDiscoveryClient client = new UdpDiscoveryClient();

    private FakeResponder responder;

    @AfterEach
    void cleanup() {
        if (responder != null) {
            responder.close();
        }
    }

    @Test
    void timDuocServerQuaLoopback() throws Exception {
        responder = new FakeResponder(new DiscoveryReply("LiveBid", 5000, 8080), false);

        Optional<UdpDiscoveryClient.DiscoveredServer> found =
                client.discover(responder.port(), 1_000, 1, List.of(LOOPBACK));

        assertTrue(found.isPresent(), "Phải tìm được responder trên loopback");
        assertEquals("127.0.0.1", found.get().address().getHostAddress(),
                "IP server phải lấy từ địa chỉ nguồn gói trả lời");
        assertEquals("LiveBid", found.get().reply().name());
        assertEquals(5000, found.get().reply().tcpPort());
        assertEquals(8080, found.get().reply().httpPort());
    }

    @Test
    void boQuaGoiRacRoiVanNhanGoiDung() throws Exception {
        responder = new FakeResponder(new DiscoveryReply("LiveBid", 5001, 8080), true);

        Optional<UdpDiscoveryClient.DiscoveredServer> found =
                client.discover(responder.port(), 1_000, 1, List.of(LOOPBACK));

        assertTrue(found.isPresent(), "Gói rác không được làm hỏng lần tìm kiếm");
        assertEquals(5001, found.get().reply().tcpPort());
    }

    @Test
    void khongCoResponderThiTraRong() throws Exception {
        int unusedPort = freeUdpPort();

        Optional<UdpDiscoveryClient.DiscoveredServer> found =
                client.discover(unusedPort, 150, 1, List.of(LOOPBACK));

        assertTrue(found.isEmpty(), "Không có ai trả lời thì phải trả Optional.empty()");
    }

    /** Cổng UDP trống: mở rồi đóng ngay để hệ điều hành chọn giúp. */
    private static int freeUdpPort() throws IOException {
        try (DatagramSocket socket = new DatagramSocket(0, LOOPBACK)) {
            return socket.getLocalPort();
        }
    }

    /** Responder giả: trả lời mọi gói {@code LIVEBID_DISCOVER} bằng JSON {@link DiscoveryReply}. */
    private static final class FakeResponder implements AutoCloseable {

        private final DatagramSocket socket;
        private final DiscoveryReply reply;
        private final boolean junkFirst;
        private final Thread thread;

        private volatile boolean running = true;
        private boolean junkSent;

        FakeResponder(DiscoveryReply reply, boolean junkFirst) throws IOException {
            this.reply = reply;
            this.junkFirst = junkFirst;
            this.socket = new DatagramSocket(0, LOOPBACK);
            this.thread = new Thread(this::run, "fake-discovery-responder");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        private void run() {
            byte[] buffer = new byte[512];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String text = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                            StandardCharsets.UTF_8).trim();
                    if (!Protocol.DISCOVERY_REQUEST.equals(text)) {
                        continue;
                    }
                    if (junkFirst && !junkSent) {
                        junkSent = true;
                        send(packet.getSocketAddress(), "khong-phai-json");
                    }
                    send(packet.getSocketAddress(), JsonCodec.gson().toJson(reply));
                } catch (SocketException e) {
                    return;
                } catch (IOException e) {
                    if (running) {
                        continue;
                    }
                }
            }
        }

        private void send(SocketAddress target, String text) throws IOException {
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length, target));
        }

        @Override
        public void close() {
            running = false;
            socket.close();
        }
    }
}
