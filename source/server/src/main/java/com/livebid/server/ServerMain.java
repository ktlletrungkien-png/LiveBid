package com.livebid.server;

import com.livebid.common.Protocol;
import com.livebid.common.udp.DiscoveryReply;
import com.livebid.server.auth.AuthServiceImpl;
import com.livebid.server.auth.PasswordHasher;
import com.livebid.server.core.AuctionLifecycleListener;
import com.livebid.server.core.AuctionScheduler;
import com.livebid.server.core.AuctionServiceImpl;
import com.livebid.server.core.BusinessExecutor;
import com.livebid.server.core.CompositeLifecycleListener;
import com.livebid.server.db.SqliteAuctionRepository;
import com.livebid.server.db.SqliteBidRepository;
import com.livebid.server.db.SqliteDatabase;
import com.livebid.server.db.SqliteSettlementQueueRepository;
import com.livebid.server.db.SqliteTransactionManager;
import com.livebid.server.db.SqliteUserRepository;
import com.livebid.server.http.HttpGateway;
import com.livebid.server.settlement.RmiSettlementLocator;
import com.livebid.server.settlement.SettlementClient;
import com.livebid.server.tcp.AuctionCommands;
import com.livebid.server.tcp.AuthCommands;
import com.livebid.server.tcp.MessageRouter;
import com.livebid.server.tcp.TcpServer;
import com.livebid.server.udp.UdpAnnouncer;
import com.livebid.server.udp.UdpDiscoveryResponder;
import java.net.InetAddress;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
 *   <li>bật scheduler, rồi mở cổng TCP, HTTP/REST và UDP.</li>
 * </ol>
 *
 * <p>UDP chỉ là tiện ích tìm server: lỗi bind UDP chỉ ghi cảnh báo, không làm server TCP ngừng chạy.
 *
 * <p>Settlement (RMI) là tiến trình riêng: server vẫn chạy khi Settlement tắt, kết quả phiên nằm trong
 * {@code settlement_queue} và được gửi bù khi Settlement trở lại; {@code /api/stats} khi đó trả 503.
 *
 * <p>Còn thiếu: WebSocket {@code /ws} (mốc M4 của Hoè).
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
        SqliteAuctionRepository auctionRepo = new SqliteAuctionRepository(db);
        SqliteBidRepository bids = new SqliteBidRepository(db);
        SqliteUserRepository users = new SqliteUserRepository(db);
        SqliteSettlementQueueRepository settlementQueue = new SqliteSettlementQueueRepository(db);
        AuctionServiceImpl auctions = new AuctionServiceImpl(executor,
                auctionRepo, bids, users,
                settlementQueue, new SqliteTransactionManager(db),
                clock, config.auctionSettings());
        AuthServiceImpl auth = new AuthServiceImpl(executor, users,
                new PasswordHasher(config.pbkdf2Iterations()), clock);

        // --- Settlement (RMI): gửi kết quả phiên đã chốt, thử lại theo settlement_queue
        SettlementClient settlementClient = new SettlementClient(executor, settlementQueue, auctionRepo, bids, users,
                new RmiSettlementLocator(config.settlementRmiHost(), config.settlementRmiPort()),
                config.settlementRetryIntervalMillis());

        // --- UDP multicast: báo "phiên sắp bắt đầu"
        UdpAnnouncer announcer = openAnnouncer(config);

        // AuctionServiceImpl chỉ nhận một listener nên gộp UDP và Settlement lại.
        List<AuctionLifecycleListener> listeners = new ArrayList<>();
        if (announcer != null) {
            listeners.add(announcer);
        }
        listeners.add(settlementClient);
        auctions.setLifecycleListener(new CompositeLifecycleListener(listeners));
        settlementClient.start();

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

        // --- HTTP: REST /api + trang web tĩnh. Dùng chung auth và auctions với TCP.
        HttpGateway http = new HttpGateway(new HttpGateway.Settings(config.httpPort(), config.webRoot()),
                auth, auctions, settlementClient::fetchSummary);
        http.start();

        // --- UDP discovery
        UdpDiscoveryResponder discovery = openDiscovery(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            http.close();
            tcp.close();
            scheduler.close();
            settlementClient.close();
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
