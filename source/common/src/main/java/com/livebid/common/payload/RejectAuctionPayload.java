package com.livebid.common.payload;

/** Payload của {@code REJECT_AUCTION}: từ chối một phiên đang chờ duyệt kèm lý do. */
public record RejectAuctionPayload(long auctionId, String reason) {
}
