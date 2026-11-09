package com.livebid.server.model;

import com.livebid.common.dto.AuctionStatus;

/**
 * Một dòng bảng {@code auctions}. Bất biến: muốn đổi thì tạo bản mới bằng các hàm {@code with...}
 * rồi gọi {@code AuctionRepository.update}.
 *
 * @param currentPrice    khởi tạo bằng {@code startingPrice}
 * @param currentBidderId null nếu chưa có bid
 * @param winnerId        chỉ gán khi chốt phiên
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
        long createdAt) {

    public boolean hasBids() {
        return currentBidderId != null;
    }

    /** Sau một bid hợp lệ: đổi giá, người dẫn đầu, tăng version. */
    public Auction withBid(long price, long bidderId) {
        return new Auction(id, title, description, startingPrice, minIncrement, price, bidderId, winnerId,
                startsAt, endsAt, extensionCount, status, version + 1, createdBy, createdAt);
    }

    /** Gia hạn chống bắn tỉa: đổi endsAt, tăng extensionCount và version. */
    public Auction withExtension(long newEndsAt) {
        return new Auction(id, title, description, startingPrice, minIncrement, currentPrice, currentBidderId,
                winnerId, startsAt, newEndsAt, extensionCount + 1, status, version + 1, createdBy, createdAt);
    }

    /** Đổi trạng thái (mở/chốt phiên); {@code winner} chỉ có nghĩa khi FINISHED. */
    public Auction withStatus(AuctionStatus newStatus, Long winner) {
        return new Auction(id, title, description, startingPrice, minIncrement, currentPrice, currentBidderId,
                winner, startsAt, endsAt, extensionCount, newStatus, version + 1, createdBy, createdAt);
    }

    /** ADMIN sửa phiên chưa bắt đầu. */
    public Auction withDetails(String newTitle, String newDescription, long newStartingPrice,
                               long newMinIncrement, long newStartsAt, long newEndsAt) {
        return new Auction(id, newTitle, newDescription, newStartingPrice, newMinIncrement, newStartingPrice,
                null, null, newStartsAt, newEndsAt, 0, status, version + 1, createdBy, createdAt);
    }
}
