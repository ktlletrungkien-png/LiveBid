package com.livebid.server.tcp;

import static com.livebid.server.tcp.CommandSupport.payload;
import static com.livebid.server.tcp.CommandSupport.requireAdmin;
import static com.livebid.server.tcp.CommandSupport.requireLogin;

import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.BidReceipt;
import com.livebid.common.dto.BidView;
import com.livebid.common.dto.PageResult;
import com.livebid.common.dto.RoomSnapshot;
import com.livebid.common.payload.AuctionIdPayload;
import com.livebid.common.payload.BidHistoryPayload;
import com.livebid.common.payload.ListAuctionsPayload;
import com.livebid.common.payload.MyAuctionsPayload;
import com.livebid.common.payload.PlaceBidPayload;
import com.livebid.common.payload.RejectAuctionPayload;
import com.livebid.common.payload.UpdateAuctionPayload;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageType;
import com.livebid.common.protocol.ProtocolException;
import com.livebid.server.core.AuctionService;
import com.livebid.server.core.ClientChannel;
import com.livebid.server.core.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Các lệnh đấu giá trên TCP. Lớp này <b>không chứa nghiệp vụ</b>: mỗi handler làm đúng ba việc —
 * kiểm tra đã đăng nhập (và quyền), đọc payload, gọi {@link AuctionService}. Mọi luật đấu giá nằm ở service.
 *
 * <p>Handler chạy trên luồng đọc của kết nối nên chỉ trả về future, không chờ kết quả.
 */
public final class AuctionCommands {

    /** LIST_AUCTIONS không gửi payload thì lấy trang đầu với cỡ trang này. */
    static final int DEFAULT_PAGE_SIZE = 20;

    private final AuctionService auctions;

    public AuctionCommands(AuctionService auctions) {
        this.auctions = Objects.requireNonNull(auctions);
    }

    public void registerTo(MessageRouter router) {
        router.register(MessageType.LIST_AUCTIONS, this::listAuctions);
        router.register(MessageType.GET_AUCTION, this::getAuction);
        router.register(MessageType.JOIN_AUCTION, this::joinAuction);
        router.register(MessageType.LEAVE_AUCTION, this::leaveAuction);
        router.register(MessageType.GET_BID_HISTORY, this::getBidHistory);
        router.register(MessageType.GET_MY_AUCTIONS, this::getMyAuctions);
        router.register(MessageType.PLACE_BID, this::placeBid);
        router.register(MessageType.CREATE_AUCTION, this::createAuction);
        router.register(MessageType.UPDATE_AUCTION, this::updateAuction);
        router.register(MessageType.CANCEL_AUCTION, this::cancelAuction);
        router.register(MessageType.GET_PENDING_AUCTIONS, this::getPendingAuctions);
        router.register(MessageType.APPROVE_AUCTION, this::approveAuction);
        router.register(MessageType.REJECT_AUCTION, this::rejectAuction);
    }

    // ---------------------------------------------------------------- tra cứu

    private CompletableFuture<PageResult<AuctionView>> listAuctions(ClientChannel session, Message request)
            throws ProtocolException {
        requireLogin(session);
        ListAuctionsPayload p = request.payloadAs(ListAuctionsPayload.class);
        if (p == null) {
            return auctions.listAuctions(null, null, 1, DEFAULT_PAGE_SIZE);
        }
        return auctions.listAuctions(p.status(), p.sort(), p.page(), p.pageSize());
    }

    private CompletableFuture<AuctionView> getAuction(ClientChannel session, Message request)
            throws ProtocolException {
        UserPrincipal viewer = requireLogin(session);
        return auctions.getAuction(viewer, payload(request, AuctionIdPayload.class).auctionId());
    }

    private CompletableFuture<List<BidView>> getBidHistory(ClientChannel session, Message request)
            throws ProtocolException {
        requireLogin(session);
        BidHistoryPayload p = payload(request, BidHistoryPayload.class);
        return auctions.getBidHistory(p.auctionId(), p.beforeBidId(), p.limit());
    }

    private CompletableFuture<List<AuctionView>> getMyAuctions(ClientChannel session, Message request)
            throws ProtocolException {
        MyAuctionsPayload p = payload(request, MyAuctionsPayload.class);
        return auctions.getMyAuctions(requireLogin(session), p.filter());
    }

    // ---------------------------------------------------------------- phòng đấu giá

    /** Trả snapshot đầy đủ; từ đây kết nối nhận sự kiện AUCTION_* của phiên. */
    private CompletableFuture<RoomSnapshot> joinAuction(ClientChannel session, Message request)
            throws ProtocolException {
        return auctions.joinAuction(requireLogin(session), session,
                payload(request, AuctionIdPayload.class).auctionId());
    }

    private CompletableFuture<Void> leaveAuction(ClientChannel session, Message request)
            throws ProtocolException {
        requireLogin(session);
        return auctions.leaveAuction(session, payload(request, AuctionIdPayload.class).auctionId());
    }

    // ---------------------------------------------------------------- đặt giá

    /**
     * Người đặt lấy từ phiên đăng nhập, không từ payload. {@code requestId} của yêu cầu được chuyển nguyên
     * cho service: client gửi lại cùng requestId (do mất phản hồi) thì nhận biên nhận cũ, không tạo bid mới.
     * ADMIN bị service từ chối {@code FORBIDDEN}; chưa JOIN thì {@code NOT_IN_ROOM}.
     */
    private CompletableFuture<BidReceipt> placeBid(ClientChannel session, Message request)
            throws ProtocolException {
        UserPrincipal user = requireLogin(session);
        PlaceBidPayload p = payload(request, PlaceBidPayload.class);
        return auctions.placeBid(user, session, p.auctionId(), p.amount(), request.requestId());
    }

    // ---------------------------------------------------------------- tạo, sửa, hủy & kiểm duyệt

    private CompletableFuture<AuctionView> createAuction(ClientChannel session, Message request)
            throws ProtocolException {
        UserPrincipal user = requireLogin(session);
        return auctions.createAuction(user, payload(request, AuctionDraft.class));
    }

    private CompletableFuture<AuctionView> updateAuction(ClientChannel session, Message request)
            throws ProtocolException {
        UserPrincipal user = requireLogin(session);
        UpdateAuctionPayload p = payload(request, UpdateAuctionPayload.class);
        if (p.draft() == null) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "Thiếu draft trong UPDATE_AUCTION");
        }
        return auctions.updateAuction(user, p.auctionId(), p.expectedVersion(), p.draft());
    }

    private CompletableFuture<Void> cancelAuction(ClientChannel session, Message request)
            throws ProtocolException {
        UserPrincipal user = requireLogin(session);
        return auctions.cancelAuction(user, payload(request, AuctionIdPayload.class).auctionId());
    }

    private CompletableFuture<List<AuctionView>> getPendingAuctions(ClientChannel session, Message request)
            throws ProtocolException {
        return auctions.getPendingAuctions(requireAdmin(session));
    }

    private CompletableFuture<AuctionView> approveAuction(ClientChannel session, Message request)
            throws ProtocolException {
        return auctions.approveAuction(requireAdmin(session),
                payload(request, AuctionIdPayload.class).auctionId());
    }

    private CompletableFuture<AuctionView> rejectAuction(ClientChannel session, Message request)
            throws ProtocolException {
        UserPrincipal admin = requireAdmin(session);
        RejectAuctionPayload p = payload(request, RejectAuctionPayload.class);
        return auctions.rejectAuction(admin, p.auctionId(), p.reason());
    }
}
