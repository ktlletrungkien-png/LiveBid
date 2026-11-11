package com.livebid.server;

import com.livebid.common.Protocol;
import com.livebid.common.udp.DiscoveryReply;
import com.livebid.server.auth.AuthServiceImpl;
import com.livebid.server.auth.PasswordHasher;
import com.livebid.server.core.AuctionLifecycleListener;
import com.livebid.server.core.AuctionScheduler;
import com.livebid.server.core.AuctionServiceImpl;
import com.livebid.server.core.BusinessExecutor;
import com.livebid.server.db.SqliteAuctionRepository;
import com.livebid.server.db.SqliteBidRepository;
import com.livebid.server.db.SqliteDatabase;
import com.livebid.server.db.SqliteSettlementQueueRepository;
import com.livebid.server.db.SqliteTransactionManager;
import com.livebid.server.db.SqliteUserRepository;
import com.livebid.server.tcp.AuctionCommands;
import com.livebid.server.tcp.AuthCommands;
import com.livebid.server.tcp.MessageRouter;
import com.livebid.server.tcp.TcpServer;
import com.livebid.server.udp.UdpAnnouncer;
import com.livebid.server.udp.UdpDiscoveryResponder;
import java.net.InetAddress;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Điểm khởi động Auction Server: nối các mảng lại với nhau.
 *
 * <p>Thứ tự khởi động quan trọng:
 * <ol>
 *   <li>mở SQLite (tự tạo thư mục và chạy migration);</li>
 *   <li>dựng executor nghiệp vụ + service;</li>
 *   <li>chốt/mở các phiên đã đến giờ trong lúc server tắt ({@code processDueAuctions}) <b>trước khi mở cổng</b>,
 *       để client đầu tiên vào đã thấy trạng thái đúng;</li>
 *   <li>bật scheduler, rồi mở cổng TCP và UDP.</li>
 * </ol>
 *
 * <p>UDP chỉ là tiện ích tìm server: lỗi bind UDP chỉ ghi cảnh báo, không làm server TCP ngừng chạy.
 *
 * <p>Còn thiếu (mảng khác): HttpGateway (REST + WebSocket, Hoè), SettlementClient (RMI, Quân).
 */
public final class ServerMain {

    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.load(args);
        log.info("LiveBid server - protocol v{}, cấu hình: {}", Protocol.PROTOCOL_VERSION,
                config.source().map(Object::toString).orElse("(mặc định, không tìm thấy server.properties)"));
        Clock clock = Clock.systemUTC();

        // --- dữ liệu và nghiệp vụ
        SqliteDatabase db = SqliteDatabase.open(config.databasePath());
        BusinessExecutor executor = new BusinessExecutor();
        SqliteBidRepository bids = new SqliteBidRepository(db);
        SqliteUserRepository users = new SqliteUserRepository(db);
        AuctionServiceImpl auctions = new AuctionServiceImpl(executor,
                new SqliteAuctionRepository(db), bids, users,
                new SqliteSettlementQueueRepository(db), new SqliteTransactionManager(db),
                clock, config.auctionSettings());
        AuthServiceImpl auth = new AuthServiceImpl(executor, users,
                new PasswordHasher(config.pbkdf2Iterations()), clock);

        // --- UDP multicast: báo "phiên sắp bắt đầu"
        UdpAnnouncer announcer = openAnnouncer(config);
        if (announcer != null) {
            auctions.setLifecycleListener(announcer);
        } else {
            auctions.setLifecycleListener(AuctionLifecycleListener.NONE);
        }

        // Bắt kịp các phiên đã đến giờ khi server tắt; chờ xong rồi mới mở cổng.
        auctions.processDueAuctions().join();
        AuctionScheduler scheduler = new AuctionScheduler(auctions, AuctionScheduler.DEFAULT_TICK_MILLIS);
        scheduler.start();

        // --- TCP
        MessageRouter router = new MessageRouter(clock);
        new AuthCommands(auth, auctions).registerTo(router);
        new AuctionCommands(auctions).registerTo(router);
        TcpServer tcp = new TcpServer(new TcpServer.Settings(
                config.host(),
                config.tcpPort(),
                config.maxMessageBytes(),
                config.idleTimeoutSeconds() * 1000,
                config.maxConnections()), router);
        tcp.setDisconnectListener(auctions::onDisconnect);
        tcp.start();

        // --- UDP discovery
        UdpDiscoveryResponder discovery = openDiscovery(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            tcp.close();
            scheduler.close();
            closeQuietly(discovery);
            closeQuietly(announcer);
            executor.close();
            auth.close();
            db.close();
        }, "shutdown"));
        tcp.awaitTermination();
    }

    private static UdpAnnouncer openAnnouncer(ServerConfig config) {
        try {
            return new UdpAnnouncer(InetAddress.getByName(config.multicastGroup()),
                    config.multicastPort(), config.multicastTtl());
        } catch (Exception e) {
            log.warn("Không bật được UDP multicast ({}), bỏ qua thông báo 'sắp bắt đầu'", e.toString());
            return null;
        }
    }

    private static UdpDiscoveryResponder openDiscovery(ServerConfig config) {
        try {
            UdpDiscoveryResponder responder = new UdpDiscoveryResponder(config.host(), config.udpDiscoveryPort(),
                    new DiscoveryReply("LiveBid", config.tcpPort(), config.httpPort()));
            responder.start();
            return responder;
        } catch (Exception e) {
            log.warn("Không bật được UDP discovery cổng {} ({}), client phải nhập địa chỉ server thủ công",
                    config.udpDiscoveryPort(), e.toString());
            return null;
        }
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            log.warn("Lỗi khi đóng {}: {}", resource.getClass().getSimpleName(), e.toString());
        }
    }
}
