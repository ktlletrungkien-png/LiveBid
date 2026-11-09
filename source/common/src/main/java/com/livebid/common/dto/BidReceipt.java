package com.livebid.common.dto;

/**
 * Biên nhận PLACE_BID thành công. Biên nhận không được ghi đè trạng thái phòng mới hơn:
 * chỉ cập nhật giao diện theo sự kiện AUCTION_UPDATED (so sánh {@code version}).
 */
public record BidReceipt(long bidId, long auctionId, long acceptedAmount, long acceptedAt, long version) {
}
