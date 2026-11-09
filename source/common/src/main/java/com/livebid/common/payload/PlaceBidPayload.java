package com.livebid.common.payload;

/** PLACE_BID. Không có userId: người đặt luôn lấy từ phiên đăng nhập trên kết nối. */
public record PlaceBidPayload(long auctionId, long amount) {
}
