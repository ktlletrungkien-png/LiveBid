package com.livebid.server.core;

/**
 * Tham số nghiệp vụ đấu giá (đọc từ {@code server.properties}, khóa {@code auction.*}).
 *
 * @param antiSnipingWindowMillis    bid hợp lệ khi còn ≤ khoảng này thì gia hạn phiên
 * @param antiSnipingExtensionMillis số mili giây cộng thêm vào {@code endsAt} mỗi lần gia hạn
 * @param maxExtensions              số lần gia hạn tối đa của một phiên (để phiên không kéo dài vô hạn)
 * @param announceLeadMillis         báo "phiên sắp bắt đầu" (multicast) trước giờ mở bao lâu
 * @param snapshotBidCount           số bid gần nhất trả về khi JOIN_AUCTION
 */
public record AuctionSettings(
        long antiSnipingWindowMillis,
        long antiSnipingExtensionMillis,
        int maxExtensions,
        long announceLeadMillis,
        int snapshotBidCount) {

    /** Giá tối đa của một mức giá (giá khởi điểm, bước giá, giá đặt): 1.000.000.000.000đ. */
    public static final long MAX_PRICE = 1_000_000_000_000L;

    public AuctionSettings {
        if (antiSnipingWindowMillis < 0 || antiSnipingExtensionMillis < 0 || maxExtensions < 0
                || announceLeadMillis < 0 || snapshotBidCount < 1 || snapshotBidCount > 50) {
            throw new IllegalArgumentException("Tham số auction.* không hợp lệ: các giá trị phải ≥ 0, "
                    + "snapshotBidCount trong 1–50");
        }
    }

    /** 10 giây cuối → cộng 30 giây, tối đa 10 lần; báo trước 5 phút; snapshot 20 bid. */
    public static AuctionSettings defaults() {
        return new AuctionSettings(10_000, 30_000, 10, 5 * 60_000, 20);
    }
}
