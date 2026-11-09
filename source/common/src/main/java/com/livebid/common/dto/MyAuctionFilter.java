package com.livebid.common.dto;

/** Bộ lọc của GET_MY_AUCTIONS và REST /api/me/auctions?filter=... */
public enum MyAuctionFilter {
    /** Các phiên mình đã đặt ít nhất một bid. */
    PARTICIPATED,
    /** Các phiên mình thắng. */
    WON
}
