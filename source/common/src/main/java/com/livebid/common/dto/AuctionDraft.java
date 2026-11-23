package com.livebid.common.dto;

/**
 * Dữ liệu ADMIN nhập khi tạo/sửa phiên (payload của CREATE_AUCTION, body của POST /api/auctions).
 * Server kiểm tra: giá khởi điểm và bước giá &gt; 0, {@code endsAt > startsAt}, tiêu đề không rỗng.
 *
 * @param imageId     ảnh đại diện, lấy từ {@code POST /api/images}. Khi tạo: null = không có ảnh. Khi sửa:
 *                    null = giữ ảnh cũ (client chưa hỗ trợ ảnh như Swing không làm mất ảnh), có giá trị = thay ảnh
 * @param removeImage chỉ dùng khi sửa: {@code true} = bỏ ảnh hiện tại (bỏ qua {@code imageId})
 */
public record AuctionDraft(
        String title,
        String description,
        long startingPrice,
        long minIncrement,
        long startsAt,
        long endsAt,
        String imageId,
        boolean removeImage) {

    /** Không đụng tới ảnh: tạo phiên không ảnh, hoặc sửa phiên mà giữ nguyên ảnh. */
    public AuctionDraft(String title, String description, long startingPrice, long minIncrement,
                        long startsAt, long endsAt) {
        this(title, description, startingPrice, minIncrement, startsAt, endsAt, null, false);
    }
}
