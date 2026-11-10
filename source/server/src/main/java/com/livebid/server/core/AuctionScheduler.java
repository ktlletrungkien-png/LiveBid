package com.livebid.server.core;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Đồng hồ nhịp: cứ {@code tickMillis} lại đưa {@link AuctionServiceImpl#processDueAuctions()} vào executor
 * nghiệp vụ. Scheduler <b>không tự sửa dữ liệu</b>; mọi thay đổi trạng thái vẫn chạy trên executor.
 *
 * <p>Tính đúng không phụ thuộc scheduler: bid tại/sau {@code endsAt} bị từ chối ngay cả khi scheduler
 * chưa kịp chạy. Scheduler chỉ để phòng không có ai thao tác vẫn nhận AUCTION_STARTED/FINISHED đúng giờ
 * (trễ tối đa một nhịp).
 *
 * <p>Nếu lần trước chưa xử lý xong (executor đang bận) thì bỏ qua nhịp này, để không dồn tác vụ.
 */
public final class AuctionScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AuctionScheduler.class);

    public static final long DEFAULT_TICK_MILLIS = 500;

    private final AuctionServiceImpl service;
    private final long tickMillis;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private ScheduledExecutorService timer;

    public AuctionScheduler(AuctionServiceImpl service, long tickMillis) {
        if (tickMillis <= 0) {
            throw new IllegalArgumentException("tickMillis phải > 0");
        }
        this.service = Objects.requireNonNull(service);
        this.tickMillis = tickMillis;
    }

    public synchronized void start() {
        if (timer != null) {
            throw new IllegalStateException("AuctionScheduler đã chạy");
        }
        timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "auction-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(this::tick, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
        log.info("Scheduler chạy, nhịp {} ms", tickMillis);
    }

    private void tick() {
        if (!inFlight.compareAndSet(false, true)) {
            return;
        }
        service.processDueAuctions().whenComplete((ok, error) -> {
            inFlight.set(false);
            if (error != null) {
                log.error("Lỗi khi mở/chốt phiên theo lịch", error);
            }
        });
    }

    @Override
    public synchronized void close() {
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
    }
}
