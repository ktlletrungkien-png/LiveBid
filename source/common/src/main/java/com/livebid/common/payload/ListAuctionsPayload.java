package com.livebid.common.payload;

import com.livebid.common.dto.AuctionStatus;

/** LIST_AUCTIONS. {@code status} null = tất cả. {@code page} từ 1, {@code pageSize} tối đa 50. */
public record ListAuctionsPayload(AuctionStatus status, int page, int pageSize) {
}
