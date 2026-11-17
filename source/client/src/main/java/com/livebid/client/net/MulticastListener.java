package com.livebid.client.net;

import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.udp.AuctionAnnouncement;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Nhận thông báo UDP multicast "phiên sắp bắt đầu" từ server.
 *
 * <p>Server gửi {@link AuctionAnnouncement} tới nhóm {@code server.multicastGroup:server.multicastPort}
 * (mặc định {@code 239.255.42.99:5002}). Lớp này mở một {@link MulticastSocket} trên cổng đó, tham gia
 * nhóm trên <b>từng card mạng</b> đang bật, hỗ trợ multicast và không phải loopback; card lỗi bị bỏ qua để
 * một card hỏng không làm mất thông báo của card khác.
 *
 * <p>Mọi việc nhận chạy trên luồng nền daemon {@code multicast-listener}; callback được gọi trên luồng đó
 * nên giao diện phải tự chuyển về EDT. Gói rác, JSON sai cấu trúc hoặc callback ném lỗi đều bị bỏ qua —
 * thông báo này chỉ để hiển thị, mất gói không ảnh hưởng dữ liệu đấu giá (dữ liệu chuẩn luôn lấy qua TCP).
 */
public final class MulticastListener implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(MulticastListener.class.getName());

    /** Gói thông báo rất nhỏ; đệm rộng để không cắt JSON lạ. */
    private static final int BUFFER_BYTES = 4_096;

    private final InetAddress group;
    private final int port;
    private final Consumer<AuctionAnnouncement> onAnnouncement;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile MulticastSocket socket;
    private volatile Thread thread;

    /**
     * Tạo listener sẽ tự mở socket trên {@code port} và tham gia nhóm {@code group} khi {@link #start()}.
     *
     * @param group          địa chỉ nhóm multicast
     * @param port           cổng UDP lắng nghe
     * @param onAnnouncement nơi nhận thông báo; gọi trên luồng {@code multicast-listener}
     */
    public MulticastListener(InetAddress group, int port, Consumer<AuctionAnnouncement> onAnnouncement) {
        this(group, port, onAnnouncement, null);
    }

    /**
     * Constructor dành cho test: dùng socket đã bind sẵn (ví dụ loopback) thay vì tự mở và tham gia nhóm.
     * Listener nhận luôn quyền đóng socket này trong {@link #close()}.
     */
    MulticastListener(InetAddress group, int port, Consumer<AuctionAnnouncement> onAnnouncement,
            MulticastSocket boundSocket) {
        this.group = Objects.requireNonNull(group, "group");
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("Cổng multicast không hợp lệ: " + port);
        }
        this.port = port;
        this.onAnnouncement = Objects.requireNonNull(onAnnouncement, "onAnnouncement");
        this.socket = boundSocket;
    }

    /**
     * Mở socket (nếu chưa có) và bắt đầu nhận trên luồng nền. Gọi nhiều lần vẫn an toàn: chỉ lần đầu có
     * tác dụng. Nếu không mở được cổng, listener im lặng không chạy — thông báo multicast chỉ là phụ.
     */
    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            return;
        }
        try {
            if (socket == null) {
                MulticastSocket created = new MulticastSocket(port);
                joinGroups(created);
                socket = created;
                if (closed.get()) {
                    // close() chạy chen giữa lúc mở socket: đóng ngay, không mở luồng nhận.
                    created.close();
                    return;
                }
            }
        } catch (IOException | RuntimeException e) {
            started.set(false);
            LOG.log(System.Logger.Level.WARNING,
                    "Không mở được cổng multicast " + port + ": " + e.getMessage());
            return;
        }
        Thread receiver = new Thread(this::receiveLoop, "multicast-listener");
        receiver.setDaemon(true);
        thread = receiver;
        receiver.start();
    }

    private void joinGroups(MulticastSocket target) {
        InetSocketAddress address = new InetSocketAddress(group, port);
        int joined = 0;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                try {
                    if (!nic.isUp() || !nic.supportsMulticast() || nic.isLoopback()) {
                        continue;
                    }
                    target.joinGroup(address, nic);
                    joined++;
                } catch (IOException | RuntimeException e) {
                    LOG.log(System.Logger.Level.DEBUG,
                            "Bỏ qua card mạng " + nic.getName() + ": " + e.getMessage());
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(System.Logger.Level.DEBUG, "Không đọc được danh sách card mạng: " + e.getMessage());
        }
        if (joined == 0) {
            try {
                target.joinGroup(address, null);
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.DEBUG,
                        "Không tham gia được nhóm " + group.getHostAddress() + ": " + e.getMessage());
            }
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[BUFFER_BYTES];
        while (!closed.get()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                if (!closed.get()) {
                    LOG.log(System.Logger.Level.DEBUG, "Lỗi khi nhận multicast: " + e.getMessage());
                }
                continue;
            }
            AuctionAnnouncement announcement = parse(packet);
            if (announcement == null) {
                continue;
            }
            try {
                onAnnouncement.accept(announcement);
            } catch (RuntimeException e) {
                // Callback hỏng không được làm chết luồng nhận.
                LOG.log(System.Logger.Level.DEBUG,
                        "Bỏ qua lỗi khi xử lý thông báo phiên " + announcement.auctionId() + ": " + e);
            }
        }
    }

    private static AuctionAnnouncement parse(DatagramPacket packet) {
        String json = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                StandardCharsets.UTF_8).trim();
        if (json.isEmpty()) {
            return null;
        }
        try {
            AuctionAnnouncement announcement = JsonCodec.gson().fromJson(json, AuctionAnnouncement.class);
            if (announcement == null || announcement.auctionId() <= 0) {
                return null;
            }
            return new AuctionAnnouncement(announcement.auctionId(),
                    announcement.title() == null ? "" : announcement.title(), announcement.startsAt());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.DEBUG, "Bỏ qua gói multicast không đọc được: " + e.getMessage());
            return null;
        }
    }

    /** Dừng nhận và đóng socket. Gọi nhiều lần vẫn an toàn. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        MulticastSocket current = socket;
        if (current != null) {
            current.close();
        }
        Thread receiver = thread;
        if (receiver != null) {
            receiver.interrupt();
        }
    }
}
