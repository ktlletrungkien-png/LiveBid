package com.livebid.server.repo.memory;

import com.livebid.server.model.Bid;
import com.livebid.server.repo.BidRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Bản lưu trong bộ nhớ, dùng cho test và khi chưa có SQLite. */
public final class InMemoryBidRepository implements BidRepository {

    private final List<Bid> bids = new CopyOnWriteArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Bid insert(long auctionId, long userId, long amount, long acceptedAt, String requestId) {
        Bid bid = new Bid(nextId.getAndIncrement(), auctionId, userId, amount, acceptedAt, requestId);
        bids.add(bid);
        return bid;
    }

    @Override
    public List<Bid> findHistory(long auctionId, Long beforeBidId, int limit) {
        List<Bid> result = new ArrayList<>();
        bids.stream()
                .filter(b -> b.auctionId() == auctionId)
                .filter(b -> beforeBidId == null || b.id() < beforeBidId)
                .sorted(Comparator.comparingLong(Bid::id).reversed())
                .limit(limit)
                .forEach(result::add);
        return result;
    }

    @Override
    public int countByAuction(long auctionId) {
        return (int) bids.stream().filter(b -> b.auctionId() == auctionId).count();
    }

    /** Dùng nội bộ cho {@link InMemoryAuctionRepository#findParticipatedBy}. */
    boolean hasBid(long auctionId, long userId) {
        return bids.stream().anyMatch(b -> b.auctionId() == auctionId && b.userId() == userId);
    }
}
