package com.livebid.server;

import com.livebid.common.Protocol;

/**
 * Điểm khởi động Auction Server.
 *
 * TODO (theo lộ trình):
 *  - Tuần 1: đọc cấu hình, mở SQLite + migration, TcpServer + framing, REGISTER/LOGIN/PING
 *  - Tuần 2: AuctionService + single-thread executor, AuctionScheduler, RoomManager
 *  - Tuần 3: UdpDiscoveryResponder, UdpAnnouncer
 *  - Tuần 4: HttpGateway (REST + WebSocket, Javalin)
 *  - Tuần 5: SettlementClient (RMI) + hàng đợi retry
 */
public final class ServerMain {

    public static void main(String[] args) {
        System.out.println("LiveBid server - protocol v" + Protocol.PROTOCOL_VERSION
                + ", TCP " + Protocol.DEFAULT_TCP_PORT
                + ", HTTP " + Protocol.DEFAULT_HTTP_PORT
                + " (not implemented yet)");
    }
}
