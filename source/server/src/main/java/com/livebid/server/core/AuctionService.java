package com.livebid.server.core;

import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionSort;
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

    /**
     * {@code status} null = tất cả. {@code sort} null = {@link AuctionSort#DEFAULT}.
     * {@code page} từ 1; {@code pageSize} 1–50.
     */
    CompletableFuture<PageResult<AuctionView>> listAuctions(AuctionStatus status, AuctionSort sort, int page,
            int pageSize);

    /**
     * Xem chi tiết phiên. Phiên {@code PENDING_APPROVAL} hoặc {@code REJECTED} chỉ chủ phiên và ADMIN
     * xem được; người khác nhận {@code AUCTION_NOT_FOUND}.
     */
    CompletableFuture<AuctionView> getAuction(UserPrincipal viewer, long auctionId);

    /** Mới nhất đứng đầu; {@code beforeBidId} null = trang đầu; {@code limit} 1–50. */
    CompletableFuture<List<BidView>> getBidHistory(long auctionId, Long beforeBidId, int limit);

    CompletableFuture<List<AuctionView>> getMyAuctions(UserPrincipal user, MyAuctionFilter filter);

    // ---------------------------------------------------------------- phòng đấu giá

    /**
     * Vào phòng, nhận snapshot đầy đủ; từ đây kênh nhận sự kiện của phiên.
     * Phiên chưa duyệt chỉ trả {@code AUCTION_NOT_FOUND} với người lạ, không cho vào phòng kể cả chủ phiên.
     */
    CompletableFuture<RoomSnapshot> joinAuction(UserPrincipal user, ClientChannel channel, long auctionId);

    CompletableFuture<Void> leaveAuction(ClientChannel channel, long auctionId);

    /** Gọi khi kết nối đóng: rời mọi phòng. Không ném lỗi. */
    void onDisconnect(ClientChannel channel);

    /**
     * Đặt giá. Người đặt lấy từ {@code user} (phiên đăng nhập), không lấy từ payload.
     * Gửi lại cùng {@code requestId} thì trả biên nhận cũ, không tạo bid mới.
     *
     * <p>Lỗi: FORBIDDEN (ADMIN), OWN_AUCTION (chủ phiên), AUCTION_NOT_FOUND, NOT_IN_ROOM,
     * AUCTION_NOT_STARTED, AUCTION_FINISHED, VALIDATION_ERROR (giá ≤ 0 hoặc quá giới hạn), BID_TOO_LOW.
     */
    CompletableFuture<BidReceipt> placeBid(UserPrincipal user, ClientChannel channel,
                                           long auctionId, long amount, String requestId);

    // ---------------------------------------------------------------- tạo, sửa, hủy & kiểm duyệt

    /**
     * Tạo phiên mới. {@code ADMIN} vào thẳng {@code SCHEDULED}; {@code USER} vào {@code PENDING_APPROVAL}
     * (yêu cầu {@code startsAt} đủ xa và không quá giới hạn phiên chờ duyệt).
     */
    CompletableFuture<AuctionView> createAuction(UserPrincipal user, AuctionDraft draft);

    /**
     * Sửa phiên: {@code USER} chỉ sửa phiên của mình đang {@code PENDING_APPROVAL};
     * {@code ADMIN} chỉ sửa phiên đang {@code SCHEDULED}.
     */
    CompletableFuture<AuctionView> updateAuction(UserPrincipal user, long auctionId,
                                                 long expectedVersion, AuctionDraft draft);

    /** Chủ phiên hủy (xóa) phiên của mình khi còn {@code PENDING_APPROVAL}. */
    CompletableFuture<Void> cancelAuction(UserPrincipal user, long auctionId);

    /** ADMIN lấy danh sách phiên đang chờ duyệt, cũ nhất đứng đầu. */
    CompletableFuture<List<AuctionView>> getPendingAuctions(UserPrincipal admin);

    /** ADMIN duyệt phiên {@code PENDING_APPROVAL} → {@code SCHEDULED}. */
    CompletableFuture<AuctionView> approveAuction(UserPrincipal admin, long auctionId);

    /** ADMIN từ chối phiên {@code PENDING_APPROVAL} → {@code REJECTED} kèm lý do (1–500 ký tự). */
    CompletableFuture<AuctionView> rejectAuction(UserPrincipal admin, long auctionId, String reason);
}
