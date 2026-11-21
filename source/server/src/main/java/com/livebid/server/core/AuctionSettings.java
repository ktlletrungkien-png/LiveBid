package com.livebid.server.core;

/**
 * Tham số nghiệp vụ đấu giá (đọc từ {@code server.properties}, khóa {@code auction.*}).
 *
 * @param antiSnipingWindowMillis           bid hợp lệ khi còn ≤ khoảng này thì gia hạn phiên
 * @param antiSnipingExtensionMillis        số mili giây cộng thêm vào {@code endsAt} mỗi lần gia hạn
 * @param maxExtensions                     số lần gia hạn tối đa của một phiên (để phiên không kéo dài vô hạn)
 * @param announceLeadMillis                báo "phiên sắp bắt đầu" (multicast) trước giờ mở bao lâu
 * @param snapshotBidCount                  số bid gần nhất trả về khi JOIN_AUCTION
 * @param minLeadTimeForUserSubmissionMillis giờ bắt đầu phiên do USER đăng phải sau hiện tại ít nhất khoảng này
 */
public record AuctionSettings(
        long antiSnipingWindowMillis,
        long antiSnipingExtensionMillis,
        int maxExtensions,
        long announceLeadMillis,
        int snapshotBidCount,
        long minLeadTimeForUserSubmissionMillis) {

    /** Giá tối đa của một mức giá (giá khởi điểm, bước giá, giá đặt): 1.000.000.000.000đ. */
    public static final long MAX_PRICE = 1_000_000_000_000L;

    /** Mặc định 30 phút để ADMIN kịp duyệt phiên do USER gửi. */
    public static final long DEFAULT_USER_SUBMIT_MIN_LEAD_MILLIS = 30 * 60_000L;

    public AuctionSettings {
        if (antiSnipingWindowMillis < 0 || antiSnipingExtensionMillis < 0 || maxExtensions < 0
                || announceLeadMillis < 0 || minLeadTimeForUserSubmissionMillis < 0
                || snapshotBidCount < 1 || snapshotBidCount > 50) {
            throw new IllegalArgumentException("Tham số auction.* không hợp lệ: các giá trị phải ≥ 0, "
                    + "snapshotBidCount trong 1–50");
        }
    }

    /** Constructor tương thích 5 tham số, dùng mặc định 30 phút cho {@code minLeadTimeForUserSubmissionMillis}. */
    public AuctionSettings(long antiSnipingWindowMillis, long antiSnipingExtensionMillis, int maxExtensions,
                           long announceLeadMillis, int snapshotBidCount) {
        this(antiSnipingWindowMillis, antiSnipingExtensionMillis, maxExtensions, announceLeadMillis,
                snapshotBidCount, DEFAULT_USER_SUBMIT_MIN_LEAD_MILLIS);
    }

    /** 10 giây cuối → cộng 30 giây, tối đa 10 lần; báo trước 5 phút; snapshot 20 bid; USER gửi trước ≥ 30 phút. */
    public static AuctionSettings defaults() {
        return new AuctionSettings(10_000, 30_000, 10, 5 * 60_000, 20, DEFAULT_USER_SUBMIT_MIN_LEAD_MILLIS);
    }
}
