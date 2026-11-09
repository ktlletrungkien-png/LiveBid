package com.livebid.common.payload;

import com.livebid.common.dto.AuctionDraft;

/** UPDATE_AUCTION: chỉ sửa được khi phiên còn SCHEDULED; sai {@code expectedVersion} → VERSION_CONFLICT. */
public record UpdateAuctionPayload(long auctionId, long expectedVersion, AuctionDraft draft) {
}
