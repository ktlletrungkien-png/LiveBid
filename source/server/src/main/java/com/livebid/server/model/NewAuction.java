package com.livebid.server.model;

/** Dữ liệu để thêm phiên mới. Repository đặt: currentPrice = startingPrice, status = SCHEDULED, version = 1. */
public record NewAuction(
        String title,
        String description,
        long startingPrice,
        long minIncrement,
        long startsAt,
        long endsAt,
        long createdBy,
        long createdAt) {
}
