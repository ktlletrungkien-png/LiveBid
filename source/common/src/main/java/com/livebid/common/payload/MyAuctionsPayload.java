package com.livebid.common.payload;

import com.livebid.common.dto.MyAuctionFilter;

/** GET_MY_AUCTIONS. */
public record MyAuctionsPayload(MyAuctionFilter filter) {
}
