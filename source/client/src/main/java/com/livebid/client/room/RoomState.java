package com.livebid.client.room;

import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import java.util.Objects;

/**
 * Trạng thái một phiên đấu giá mà client đang xem — logic thuần, không phụ thuộc Swing nên test được.
 *
 * <p>Server gửi {@link AuctionView} kèm {@code version} tăng dần. Client <b>bỏ qua</b> bản có
 * {@code version} nhỏ hơn hoặc bằng bản đang giữ, nhờ vậy sự kiện về muộn không ghi đè trạng thái mới hơn
 * (ví dụ biên nhận {@code PLACE_BID} cũ không được đè lên {@code AUCTION_UPDATED} mới).
 *
 * <p>Đồng hồ đếm ngược tính theo giờ server: lúc nhận bản mới lưu
 * {@code offset = serverTime - giờ máy}, sau đó {@code remainingMillis(now) = endsAt - (now + offset)}.
 * Không dùng trực tiếp giờ máy client vì đồng hồ máy có thể lệch vài phút.
 */
public final class RoomState {

    private boolean present;
    private long auctionId;
    private long version = -1;
    private String title = "";
    private String description = "";
    private AuctionStatus status;
    private long startingPrice;
    private long minIncrement;
    private long currentPrice;
    private boolean hasBids;
    private String leaderDisplayName;
    private String winnerDisplayName;
    private long startsAt;
    private long endsAt;
    private int extensionCount;
    private long serverOffsetMillis;

    /** Đã nhận bản {@link AuctionView} nào chưa. */
    public boolean isPresent() {
        return present;
    }

    /** Áp dụng bản mới, dùng giờ máy hiện tại để tính offset. */
    public boolean apply(AuctionView view) {
        return apply(view, System.currentTimeMillis());
    }

    /**
     * Áp dụng bản mới với giờ máy cho trước (dùng trong test).
     *
     * @return {@code true} nếu bản này mới hơn và đã được nhận; {@code false} nếu bị bỏ qua
     */
    public boolean apply(AuctionView view, long nowMillis) {
        Objects.requireNonNull(view, "view");
        if (present && view.auctionId() == auctionId && view.version() <= version) {
            return false;
        }
        auctionId = view.auctionId();
        version = view.version();
        title = view.title() == null ? "" : view.title();
        description = view.description() == null ? "" : view.description();
        status = view.status();
        startingPrice = view.startingPrice();
        minIncrement = view.minIncrement();
        currentPrice = view.currentPrice();
        hasBids = view.hasBids();
        leaderDisplayName = view.leaderDisplayName();
        winnerDisplayName = view.winnerDisplayName();
        startsAt = view.startsAt();
        endsAt = view.endsAt();
        extensionCount = view.extensionCount();
        serverOffsetMillis = view.serverTime() - nowMillis;
        present = true;
        return true;
    }

    /** Thời gian còn lại theo giờ máy cho trước; không bao giờ âm. */
    public long remainingMillis(long nowMillis) {
        if (!present) {
            return 0;
        }
        long serverNow = nowMillis + serverOffsetMillis;
        return Math.max(0, endsAt - serverNow);
    }

    /** Thời gian còn lại theo giờ máy hiện tại. */
    public long remainingMillis() {
        return remainingMillis(System.currentTimeMillis());
    }

    /** Giá thấp nhất được đặt tiếp: giá khởi điểm khi chưa có bid, ngược lại giá hiện tại + bước giá. */
    public long minimumNextBid() {
        if (!present) {
            return 0;
        }
        return hasBids ? currentPrice + minIncrement : startingPrice;
    }

    public long auctionId() {
        return auctionId;
    }

    public long version() {
        return version;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public AuctionStatus status() {
        return status;
    }

    public long startingPrice() {
        return startingPrice;
    }

    public long minIncrement() {
        return minIncrement;
    }

    public long currentPrice() {
        return currentPrice;
    }

    public boolean hasBids() {
        return hasBids;
    }

    public String leaderDisplayName() {
        return leaderDisplayName;
    }

    public String winnerDisplayName() {
        return winnerDisplayName;
    }

    public long startsAt() {
        return startsAt;
    }

    public long endsAt() {
        return endsAt;
    }

    public int extensionCount() {
        return extensionCount;
    }

    /** Chênh lệch giữa giờ server và giờ máy tại lần nhận gần nhất. */
    public long serverOffsetMillis() {
        return serverOffsetMillis;
    }

    /** Phiên đã kết thúc theo trạng thái server. */
    public boolean isFinished() {
        return status == AuctionStatus.FINISHED;
    }
}
