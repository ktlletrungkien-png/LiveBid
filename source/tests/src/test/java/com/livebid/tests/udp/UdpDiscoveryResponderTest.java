package com.livebid.tests.udp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.Protocol;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.udp.DiscoveryReply;
import com.livebid.server.udp.UdpDiscoveryResponder;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Kiểm tra {@link UdpDiscoveryResponder} qua loopback, cổng do hệ điều hành chọn. */
class UdpDiscoveryResponderTest {

    private static final String LOOPBACK = "127.0.0.1";

    private UdpDiscoveryResponder responder;

    @AfterEach
    void cleanup() {
        if (responder != null) {
            responder.close();
        }
    }

    @Test
    void traLoiDungGoiDiscovery() throws Exception {
        responder = new UdpDiscoveryResponder(LOOPBACK, 0, new DiscoveryReply("LiveBid", 5000, 8080));
        responder.start();
        assertTrue(responder.port() > 0, "Phải bind được cổng thực tế");

        try (DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(2_000);
            send(client, Protocol.DISCOVERY_REQUEST);

            byte[] buffer = new byte[512];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            client.receive(response);

            String json = new String(response.getData(), response.getOffset(), response.getLength(),
                    StandardCharsets.UTF_8);
            DiscoveryReply reply = JsonCodec.gson().fromJson(json, DiscoveryReply.class);
            assertEquals("LiveBid", reply.name());
            assertEquals(5000, reply.tcpPort());
            assertEquals(8080, reply.httpPort());
            assertEquals(responder.port(), response.getPort(), "Trả lời phải đi từ cổng discovery");
        }
    }

    @Test
    void boQuaGoiKhongDungChuoi() throws Exception {
        responder = new UdpDiscoveryResponder(LOOPBACK, 0, new DiscoveryReply("LiveBid", 5000, 8080));
        responder.start();

        try (DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(300);
            send(client, "HELLO_SERVER");
            assertThrows(SocketTimeoutException.class,
                    () -> client.receive(new DatagramPacket(new byte[512], 512)),
                    "Gói lạ không được trả lời");
        }
    }

    @Test
    void chapNhanChuoiCoKhoangTrangThua() throws Exception {
        responder = new UdpDiscoveryResponder(LOOPBACK, 0, new DiscoveryReply("LiveBid", 5001, 8080));
        responder.start();

        try (DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(2_000);
            send(client, "  " + Protocol.DISCOVERY_REQUEST + "\n");
            DatagramPacket response = new DatagramPacket(new byte[512], 512);
            client.receive(response);
            assertTrue(response.getLength() > 0);
        }
    }

    private void send(DatagramSocket client, String message) throws Exception {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        client.send(new DatagramPacket(payload, payload.length, InetAddress.getByName(LOOPBACK), responder.port()));
    }
}
