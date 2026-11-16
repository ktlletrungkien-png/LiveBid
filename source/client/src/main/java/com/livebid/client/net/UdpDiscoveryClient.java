package com.livebid.client.net;

import com.livebid.common.Protocol;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.udp.DiscoveryReply;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Tìm server LiveBid trong mạng LAN bằng UDP broadcast.
 *
 * <p>Gửi chuỗi {@link Protocol#DISCOVERY_REQUEST} tới {@code 255.255.255.255} <b>và</b> địa chỉ broadcast
 * của từng card mạng đang bật, vì Windows thường chỉ gửi {@code 255.255.255.255} ra một card. Server trả
 * lời JSON {@link DiscoveryReply}; IP của server lấy từ <b>địa chỉ nguồn</b> của gói trả lời nên server
 * không phải tự đoán IP của mình.
 *
 * <p>Gói rác hoặc JSON sai cấu trúc bị bỏ qua. Không tìm thấy server thì trả {@link Optional#empty()};
 * giao diện khi đó cho người dùng nhập IP thủ công.
 *
 * <p>Lớp này chạy được trên luồng nền; mỗi lần gọi tự mở và đóng socket riêng.
 */
public final class UdpDiscoveryClient {

    private static final System.Logger LOG = System.getLogger(UdpDiscoveryClient.class.getName());

    /** Gói discovery và gói trả lời đều rất nhỏ. */
    private static final int BUFFER_BYTES = 512;

    /** Server tìm được trong LAN. */
    public record DiscoveredServer(InetAddress address, DiscoveryReply reply) {
    }

    /**
     * Tìm server trên tất cả địa chỉ broadcast của máy.
     *
     * @param port          cổng UDP server lắng nghe discovery (mặc định 5001)
     * @param timeoutMillis thời gian chờ mỗi lần thử
     * @param retries       số lần gửi lại gói tìm kiếm
     */
    public Optional<DiscoveredServer> discover(int port, int timeoutMillis, int retries) {
        return discover(port, timeoutMillis, retries, broadcastTargets());
    }

    /**
     * Như {@link #discover(int, int, int)} nhưng gửi tới danh sách địa chỉ cho trước — dùng trong test với
     * {@code 127.0.0.1}.
     */
    public Optional<DiscoveredServer> discover(int port, int timeoutMillis, int retries,
            List<InetAddress> targets) {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("Cổng UDP không hợp lệ: " + port);
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Thời gian chờ phải > 0: " + timeoutMillis);
        }
        if (retries <= 0) {
            throw new IllegalArgumentException("Số lần thử phải > 0: " + retries);
        }
        Objects.requireNonNull(targets, "targets");
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("Chưa có địa chỉ nào để tìm server");
        }

        byte[] request = Protocol.DISCOVERY_REQUEST.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            for (int attempt = 1; attempt <= retries; attempt++) {
                for (InetAddress target : targets) {
                    try {
                        socket.send(new DatagramPacket(request, request.length, target, port));
                    } catch (IOException e) {
                        // Một card mạng lỗi không được làm hỏng cả lần tìm kiếm.
                        LOG.log(System.Logger.Level.DEBUG,
                                "Không gửi được gói tìm server tới " + target + ": " + e.getMessage());
                    }
                }
                Optional<DiscoveredServer> found = awaitReply(socket, timeoutMillis);
                if (found.isPresent()) {
                    return found;
                }
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Không mở được socket UDP: " + e.getMessage());
        }
        return Optional.empty();
    }

    private static Optional<DiscoveredServer> awaitReply(DatagramSocket socket, int timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        byte[] buffer = new byte[BUFFER_BYTES];
        while (true) {
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMillis <= 0) {
                return Optional.empty();
            }
            try {
                socket.setSoTimeout((int) Math.min(remainingMillis, Integer.MAX_VALUE));
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                DiscoveryReply reply = parse(packet);
                if (reply != null) {
                    return Optional.of(new DiscoveredServer(packet.getAddress(), reply));
                }
            } catch (SocketTimeoutException e) {
                return Optional.empty();
            } catch (IOException e) {
                // Ví dụ ICMP "port unreachable" trên Windows: coi như lần thử này không có ai trả lời.
                LOG.log(System.Logger.Level.DEBUG, "Lỗi khi chờ trả lời tìm server: " + e.getMessage());
                return Optional.empty();
            }
        }
    }

    private static DiscoveryReply parse(DatagramPacket packet) {
        String json = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                StandardCharsets.UTF_8).trim();
        if (json.isEmpty()) {
            return null;
        }
        try {
            DiscoveryReply reply = JsonCodec.gson().fromJson(json, DiscoveryReply.class);
            if (reply == null || reply.tcpPort() <= 0 || reply.tcpPort() > 65_535) {
                return null;
            }
            return reply;
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.DEBUG, "Bỏ qua gói trả lời không đọc được: " + e.getMessage());
            return null;
        }
    }

    /** Địa chỉ broadcast giới hạn cộng với broadcast của từng card mạng đang bật, không trùng nhau. */
    public static List<InetAddress> broadcastTargets() {
        Map<String, InetAddress> unique = new LinkedHashMap<>();
        try {
            InetAddress limited = InetAddress.getByName("255.255.255.255");
            unique.put(limited.getHostAddress(), limited);
        } catch (UnknownHostException e) {
            LOG.log(System.Logger.Level.DEBUG, "Không phân giải được 255.255.255.255: " + e.getMessage());
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                try {
                    if (!nic.isUp()) {
                        continue;
                    }
                    for (InterfaceAddress address : nic.getInterfaceAddresses()) {
                        InetAddress broadcast = address.getBroadcast();
                        if (broadcast != null) {
                            unique.putIfAbsent(broadcast.getHostAddress(), broadcast);
                        }
                    }
                } catch (SocketException e) {
                    LOG.log(System.Logger.Level.DEBUG,
                            "Bỏ qua card mạng " + nic.getName() + ": " + e.getMessage());
                }
            }
        } catch (SocketException e) {
            LOG.log(System.Logger.Level.DEBUG, "Không đọc được danh sách card mạng: " + e.getMessage());
        }
        return List.copyOf(unique.values());
    }
}
