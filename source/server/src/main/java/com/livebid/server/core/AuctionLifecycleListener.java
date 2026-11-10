package com.livebid.server.core;

import com.livebid.common.dto.AuctionView;

/**
 * Điểm móc cho các thành phần ngoài đường găng của bid:
 * <ul>
 *   <li>{@code UdpAnnouncer} (Quân) gửi multicast khi {@link #auctionStartingSoon}.</li>
 *   <li>{@code SettlementClient} (Quân) thử gửi RMI ngay khi {@link #auctionFinished}; bản ghi
 *       {@code settlement_queue} đã được ghi trong cùng transaction chốt phiên, nên lỡ có lỗi vẫn gửi bù được.</li>
 * </ul>
 *
 * <p>Được gọi trên <b>luồng executor nghiệp vụ, sau commit</b>. Cài đặt phải trả về ngay (đưa việc gửi
 * mạng sang luồng khác); lỗi ném ra chỉ được ghi log, không ảnh hưởng phiên đấu giá.
 */
public interface AuctionLifecycleListener {

    AuctionLifecycleListener NONE = new AuctionLifecycleListener() {
    };

    /** Phiên còn ≤ {@link AuctionSettings#announceLeadMillis()} nữa thì mở; gọi một lần cho mỗi giờ mở. */
    default void auctionStartingSoon(AuctionView auction) {
    }

    /** Phiên vừa chốt (có hoặc không có người thắng). */
    default void auctionFinished(AuctionView auction) {
    }
}
