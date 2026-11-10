package com.livebid.server;

import com.livebid.common.Protocol;
import com.livebid.server.tcp.MessageRouter;
import com.livebid.server.tcp.TcpServer;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Điểm khởi động Auction Server.
 *
 * <p>Hiện có: đọc cấu hình, TCP server + framing, lệnh PING.
 *
 * TODO (theo lộ trình):
 *  - Tuần 1: mở SQLite + migration, REGISTER/LOGIN (AuthService)
 *  - Tuần 2: AuctionService + single-thread executor, AuctionScheduler, RoomManager
 *  - Tuần 3: UdpDiscoveryResponder, UdpAnnouncer
 *  - Tuần 4: HttpGateway (REST + WebSocket, Javalin)
 *  - Tuần 5: SettlementClient (RMI) + hàng đợi retry
 */
public final class ServerMain {

    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

    private ServerMain() {
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.load(args);
        log.info("LiveBid server - protocol v{}, cấu hình: {}", Protocol.PROTOCOL_VERSION,
                config.source().map(Object::toString).orElse("(mặc định, không tìm thấy server.properties)"));

        MessageRouter router = new MessageRouter(Clock.systemUTC());

        TcpServer tcp = new TcpServer(new TcpServer.Settings(
                config.host(),
                config.tcpPort(),
                config.maxMessageBytes(),
                config.idleTimeoutSeconds() * 1000,
                config.maxConnections()), router);
        tcp.start();

        Runtime.getRuntime().addShutdownHook(new Thread(tcp::close, "shutdown"));
        tcp.awaitTermination();
    }
}
