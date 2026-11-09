package com.livebid.server.repo.memory;

import com.livebid.server.model.SettlementTask;
import com.livebid.server.repo.SettlementQueueRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bản lưu trong bộ nhớ, dùng cho test và khi chưa có SQLite. */
public final class InMemorySettlementQueueRepository implements SettlementQueueRepository {

    private final Map<Long, SettlementTask> byId = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public synchronized void enqueue(long auctionId) {
        boolean exists = byId.values().stream().anyMatch(t -> t.auctionId() == auctionId);
        if (!exists) {
            long id = nextId.getAndIncrement();
            byId.put(id, new SettlementTask(id, auctionId, SettlementTask.Status.PENDING, 0, null));
        }
    }

    @Override
    public List<SettlementTask> findPending() {
        return byId.values().stream()
                .filter(t -> t.status() == SettlementTask.Status.PENDING)
                .sorted(Comparator.comparingLong(SettlementTask::id))
                .toList();
    }

    @Override
    public void markDone(long taskId) {
        byId.computeIfPresent(taskId, (id, t) ->
                new SettlementTask(id, t.auctionId(), SettlementTask.Status.DONE, t.attempts(), t.lastAttemptAt()));
    }

    @Override
    public void recordFailure(long taskId, long attemptAt) {
        byId.computeIfPresent(taskId, (id, t) ->
                new SettlementTask(id, t.auctionId(), t.status(), t.attempts() + 1, attemptAt));
    }
}
