package com.livebid.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.AuctionStatus;
import com.livebid.server.model.Auction;
import com.livebid.server.model.Bid;
import com.livebid.server.model.NewAuction;
import com.livebid.server.repo.memory.InMemoryAuctionRepository;
import com.livebid.server.repo.memory.InMemoryBidRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hành vi mong đợi của repository. Bản SQLite (Hoè) phải cho kết quả giống hệt;
 * có thể copy test này và đổi sang bản SQLite để kiểm tra.
 */
class InMemoryRepositoryTest {

    private final InMemoryBidRepository bids = new InMemoryBidRepository();
    private final InMemoryAuctionRepository auctions = new InMemoryAuctionRepository(bids);

    @Test
    void newAuctionStartsScheduledAtStartingPrice() {
        Auction a = auctions.insert(new NewAuction("Bàn phím cơ", "", 200_000, 20_000, 1000, 2000, 1, 0));
        assertEquals(AuctionStatus.SCHEDULED, a.status());
        assertEquals(200_000, a.currentPrice());
        assertEquals(1, a.version());
        assertFalse(a.hasBids());

        Auction afterBid = a.withBid(240_000, 7);
        auctions.update(afterBid);
        assertEquals(240_000, auctions.findById(a.id()).orElseThrow().currentPrice());
        assertEquals(2, auctions.findById(a.id()).orElseThrow().version());
    }

    @Test
    void bidHistoryIsNewestFirstAndPaged() {
        for (int i = 1; i <= 5; i++) {
            bids.insert(1, 7, 100_000L * i, i, "r" + i);
        }
        bids.insert(2, 7, 1, 1, "other");

        List<Bid> first = bids.findHistory(1, null, 2);
        assertEquals(List.of(5L, 4L), first.stream().map(Bid::id).toList());

        List<Bid> second = bids.findHistory(1, first.get(1).id(), 2);
        assertEquals(List.of(3L, 2L), second.stream().map(Bid::id).toList());
        assertEquals(5, bids.countByAuction(1));
    }

    @Test
    void participatedAndWon() {
        Auction a = auctions.insert(new NewAuction("A", "", 1, 1, 0, 10, 1, 0));
        Auction b = auctions.insert(new NewAuction("B", "", 1, 1, 0, 10, 1, 0));
        bids.insert(a.id(), 7, 1, 1, "r1");
        auctions.update(a.withStatus(AuctionStatus.FINISHED, 7L));

        assertEquals(List.of(a.id()), auctions.findParticipatedBy(7).stream().map(Auction::id).toList());
        assertEquals(List.of(a.id()), auctions.findWonBy(7).stream().map(Auction::id).toList());
        assertTrue(auctions.findWonBy(8).isEmpty());
        assertEquals(List.of(b.id()), auctions.findByStatus(AuctionStatus.SCHEDULED).stream()
                .map(Auction::id).toList());
    }
}
