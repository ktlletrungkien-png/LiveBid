package com.livebid.common.dto;

/**
 * Dữ liệu ADMIN nhập khi tạo/sửa phiên (payload của CREATE_AUCTION, body của POST /api/auctions).
 * Server kiểm tra: giá khởi điểm và bước giá &gt; 0, {@code endsAt > startsAt}, tiêu đề không rỗng.
 */
public record AuctionDraft(
        String title,
        String description,
        long startingPrice,
        long minIncrement,
        long startsAt,
        long endsAt) {
}
