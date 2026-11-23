package com.livebid.server.model;

import com.livebid.common.dto.AuctionStatus;

/**
 * Một dòng bảng {@code auctions}. Bất biến: muốn đổi thì tạo bản mới bằng các hàm {@code with...}
 * rồi gọi {@code AuctionRepository.update}.
 *
 * @param currentPrice    khởi tạo bằng {@code startingPrice}
 * @param currentBidderId null nếu chưa có bid
 * @param winnerId        chỉ gán khi chốt phiên
 * @param imageId         tên file ảnh đại diện trong kho ảnh; null nếu phiên không có ảnh
 */
public record Auction(
        long id,
        String title,
        String description,
        long startingPrice,
        long minIncrement,
        long currentPrice,
        Long currentBidderId,
        Long winnerId,
        long startsAt,
        long endsAt,
        int extensionCount,
        AuctionStatus status,
        long version,
        long createdBy,
        long createdAt,
        String rejectReason,
        Long reviewedBy,
        Long reviewedAt,
        String imageId) {

    /** Không có ảnh đại diện. */
    public Auction(long id, String title, String description, long startingPrice, long minIncrement,
                   long currentPrice, Long currentBidderId, Long winnerId, long startsAt, long endsAt,
                   int extensionCount, AuctionStatus status, long version, long createdBy, long createdAt,
                   String rejectReason, Long reviewedBy, Long reviewedAt) {
        this(id, title, description, startingPrice, minIncrement, currentPrice, currentBidderId, winnerId,
                startsAt, endsAt, extensionCount, status, version, createdBy, createdAt,
                rejectReason, reviewedBy, reviewedAt, null);
    }

    /** Khởi tạo phiên mới chưa có thông tin kiểm duyệt. */
    public Auction(long id, String title, String description, long startingPrice, long minIncrement,
                   long currentPrice, Long currentBidderId, Long winnerId, long startsAt, long endsAt,
                   int extensionCount, AuctionStatus status, long version, long createdBy, long createdAt) {
        this(id, title, description, startingPrice, minIncrement, currentPrice, currentBidderId, winnerId,
                startsAt, endsAt, extensionCount, status, version, createdBy, createdAt, null, null, null);
    }

    public boolean hasBids() {
        return currentBidderId != null;
    }

    /** Sau một bid hợp lệ: đổi giá, người dẫn đầu, tăng version. */
    public Auction withBid(long price, long bidderId) {
        return new Auction(id, title, description, startingPrice, minIncrement, price, bidderId, winnerId,
                startsAt, endsAt, extensionCount, status, version + 1, createdBy, createdAt,
                rejectReason, reviewedBy, reviewedAt, imageId);
    }

    /** Gia hạn chống bắn tỉa: đổi endsAt, tăng extensionCount và version. */
    public Auction withExtension(long newEndsAt) {
        return new Auction(id, title, description, startingPrice, minIncrement, currentPrice, currentBidderId,
                winnerId, startsAt, newEndsAt, extensionCount + 1, status, version + 1, createdBy, createdAt,
                rejectReason, reviewedBy, reviewedAt, imageId);
    }

    /** Đổi trạng thái (mở/chốt phiên); {@code winner} chỉ có nghĩa khi FINISHED. */
    public Auction withStatus(AuctionStatus newStatus, Long winner) {
        return new Auction(id, title, description, startingPrice, minIncrement, currentPrice, currentBidderId,
                winner, startsAt, endsAt, extensionCount, newStatus, version + 1, createdBy, createdAt,
                rejectReason, reviewedBy, reviewedAt, imageId);
    }

    /** ADMIN hoặc chủ phiên (khi chờ duyệt) sửa nội dung phiên; {@code newImageId} null = không có ảnh. */
    public Auction withDetails(String newTitle, String newDescription, long newStartingPrice,
                               long newMinIncrement, long newStartsAt, long newEndsAt, String newImageId) {
        return new Auction(id, newTitle, newDescription, newStartingPrice, newMinIncrement, newStartingPrice,
                null, null, newStartsAt, newEndsAt, 0, status, version + 1, createdBy, createdAt,
                rejectReason, reviewedBy, reviewedAt, newImageId);
    }

    /** ADMIN duyệt phiên đang chờ duyệt: chuyển sang SCHEDULED và lưu thông tin người duyệt. */
    public Auction withApproved(long adminId, long reviewedAtMillis) {
        return new Auction(id, title, description, startingPrice, minIncrement, currentPrice, currentBidderId,
                winnerId, startsAt, endsAt, extensionCount, AuctionStatus.SCHEDULED, version + 1,
                createdBy, createdAt, null, adminId, reviewedAtMillis, imageId);
    }

    /** ADMIN từ chối phiên đang chờ duyệt: chuyển sang REJECTED và lưu lý do. */
    public Auction withRejected(String reason, long adminId, long reviewedAtMillis) {
        return new Auction(id, title, description, startingPrice, minIncrement, currentPrice, currentBidderId,
                winnerId, startsAt, endsAt, extensionCount, AuctionStatus.REJECTED, version + 1,
                createdBy, createdAt, reason, adminId, reviewedAtMillis, imageId);
    }
}
