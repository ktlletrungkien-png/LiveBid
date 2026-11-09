package com.livebid.server.model;

/** Một dòng bảng {@code settlement_queue}. */
public record SettlementTask(long id, long auctionId, Status status, int attempts, Long lastAttemptAt) {

    public enum Status {
        PENDING,
        DONE
    }
}
