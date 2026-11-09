package com.livebid.common.dto;

/** Một lượt đặt giá đã được chấp nhận (lịch sử bid). {@code acceptedAt} là giờ server, epoch ms UTC. */
public record BidView(long bidId, long auctionId, String bidderDisplayName, long amount, long acceptedAt) {
}
