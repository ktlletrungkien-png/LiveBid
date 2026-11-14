package com.livebid.tests.settlementclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.Role;
import com.livebid.common.rmi.AuctionResultDto;
import com.livebid.common.rmi.SettlementService;
import com.livebid.common.rmi.SettlementSummaryDto;
import com.livebid.server.core.BusinessExecutor;
import com.livebid.server.model.Auction;
import com.livebid.server.model.NewAuction;
import com.livebid.server.model.NewUser;
import com.livebid.server.repo.AuctionRepository;
import com.livebid.server.repo.BidRepository;
import com.livebid.server.repo.SettlementQueueRepository;
import com.livebid.server.repo.UserRepository;
import com.livebid.server.repo.memory.InMemoryAuctionRepository;
import com.livebid.server.repo.memory.InMemoryBidRepository;
import com.livebid.server.repo.memory.InMemorySettlementQueueRepository;
import com.livebid.server.repo.memory.InMemoryUserRepository;
import com.livebid.server.settlement.SettlementClient;
import com.livebid.server.settlement.SettlementServiceLocator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * M3: {@link SettlementClient} lấy việc từ {@code settlement_queue}, gọi RMI và thử lại khi Settlement tắt.
 *
 * <p>Không cần RMI Registry: locator trả về {@link FakeSettlementService}. Dữ liệu chuẩn bị bằng repository
 * trong bộ nhớ, mọi lần đọc/ghi đều qua {@link BusinessExecutor} giống môi trường thật.
 */
class SettlementClientTest {

    private static final long NOW = 1_793_080_800_000L;
    private static final long START_PRICE = 100_000;
    private static final long STEP = 10_000;
    private static final long RETRY_MILLIS = 100;
    private static final long AWAIT_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(3);

    private final BusinessExecutor executor = new BusinessExecutor();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryBidRepository bids = new InMemoryBidRepository();
    private final InMemoryAuctionRepository auctions = new InMemoryAuctionRepository(bids);
    private final InMemorySettlementQueueRepository queue = new InMemorySettlementQueueRepository();
    private final FakeSettlementService settlement = new FakeSettlementService();
    private final List<String> offExecutorCalls = new CopyOnWriteArrayList<>();

