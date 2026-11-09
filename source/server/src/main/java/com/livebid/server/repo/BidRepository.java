package com.livebid.server.repo;

import com.livebid.server.model.Bid;
import java.util.List;

/** Bảng {@code bids}. <b>Hoè cài đặt bản SQLite</b> trong {@code server.db}. */
public interface BidRepository {

    Bid insert(long auctionId, long userId, long amount, long acceptedAt, String requestId);

    /** Mới nhất đứng đầu (id giảm dần). {@code beforeBidId} null = từ bid mới nhất. */
    List<Bid> findHistory(long auctionId, Long beforeBidId, int limit);

    int countByAuction(long auctionId);
}
