package com.livebid.server.repo;

import com.livebid.server.model.SettlementTask;
import java.util.List;

/** Bảng {@code settlement_queue}. <b>Hoè cài đặt bản SQLite</b>; Quân dùng trong SettlementClient. */
public interface SettlementQueueRepository {

    /** Thêm PENDING cho phiên; đã có thì bỏ qua (auction_id UNIQUE). */
    void enqueue(long auctionId);

    List<SettlementTask> findPending();

    void markDone(long taskId);

    /** Tăng {@code attempts}, ghi {@code last_attempt_at}. */
    void recordFailure(long taskId, long attemptAt);
}
