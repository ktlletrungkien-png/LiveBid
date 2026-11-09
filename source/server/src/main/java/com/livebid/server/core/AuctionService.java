package com.livebid.server.core;

import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.BidReceipt;
import com.livebid.common.dto.BidView;
import com.livebid.common.dto.MyAuctionFilter;
import com.livebid.common.dto.PageResult;
import com.livebid.common.dto.RoomSnapshot;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Nơi <b>duy nhất</b> đọc/ghi trạng thái đấu giá. <b>Kiên cài đặt</b>; mọi đường vào (TCP, WebSocket,
 * REST, scheduler) đều gọi interface này, không ai truy cập repository đấu giá trực tiếp.
 *
 * <p>Mọi hàm chỉ đưa tác vụ vào <b>một single-thread executor</b> rồi trả về {@link CompletableFuture} ngay.
 * Thứ tự vào executor là thứ tự xử lý. Người gọi không được chặn chờ future trên luồng đọc socket
 * hay luồng giao diện; hãy dùng {@code thenAccept}/{@code whenComplete}.
 *
 * <p>Lỗi nghiệp vụ: future hoàn thành với {@link ServiceException}, ví dụ {@code BID_TOO_LOW} kèm
 * {@code details.minimumAllowed}.
 *
 * <p>Sự kiện AUCTION_* được service tự đẩy tới các {@link ClientChannel} trong phòng <b>sau khi commit</b>;
 * người gọi không cần tự phát sự kiện.
 */
public interface AuctionService {

    // ---------------------------------------------------------------- tra cứu (đã đăng nhập)

    /** {@code status} null = tất cả. {@code page} từ 1; {@code pageSize} 1–50. */
    CompletableFuture<PageResult<AuctionView>> listAuctions(AuctionStatus status, int page, int pageSize);

    /** Lỗi: AUCTION_NOT_FOUND. */
    CompletableFuture<AuctionView> getAuction(long auctionId);

    /** Mới nhất đứng đầu; {@code beforeBidId} null = trang đầu; {@code limit} 1–50. */
    CompletableFuture<List<BidView>> getBidHistory(long auctionId, Long beforeBidId, int limit);

    CompletableFuture<List<AuctionView>> getMyAuctions(UserPrincipal user, MyAuctionFilter filter);

    // ---------------------------------------------------------------- phòng đấu giá

    /** Vào phòng, nhận snapshot đầy đủ; từ đây kênh nhận sự kiện của phiên. Lỗi: AUCTION_NOT_FOUND. */
    CompletableFuture<RoomSnapshot> joinAuction(UserPrincipal user, ClientChannel channel, long auctionId);

    CompletableFuture<Void> leaveAuction(ClientChannel channel, long auctionId);

    /** Gọi khi kết nối đóng: rời mọi phòng. Không ném lỗi. */
    void onDisconnect(ClientChannel channel);

    /**
     * Đặt giá. Người đặt lấy từ {@code user} (phiên đăng nhập), không lấy từ payload.
     * Gửi lại cùng {@code requestId} thì trả biên nhận cũ, không tạo bid mới.
     *
     * <p>Lỗi: FORBIDDEN (ADMIN), AUCTION_NOT_FOUND, NOT_IN_ROOM, AUCTION_NOT_STARTED, AUCTION_FINISHED,
     * VALIDATION_ERROR (giá ≤ 0 hoặc quá giới hạn), BID_TOO_LOW.
     */
    CompletableFuture<BidReceipt> placeBid(UserPrincipal user, ClientChannel channel,
                                           long auctionId, long amount, String requestId);

    // ---------------------------------------------------------------- quản trị (ADMIN)

    /** Lỗi: FORBIDDEN, VALIDATION_ERROR. */
    CompletableFuture<AuctionView> createAuction(UserPrincipal admin, AuctionDraft draft);

    /** Chỉ khi phiên còn SCHEDULED. Lỗi: FORBIDDEN, AUCTION_NOT_FOUND, VALIDATION_ERROR, VERSION_CONFLICT. */
    CompletableFuture<AuctionView> updateAuction(UserPrincipal admin, long auctionId,
                                                 long expectedVersion, AuctionDraft draft);
}
