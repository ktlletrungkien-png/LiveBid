package com.livebid.server.repo;

import com.livebid.common.dto.AuctionStatus;
import com.livebid.server.model.Auction;
import com.livebid.server.model.NewAuction;
import java.util.List;
import java.util.Optional;

/** Bảng {@code auctions}. <b>Hoè cài đặt bản SQLite</b> trong {@code server.db}. */
public interface AuctionRepository {

    Optional<Auction> findById(long id);

    /** {@code status} null = tất cả; sắp xếp theo {@code starts_at} tăng dần rồi {@code id}. */
    List<Auction> findPage(AuctionStatus status, int offset, int limit);

    /** Toàn bộ phiên ở một trạng thái (scheduler dùng khi khởi động và khi chốt phiên quá hạn). */
    List<Auction> findByStatus(AuctionStatus status);

    /** Phiên mà {@code userId} đã đặt ít nhất một bid. */
    List<Auction> findParticipatedBy(long userId);

    /** Phiên mà {@code userId} thắng. */
    List<Auction> findWonBy(long userId);

    /** Thêm và trả về bản có id (currentPrice = startingPrice, status = SCHEDULED, version = 1). */
    Auction insert(NewAuction auction);

    /** Ghi đè toàn bộ trường theo {@code auction.id()}. */
    void update(Auction auction);
}
