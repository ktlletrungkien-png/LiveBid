package com.livebid.common.dto;

/**
 * Vòng đời phiên:
 * {@code PENDING_APPROVAL} (USER đăng) ──duyệt──► {@code SCHEDULED} ──► {@code ACTIVE} ──► {@code FINISHED}
 * hoặc bị từ chối ({@code REJECTED}). Phiên ADMIN tạo vào thẳng {@code SCHEDULED}.
 */
public enum AuctionStatus {
    PENDING_APPROVAL,
    REJECTED,
    SCHEDULED,
    ACTIVE,
    FINISHED
}
