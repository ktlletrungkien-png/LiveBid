package com.livebid.server.repo.memory;

import com.livebid.common.dto.AuctionStatus;
import com.livebid.server.model.Auction;
import com.livebid.server.model.NewAuction;
import com.livebid.server.repo.AuctionRepository;
import com.livebid.server.repo.DataAccessException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/** Bản lưu trong bộ nhớ, dùng cho test và khi chưa có SQLite. */
public final class InMemoryAuctionRepository implements AuctionRepository {

    private static final Comparator<Auction> ORDER =
            Comparator.comparingLong(Auction::startsAt).thenComparingLong(Auction::id);
    private static final Comparator<Auction> PENDING_ORDER =
            Comparator.comparingLong(Auction::createdAt).thenComparingLong(Auction::id);
    private static final Comparator<Auction> CREATED_ORDER =
            Comparator.comparingLong(Auction::createdAt).thenComparingLong(Auction::id).reversed();

    private final Map<Long, Auction> byId = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final InMemoryBidRepository bids;

    /** {@code bids} dùng cho {@link #findParticipatedBy(long)}. */
    public InMemoryAuctionRepository(InMemoryBidRepository bids) {
        this.bids = Objects.requireNonNull(bids);
    }

    @Override
    public Optional<Auction> findById(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<Auction> findPage(AuctionStatus status, int offset, int limit) {
        return sorted(byId.values().stream().filter(a -> status == null || a.status() == status))
                .stream().skip(offset).limit(limit).toList();
    }

    @Override
    public List<Auction> findPublicPage(AuctionStatus status, int offset, int limit) {
        if (status == AuctionStatus.PENDING_APPROVAL || status == AuctionStatus.REJECTED) {
            return List.of();
        }
        return sorted(byId.values().stream()
                .filter(a -> isPublicStatus(a.status()))
                .filter(a -> status == null || a.status() == status))
                .stream().skip(offset).limit(limit).toList();
    }

    @Override
    public List<Auction> findByStatus(AuctionStatus status) {
        return sorted(byId.values().stream().filter(a -> a.status() == status));
    }

    @Override
    public List<Auction> findPendingApproval() {
        return byId.values().stream()
                .filter(a -> a.status() == AuctionStatus.PENDING_APPROVAL)
                .sorted(PENDING_ORDER)
                .toList();
    }

    @Override
    public List<Auction> findCreatedBy(long userId) {
        return byId.values().stream()
                .filter(a -> a.createdBy() == userId)
                .sorted(CREATED_ORDER)
                .toList();
    }

    @Override
    public int countPendingBy(long userId) {
        return (int) byId.values().stream()
                .filter(a -> a.createdBy() == userId && a.status() == AuctionStatus.PENDING_APPROVAL)
                .count();
    }

    @Override
    public List<Auction> findParticipatedBy(long userId) {
        return sorted(byId.values().stream().filter(a -> bids.hasBid(a.id(), userId)));
    }

    @Override
    public List<Auction> findWonBy(long userId) {
        return sorted(byId.values().stream().filter(a -> a.winnerId() != null && a.winnerId() == userId));
    }

    @Override
    public Auction insert(NewAuction n) {
        Auction auction = new Auction(nextId.getAndIncrement(), n.title(), n.description(), n.startingPrice(),
                n.minIncrement(), n.startingPrice(), null, null, n.startsAt(), n.endsAt(), 0,
                n.initialStatus(), 1, n.createdBy(), n.createdAt(), null, null, null);
        byId.put(auction.id(), auction);
        return auction;
    }

    @Override
    public void update(Auction auction) {
        if (byId.replace(auction.id(), auction) == null) {
            throw new DataAccessException("Không có phiên " + auction.id(), null);
        }
    }

    @Override
    public boolean deleteIfPending(long auctionId) {
        Auction current = byId.get(auctionId);
        if (current == null || current.status() != AuctionStatus.PENDING_APPROVAL) {
            return false;
        }
        return byId.remove(auctionId, current);
    }

    private static boolean isPublicStatus(AuctionStatus status) {
        return status == AuctionStatus.SCHEDULED
                || status == AuctionStatus.ACTIVE
                || status == AuctionStatus.FINISHED;
    }

    private static List<Auction> sorted(Stream<Auction> stream) {
        return stream.sorted(ORDER).toList();
    }
}
