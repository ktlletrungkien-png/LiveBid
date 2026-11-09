package com.livebid.common.dto;

import java.util.List;

/** Payload trả về của JOIN_AUCTION: trạng thái phiên + các bid gần nhất (mới nhất đứng đầu). */
public record RoomSnapshot(AuctionView auction, List<BidView> recentBids) {
}
