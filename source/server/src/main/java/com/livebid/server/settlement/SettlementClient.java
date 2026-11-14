package com.livebid.server.settlement;

import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.rmi.AuctionResultDto;
import com.livebid.common.rmi.SettlementService;
import com.livebid.common.rmi.SettlementSummaryDto;
import com.livebid.server.core.AuctionLifecycleListener;
import com.livebid.server.core.BusinessExecutor;
import com.livebid.server.model.Auction;
import com.livebid.server.model.SettlementTask;
import com.livebid.server.model.User;
import com.livebid.server.repo.AuctionRepository;
import com.livebid.server.repo.BidRepository;
import com.livebid.server.repo.SettlementQueueRepository;
import com.livebid.server.repo.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gửi biên bản các phiên đã chốt sang dịch vụ Settlement qua RMI, thử lại theo {@code settlement_queue}.
 *
 * <p>Nguồn sự thật là bảng {@code settlement_queue}: {@code AuctionServiceImpl} ghi một dòng {@code PENDING}
 * <b>trong cùng transaction</b> chốt phiên, nên mất kết nối RMI hay Settlement tắt đều không làm mất kết quả —
 * chỉ làm việc gửi bị hoãn.
 *
 * <h2>Quy tắc luồng</h2>
 *
 * <ul>
 *   <li>Chỉ <b>một</b> luồng {@code settlement-client} (daemon) chạy mọi lần quét, nên không bao giờ có hai lần
 *       quét chồng nhau. Lần quét định kỳ dùng {@code scheduleWithFixedDelay}.</li>
 *   <li>Mọi lần đọc/ghi repository đi qua {@link BusinessExecutor#submit}, vì SQLite dùng chung một connection.
 *       Trên luồng {@code settlement-client} được phép {@code join()} future đó.</li>
 *   <li>Gọi RMI <b>chỉ</b> trên luồng {@code settlement-client}, không bao giờ trong {@code executor.submit}:
 *       RMI chậm hoặc treo sẽ chặn toàn bộ đấu giá.</li>
 *   <li>{@link #auctionFinished(AuctionView)} chạy trên luồng executor nên chỉ xếp một lần quét rồi trả về ngay,
 *       không bao giờ ném lỗi.</li>
 * </ul>
 *
 * <p>Chỉ <b>đọc</b> {@code auctions}/{@code bids}/{@code users} để dựng DTO; chỉ <b>đọc/ghi</b>
 * {@code settlement_queue}. Không ghi bảng nào khác.
 *
 * <p><b>Hạn chế đã biết:</b> lời gọi RMI không có timeout riêng. Settlement tắt hẳn thì {@code lookup()} ném lỗi
 * ngay; nhưng nếu Registry còn sống mà đối tượng Settlement treo, luồng {@code settlement-client} (và
 * {@link #fetchSummary()} trên luồng HTTP) có thể chờ lâu. Việc này không ảnh hưởng đường găng của bid.
 */
public final class SettlementClient implements AuctionLifecycleListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SettlementClient.class);

    /** Thời gian chờ tối đa khi đóng, để lần quét đang dở kịp ghi {@code markDone}/{@code recordFailure}. */
    private static final long SHUTDOWN_WAIT_SECONDS = 2;

    private final BusinessExecutor executor;
    private final SettlementQueueRepository queue;
    private final AuctionRepository auctions;
    private final BidRepository bids;
    private final UserRepository users;
    private final SettlementServiceLocator locator;
    private final long retryIntervalMillis;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Stub đang dùng; null = lần sau phải hỏi {@link #locator}. */
    private volatile SettlementService stub;

    /**
     * @param retryIntervalMillis chu kỳ quét hàng đợi; cấu hình {@code settlement.retryIntervalSeconds} × 1000
     */
    public SettlementClient(BusinessExecutor executor,
                            SettlementQueueRepository queue,
                            AuctionRepository auctions,
                            BidRepository bids,
                            UserRepository users,
                            SettlementServiceLocator locator,
                            long retryIntervalMillis) {
        if (retryIntervalMillis <= 0) {
            throw new IllegalArgumentException("Chu kỳ thử lại phải > 0 ms, đang là: " + retryIntervalMillis);
        }
        this.executor = Objects.requireNonNull(executor, "executor");
        this.queue = Objects.requireNonNull(queue, "queue");
        this.auctions = Objects.requireNonNull(auctions, "auctions");
        this.bids = Objects.requireNonNull(bids, "bids");
        this.users = Objects.requireNonNull(users, "users");
        this.locator = Objects.requireNonNull(locator, "locator");
        this.retryIntervalMillis = retryIntervalMillis;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "settlement-client");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Bắt đầu quét định kỳ. Gọi một lần lúc khởi động, trước khi mở cổng. */
    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleWithFixedDelay(this::drainQuietly, 0, retryIntervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Phiên vừa chốt: xếp ngay một lần quét. Chạy trên luồng executor nên phải trả về tức thì và không ném lỗi.
     */
    @Override
    public void auctionFinished(AuctionView auction) {
        if (closed.get()) {
            return;
        }
        try {
            scheduler.execute(this::drainQuietly);
        } catch (RejectedExecutionException e) {
            log.debug("Bỏ qua yêu cầu quét: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Không đưa được yêu cầu quét vào luồng settlement-client: {}", e.toString());
        }
    }

    /**
     * Thống kê cộng dồn cho REST {@code /api/stats}. Chạy trên luồng người gọi (HTTP), không qua executor.
     * Settlement không chạy thì trả {@link Optional#empty()} thay vì ném lỗi.
     */
    public Optional<SettlementSummaryDto> fetchSummary() {
        if (closed.get()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(lookupService().getSummary());
        } catch (Exception e) {
            stub = null;
            log.warn("Không lấy được thống kê từ Settlement: {}", e.toString());
            return Optional.empty();
        }
    }

    /** Dừng luồng quét; lần quét đang dở được chờ tối đa {@value #SHUTDOWN_WAIT_SECONDS} giây. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            stub = null;
        }
    }

    // ================================================================== một lần quét

    private void drainQuietly() {
        if (closed.get()) {
            return;
        }
        try {
            drain();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("Lần quét settlement_queue lỗi: {}", e.toString());
        }
    }

    /**
     * Một lần quét: dựng toàn bộ DTO trong một tác vụ executor, rồi gửi RMI lần lượt trên luồng hiện tại.
     * Gặp lỗi RMI thì ghi nhận thất bại và <b>dừng</b> lần quét — Settlement đang tắt thì các việc sau cũng lỗi,
     * thử tiếp chỉ tốn timeout.
     */
    private void drain() throws InterruptedException {
        List<Work> work = pendingWork();
        for (Work item : work) {
            if (closed.get()) {
                return;
            }
            try {
                lookupService().recordResult(item.result());
            } catch (Exception e) {
                stub = null;
                long now = System.currentTimeMillis();
                runOnExecutor(() -> queue.recordFailure(item.taskId(), now));
                log.warn("Gửi biên bản phiên {} thất bại ({}); sẽ thử lại sau {} ms",
                        item.result().auctionId(), e.toString(), retryIntervalMillis);
                return;
            }
            runOnExecutor(() -> queue.markDone(item.taskId()));
        }
    }

    /** Đọc hàng đợi và dựng DTO — toàn bộ trong một tác vụ executor. */
    private List<Work> pendingWork() throws InterruptedException {
        CompletableFuture<List<Work>> future = executor.submit(() -> {
            List<SettlementTask> tasks = queue.findPending();
            List<Work> work = new ArrayList<>(tasks.size());
            for (SettlementTask task : tasks) {
                AuctionResultDto result = buildResult(task.auctionId());
                if (result != null) {
                    work.add(new Work(task.id(), result));
                }
            }
            return work;
        });
        return await(future);
    }

    /** Chạy trên luồng executor: dựng DTO từ dữ liệu đã chốt. Trả null nếu phiên không còn hoặc chưa FINISHED. */
    private AuctionResultDto buildResult(long auctionId) {
        Optional<Auction> found = auctions.findById(auctionId);
        if (found.isEmpty()) {
            log.warn("Bỏ qua lập biên bản phiên {}: không còn trong DB", auctionId);
            return null;
        }
        Auction auction = found.get();
        if (auction.status() != AuctionStatus.FINISHED) {
            log.warn("Bỏ qua lập biên bản phiên {}: trạng thái {}", auctionId, auction.status());
            return null;
        }

        Long winnerId = auction.winnerId();
        String winnerDisplayName = null;
        if (winnerId != null) {
            winnerDisplayName = users.findById(winnerId).map(User::displayName).orElse(null);
        }
        long finalPrice = winnerId != null ? auction.currentPrice() : 0L;
        int bidCount = bids.countByAuction(auctionId);

        return new AuctionResultDto(auctionId, auction.title(), winnerId, winnerDisplayName, finalPrice, bidCount,
                auction.startsAt(), auction.endsAt());
    }

    /** Lấy stub đã nhớ, hoặc hỏi locator một lần rồi nhớ lại. */
    private SettlementService lookupService() throws Exception {
        SettlementService current = stub;
        if (current == null) {
            current = locator.lookup();
            if (current == null) {
                throw new IllegalStateException("Locator trả về null");
            }
            stub = current;
        }
        return current;
    }

    /** Ghi kết quả vào {@code settlement_queue} qua executor rồi chờ xong (được phép trên luồng hiện tại). */
    private void runOnExecutor(Runnable task) throws InterruptedException {
        await(executor.run(task));
    }

    private static <T> T await(CompletableFuture<T> future) throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Tác vụ nghiệp vụ lỗi: " + cause, cause);
        }
    }

    /** Một việc cần gửi: id dòng {@code settlement_queue} và DTO đã dựng sẵn. */
    private record Work(long taskId, AuctionResultDto result) {
    }
}
