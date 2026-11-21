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

    /**
     * Danh sách công khai: chỉ gồm {@code SCHEDULED}, {@code ACTIVE}, {@code FINISHED};
     * {@code status} null = cả ba. Nếu truyền {@code PENDING_APPROVAL} hoặc {@code REJECTED} trả rỗng.
     */
    List<Auction> findPublicPage(AuctionStatus status, int offset, int limit);

    /** Toàn bộ phiên ở một trạng thái (scheduler dùng khi khởi động và khi chốt phiên quá hạn). */
    List<Auction> findByStatus(AuctionStatus status);

    /** Hàng chờ duyệt của ADMIN: chỉ {@code PENDING_APPROVAL}, cũ nhất trước ({@code created_at, id}). */
    List<Auction> findPendingApproval();

    /** Toàn bộ phiên do {@code userId} tạo ở mọi trạng thái, mới nhất trước ({@code created_at DESC, id DESC}). */
    List<Auction> findCreatedBy(long userId);

    /** Đếm số phiên đang {@code PENDING_APPROVAL} của {@code userId}. */
    int countPendingBy(long userId);

    /** Phiên mà {@code userId} đã đặt ít nhất một bid. */
    List<Auction> findParticipatedBy(long userId);

    /** Phiên mà {@code userId} thắng. */
    List<Auction> findWonBy(long userId);

    /** Thêm và trả về bản có id (currentPrice = startingPrice, status = initialStatus, version = 1). */
    Auction insert(NewAuction auction);

    /** Ghi đè toàn bộ trường theo {@code auction.id()}. */
    void update(Auction auction);

    /** Chỉ xóa bản ghi nếu phiên đang ở trạng thái {@code PENDING_APPROVAL}; trả {@code true} nếu đã xóa. */
    boolean deleteIfPending(long auctionId);
}
