package com.livebid.common.payload;

/**
 * GET_BID_HISTORY, mới nhất đứng đầu. {@code beforeBidId} null = trang đầu;
 * trang sau truyền id nhỏ nhất của trang trước. {@code limit} tối đa 50.
 */
public record BidHistoryPayload(long auctionId, Long beforeBidId, int limit) {
}
