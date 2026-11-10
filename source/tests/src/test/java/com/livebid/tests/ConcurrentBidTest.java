package com.livebid.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.BidReceipt;
import com.livebid.common.dto.Role;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageType;
import com.livebid.server.core.AuctionServiceImpl;
import com.livebid.server.core.AuctionSettings;
import com.livebid.server.core.BusinessExecutor;
import com.livebid.server.core.ClientChannel;
import com.livebid.server.core.ServiceException;
import com.livebid.server.core.UserPrincipal;
import com.livebid.server.model.NewUser;
import com.livebid.server.model.User;
import com.livebid.server.repo.memory.InMemoryAuctionRepository;
import com.livebid.server.repo.memory.InMemoryBidRepository;
import com.livebid.server.repo.memory.InMemorySettlementQueueRepository;
import com.livebid.server.repo.memory.InMemoryTransactionManager;
import com.livebid.server.repo.memory.InMemoryUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * T05: nhiều người đặt <b>cùng một mức giá hợp lệ cùng lúc</b> (xuất phát đồng thời bằng {@link CountDownLatch}).
 * Chỉ đúng một lượt được chấp nhận; mọi lượt còn lại nhận {@code BID_TOO_LOW} với mức tối thiểu mới.
 *
 * <p>Lý do đúng: mọi bid đi qua một executor một luồng, nên tác vụ đến sau luôn thấy giá đã đổi.
 */
class ConcurrentBidTest {

    private static final long NOW = 1_793_080_800_000L;
    private static final long START_PRICE = 200_000;
    private static final long STEP = 20_000;
    private static final int BIDDERS = 8;
    private static final int ROUNDS = 25;

    private final BusinessExecutor executor = new BusinessExecutor();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryBidRepository bids = new InMemoryBidRepository();
    private final InMemoryAuctionRepository auctions = new InMemoryAuctionRepository(bids);
    private final AuctionServiceImpl service = new AuctionServiceImpl(executor, auctions, bids, users,
            new InMemorySettlementQueueRepository(), new InMemoryTransactionManager(),
            Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC), AuctionSettings.defaults());
    private final ExecutorService clients = Executors.newFixedThreadPool(BIDDERS);

    @AfterEach
    void tearDown() throws InterruptedException {
        clients.shutdownNow();
        clients.awaitTermination(5, TimeUnit.SECONDS);
        executor.close();
    }

    @Test
    void onlyOneOfSimultaneousEqualBidsIsAccepted() throws Exception {
        UserPrincipal admin = user("admin", Role.ADMIN);
        long auctionId = service.createAuction(admin, new AuctionDraft("Bàn phím cơ", "",
                START_PRICE, STEP, NOW - 60_000, NOW + 3_600_000)).join().auctionId();

        List<UserPrincipal> bidders = new ArrayList<>();
        List<RecordingChannel> channels = new ArrayList<>();
        for (int i = 0; i < BIDDERS; i++) {
            bidders.add(user("user" + i, Role.USER));
            channels.add(new RecordingChannel("c" + i));
            service.joinAuction(bidders.get(i), channels.get(i), auctionId).join();
        }

        long expectedPrice = 0;
        for (int round = 0; round < ROUNDS; round++) {
            // Vòng đầu: mọi người cùng đặt giá khởi điểm; các vòng sau: cùng đặt đúng giá hiện tại + bước giá.
            long amount = round == 0 ? START_PRICE : expectedPrice + STEP;
            List<CompletableFuture<BidReceipt>> results = placeSimultaneously(auctionId, amount, bidders, channels);

            List<BidReceipt> accepted = new ArrayList<>();
            for (CompletableFuture<BidReceipt> result : results) {
                try {
                    accepted.add(result.join());
                } catch (CompletionException e) {
                    ServiceException rejected = assertInstanceOf(ServiceException.class, e.getCause());
                    assertEquals(ErrorCode.BID_TOO_LOW, rejected.code(), "vòng " + round);
                    assertEquals(amount + STEP, rejected.details().get("minimumAllowed"),
                            "lượt bị từ chối được báo mức tối thiểu mới");
                }
            }
            assertEquals(1, accepted.size(), "vòng " + round + ": đúng một lượt được chấp nhận");
            assertEquals(amount, accepted.get(0).acceptedAmount());
            expectedPrice = amount;
        }

        AuctionView finalState = service.getAuction(auctionId).join();
        assertEquals(expectedPrice, finalState.currentPrice());
        assertEquals(ROUNDS, bids.countByAuction(auctionId), "không có bid thừa nào được ghi");

        // Mọi người trong phòng thấy cùng một chuỗi cập nhật: mỗi vòng đúng một sự kiện, version tăng dần.
        for (RecordingChannel channel : channels) {
            List<AuctionView> updates = channel.events(MessageType.AUCTION_UPDATED);
            assertEquals(ROUNDS, updates.size(), channel.id());
            for (int i = 1; i < updates.size(); i++) {
                assertTrue(updates.get(i).version() > updates.get(i - 1).version());
            }
            assertEquals(expectedPrice, updates.get(updates.size() - 1).currentPrice());
        }
    }

    /** Mọi người chờ ở vạch xuất phát, rồi cùng gửi bid khi latch mở. */
    private List<CompletableFuture<BidReceipt>> placeSimultaneously(long auctionId, long amount,
            List<UserPrincipal> bidders, List<RecordingChannel> channels) throws Exception {
        CountDownLatch ready = new CountDownLatch(bidders.size());
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<CompletableFuture<BidReceipt>>> submitted = new ArrayList<>();
        for (int i = 0; i < bidders.size(); i++) {
            UserPrincipal bidder = bidders.get(i);
            RecordingChannel channel = channels.get(i);
            submitted.add(CompletableFuture.supplyAsync(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                return service.placeBid(bidder, channel, auctionId, amount, java.util.UUID.randomUUID().toString());
            }, clients));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        List<CompletableFuture<BidReceipt>> results = new ArrayList<>();
        for (CompletableFuture<CompletableFuture<BidReceipt>> s : submitted) {
            results.add(s.get(5, TimeUnit.SECONDS));
        }
        CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
                .exceptionally(e -> null).get(5, TimeUnit.SECONDS);
        return results;
    }

    private UserPrincipal user(String username, Role role) {
        User u = users.insert(new NewUser(username, "Người " + username, "hash", "salt", 1, role, 0));
        return new UserPrincipal(u.id(), u.username(), u.displayName(), u.role());
    }

    /** Kênh giả: ghi lại mọi thông điệp server đẩy xuống. */
    static final class RecordingChannel implements ClientChannel {

        private final String id;
        private final List<Message> received = new CopyOnWriteArrayList<>();

        RecordingChannel(String id) {
            this.id = id;
        }

        List<AuctionView> events(MessageType type) {
            return received.stream().filter(m -> type.name().equals(m.type()))
                    .map(m -> JsonCodec.fromTree(m.payload(), AuctionView.class)).toList();
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Transport transport() {
            return Transport.TCP;
        }

        @Override
        public String remoteAddress() {
            return "test";
        }

        @Override
        public void send(Message message) {
            received.add(message);
        }

        @Override
        public void close(String reason) {
        }

        @Override
        public boolean isOpen() {
            return true;
        }
    }
}
