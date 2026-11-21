package com.livebid.common.dto;

/**
 * Trạng thái một phiên tại một thời điểm. Dùng làm payload cho GET_AUCTION, LIST_AUCTIONS (từng phần tử)
 * và mọi sự kiện AUCTION_*.
 *
 * <p>Client bỏ qua bản có {@code version} nhỏ hơn hoặc bằng bản đang hiển thị. Đồng hồ đếm ngược tính
 * theo {@code endsAt - serverTime}, không dùng giờ máy client.
 *
 * @param hasBids           {@code false} thì {@code currentPrice} là "Giá khởi điểm", chưa phải "Giá đã được đặt"
 * @param leaderDisplayName người đang dẫn đầu; null nếu chưa có bid
 * @param winnerDisplayName chỉ có khi FINISHED và có người thắng
 * @param serverTime        giờ server lúc tạo bản này (epoch ms UTC)
 * @param ownerId           mã người tạo phiên ({@code created_by})
 * @param ownerDisplayName  tên hiển thị của người tạo phiên
 * @param ownerRole         vai trò người tạo ({@code ADMIN} → giao diện hiển thị "Ban tổ chức")
 * @param rejectReason      lý do từ chối khi {@code REJECTED}; chỉ trả cho chủ phiên và ADMIN
 */
public record AuctionView(
        long auctionId,
        String title,
        String description,
        AuctionStatus status,
        long startingPrice,
        long minIncrement,
        long currentPrice,
        boolean hasBids,
        String leaderDisplayName,
        String winnerDisplayName,
        long startsAt,
        long endsAt,
        int extensionCount,
        long version,
        long serverTime,
        long ownerId,
        String ownerDisplayName,
        Role ownerRole,
        String rejectReason) {

    /** Mức giá thấp nhất được chấp nhận cho bid tiếp theo. */
    public long minimumNextBid() {
        return hasBids ? currentPrice + minIncrement : startingPrice;
    }
}
