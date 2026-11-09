package com.livebid.server.model;

/** Một dòng bảng {@code bids}. {@code userId} luôn lấy từ phiên đăng nhập. */
public record Bid(long id, long auctionId, long userId, long amount, long acceptedAt, String requestId) {
}
