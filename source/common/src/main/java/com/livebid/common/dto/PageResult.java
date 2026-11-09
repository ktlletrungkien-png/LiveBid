package com.livebid.common.dto;

import java.util.List;

/** Một trang kết quả. {@code page} bắt đầu từ 1. */
public record PageResult<T>(List<T> items, int page, int pageSize, boolean hasMore) {
}
