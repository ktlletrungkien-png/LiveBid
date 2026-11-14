/**
 * SettlementClient: gọi RMI {@code SettlementService}, retry theo {@code settlement_queue}.
 * <b>Phụ trách: Hà Mạnh Quân.</b>
 *
 * <h2>Cách gắn vào {@code ServerMain}</h2>
 *
 * <pre>{@code
 * SettlementClient settlementClient = new SettlementClient(executor, settlementQueue, auctions, bids, users,
 *         new RmiSettlementLocator(config.getString("settlement.rmiHost", "127.0.0.1"),
 *                 config.getInt("settlement.rmiPort", Protocol.DEFAULT_RMI_PORT)),
 *         config.getInt("settlement.retryIntervalSeconds", 10) * 1000L);
 * settlementClient.start();
 *
 * // AuctionServiceImpl chỉ nhận một listener, nên gộp udpAnnouncer + settlementClient:
 * auctions.setLifecycleListener(new AuctionLifecycleListener() {
 *     @Override
 *     public void auctionStartingSoon(AuctionView auction) {
 *         udpAnnouncer.auctionStartingSoon(auction);
 *     }
 *
 *     @Override
 *     public void auctionFinished(AuctionView auction) {
 *         udpAnnouncer.auctionFinished(auction);
 *         settlementClient.auctionFinished(auction);
 *     }
 * });
 *
 * // HttpGateway của Hoè dùng settlementClient::fetchSummary cho REST /api/stats.
 * }</pre>
 *
 * <p>Đóng theo thứ tự ngược lại lúc khởi động: {@code settlementClient.close()} trước {@code executor.close()},
 * để lần quét đang dở còn ghi được {@code markDone}/{@code recordFailure}.
 */
package com.livebid.server.settlement;
