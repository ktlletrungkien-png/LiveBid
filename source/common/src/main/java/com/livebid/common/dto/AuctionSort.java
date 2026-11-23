package com.livebid.common.dto;

/**
 * Cách sắp xếp danh sách phiên công khai (LIST_AUCTIONS và REST /api/auctions?sort=...).
 * {@code null} được hiểu là {@link #DEFAULT}.
 */
public enum AuctionSort {
    /**
     * Theo nhóm ưu tiên: ACTIVE (kết thúc sớm nhất trước), rồi SCHEDULED (bắt đầu sớm nhất trước),
     * rồi FINISHED (vừa kết thúc trước).
     */
    DEFAULT,
    /** {@code ends_at} tăng dần. */
    ENDS_SOONEST,
    /** {@code ends_at} giảm dần. */
    ENDS_LATEST,
    /** {@code starts_at} tăng dần. */
    STARTS_SOONEST,
    /** {@code starts_at} giảm dần. */
    STARTS_LATEST
}