    private SettlementClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        executor.close();
    }

    @Test
    void sendsResultAndMarksTaskDone() throws Exception {
        long winnerId = createUser("alice", "Alice");
        long auctionId = createFinishedAuction(winnerId, 2);

        client = newClient(() -> settlement);
        client.start();
        client.auctionFinished(view(auctionId));

        await("task phải chuyển DONE", () -> queue.findPending().isEmpty());

        assertEquals(1, settlement.results().size(), "gửi đúng một biên bản");
        AuctionResultDto dto = settlement.results().get(0);
        assertEquals(auctionId, dto.auctionId());
        assertEquals("Bàn phím cơ", dto.title());
        assertEquals(Long.valueOf(winnerId), dto.winnerUserId());
        assertEquals("Alice", dto.winnerDisplayName());
        assertEquals(START_PRICE + 2 * STEP, dto.finalPrice());
        assertEquals(2, dto.bidCount());
        assertEquals(NOW - 60_000, dto.startsAt());
        assertEquals(NOW + 60_000, dto.finishedAt());
    }

    @Test
    void failedDeliveryStaysPendingThenSucceedsOnRetry() throws Exception {
        long winnerId = createUser("bob", "Bob");
        createFinishedAuction(winnerId, 1);

        settlement.failing(true);
        client = newClient(() -> settlement);
        client.start();

        await("task vẫn PENDING và attempts tăng", () -> pendingAttempts() >= 1);
        assertFalse(queue.findPending().isEmpty(), "Settlement tắt thì việc phải còn trong hàng đợi");
        assertTrue(settlement.results().isEmpty(), "không có biên bản nào được nhận khi lỗi");

        settlement.failing(false);
        await("lần quét sau phải gửi thành công", () -> queue.findPending().isEmpty());
        assertEquals(1, settlement.results().size());
    }

    @Test
    void auctionWithoutWinnerHasNullWinnerAndZeroPrice() throws Exception {
        long auctionId = createFinishedAuction(null, 0);

        client = newClient(() -> settlement);
        client.start();
        client.auctionFinished(view(auctionId));

        await("task phải chuyển DONE", () -> queue.findPending().isEmpty());

        AuctionResultDto dto = settlement.results().get(0);
        assertFalse(dto.hasWinner());
        assertNull(dto.winnerUserId());
        assertNull(dto.winnerDisplayName());
        assertEquals(0, dto.finalPrice());
        assertEquals(0, dto.bidCount());
    }

    @Test
    void fetchSummaryReturnsEmptyWhenLocatorFails() throws Exception {
        client = newClient(() -> {
            throw new NotBoundException("Settlement chưa chạy");
        });

        assertTrue(client.fetchSummary().isEmpty(), "Settlement tắt thì trả rỗng, không ném lỗi");

        client.close();
        client = newClient(() -> settlement);
        assertTrue(client.fetchSummary().isPresent(), "Settlement chạy thì trả thống kê");
    }

    @Test
    void repositoriesAreOnlyTouchedOnExecutorThread() throws Exception {
        long winnerId = createUser("carol", "Carol");
        long auctionId = createFinishedAuction(winnerId, 1);

        client = newClient(() -> settlement,
                onlyOnExecutorThread(queue, SettlementQueueRepository.class),
                onlyOnExecutorThread(auctions, AuctionRepository.class),
                onlyOnExecutorThread(bids, BidRepository.class),
                onlyOnExecutorThread(users, UserRepository.class));
        client.start();
        client.auctionFinished(view(auctionId));

        await("task phải chuyển DONE", () -> queue.findPending().isEmpty());
        assertEquals(List.of(), offExecutorCalls, "repository chỉ được chạm trên luồng executor");
    }

    // ------------------------------------------------------------------ dựng dữ liệu

    private long createUser(String username, String displayName) throws Exception {
        long[] id = new long[1];
        executor.run(() -> id[0] = users.insert(new NewUser(username, displayName, "hash", "salt", 1,
                Role.USER, NOW)).id()).get();
        return id[0];
    }

    /** Tạo phiên đã chốt; {@code bidCount} bid tăng dần, {@code winnerId} null = không ai thắng. */
    private long createFinishedAuction(Long winnerId, int bidCount) throws Exception {
        long[] id = new long[1];
        executor.run(() -> {
            long sellerId = users.insert(new NewUser("seller" + System.nanoTime(), "Người bán", "hash", "salt",
                    1, Role.ADMIN, NOW)).id();
            Auction auction = auctions.insert(new NewAuction("Bàn phím cơ", "Mô tả", START_PRICE, STEP,
                    NOW - 60_000, NOW + 60_000, sellerId, NOW - 120_000));

            long price = START_PRICE;
            if (winnerId != null) {
                for (int i = 0; i < bidCount; i++) {
                    price += STEP;
                    bids.insert(auction.id(), winnerId, price, NOW - 30_000 + i, "req-" + i);
                }
            }
            Auction finished = winnerId == null
                    ? auction.withStatus(AuctionStatus.FINISHED, null)
                    : auction.withBid(price, winnerId).withStatus(AuctionStatus.FINISHED, winnerId);
            auctions.update(finished);
            queue.enqueue(auction.id());
            id[0] = auction.id();
        }).get();
        return id[0];
    }

    private int pendingAttempts() {
        return queue.findPending().stream().mapToInt(task -> task.attempts()).max().orElse(0);
    }

    /** {@link SettlementClient} chỉ dùng {@code auctionId}; các trường khác giữ cho giống dữ liệu thật. */
    private static AuctionView view(long auctionId) {
        return new AuctionView(auctionId, "Bàn phím cơ", "", AuctionStatus.FINISHED, START_PRICE, STEP,
                START_PRICE, false, null, null, NOW - 60_000, NOW + 60_000, 0, 1, NOW);
    }

    private SettlementClient newClient(SettlementServiceLocator locator) {
        return newClient(locator, queue, auctions, bids, users);
    }

    private SettlementClient newClient(SettlementServiceLocator locator, SettlementQueueRepository queueRepo,
                                       AuctionRepository auctionRepo, BidRepository bidRepo,
                                       UserRepository userRepo) {
        return new SettlementClient(executor, queueRepo, auctionRepo, bidRepo, userRepo, locator, RETRY_MILLIS);
    }

    /** Bọc repository để ghi lại mọi lần bị gọi ngoài luồng executor. */
    @SuppressWarnings("unchecked")
    private <T> T onlyOnExecutorThread(T delegate, Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            try {
                executor.checkExecutorThread();
            } catch (IllegalStateException e) {
                offExecutorCalls.add(method.getName());
                throw e;
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    private static void await(String message, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + AWAIT_TIMEOUT_NANOS;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    /** Settlement giả: ghi lại biên bản, có cờ "đang lỗi" để ném {@link RemoteException}. */
    private static final class FakeSettlementService implements SettlementService {

        private final List<AuctionResultDto> results = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failing = new AtomicBoolean(false);
        private final SettlementSummaryDto summary =
                new SettlementSummaryDto(1, 1, START_PRICE + STEP, List.of());

        void failing(boolean value) {
            failing.set(value);
        }

        List<AuctionResultDto> results() {
            return results;
        }

        @Override
        public void recordResult(AuctionResultDto result) throws RemoteException {
            if (failing.get()) {
                throw new RemoteException("Settlement đang tắt (giả lập)");
            }
            results.add(result);
        }

        @Override
        public SettlementSummaryDto getSummary() throws RemoteException {
            if (failing.get()) {
                throw new RemoteException("Settlement đang tắt (giả lập)");
            }
            return summary;
        }
    }
}
