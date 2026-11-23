package com.livebid.server.model;

import com.livebid.common.dto.AuctionStatus;
import java.util.Objects;

/**
 * Dữ liệu để thêm phiên mới.
 * Repository đặt: {@code currentPrice = startingPrice}, {@code status = initialStatus}, {@code version = 1}.
 * {@code imageId} null = không có ảnh đại diện.
 */
public record NewAuction(
        String title,
        String description,
        long startingPrice,
        long minIncrement,
        long startsAt,
        long endsAt,
        long createdBy,
        long createdAt,
        AuctionStatus initialStatus,
        String imageId) {

    public NewAuction {
        Objects.requireNonNull(initialStatus, "initialStatus");
    }

    /** Không có ảnh đại diện. */
    public NewAuction(String title, String description, long startingPrice, long minIncrement,
                      long startsAt, long endsAt, long createdBy, long createdAt, AuctionStatus initialStatus) {
        this(title, description, startingPrice, minIncrement, startsAt, endsAt, createdBy, createdAt,
                initialStatus, null);
    }

    /** Khởi tạo mặc định với trạng thái {@link AuctionStatus#SCHEDULED}. */
    public NewAuction(String title, String description, long startingPrice, long minIncrement,
                      long startsAt, long endsAt, long createdBy, long createdAt) {
        this(title, description, startingPrice, minIncrement, startsAt, endsAt,
                createdBy, createdAt, AuctionStatus.SCHEDULED);
    }
}
