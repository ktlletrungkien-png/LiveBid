package com.livebid.server.core;

import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.BidReceipt;
import com.livebid.common.dto.BidView;
import com.livebid.common.dto.MyAuctionFilter;
import com.livebid.common.dto.PageResult;
import com.livebid.common.dto.RoomSnapshot;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageType;
import com.livebid.server.model.Auction;
import com.livebid.server.model.Bid;
import com.livebid.server.model.NewAuction;
import com.livebid.server.model.User;
import com.livebid.server.repo.AuctionRepository;
import com.livebid.server.repo.BidRepository;
import com.livebid.server.repo.SettlementQueueRepository;
import com.livebid.server.repo.TransactionManager;
import com.livebid.server.repo.UserRepository;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cài đặt {@link AuctionService}. Mọi hàm đưa tác vụ vào {@link BusinessExecutor} (một luồng), nên toàn bộ
 * trạng thái bên dưới (phòng, cache chống gửi trùng, danh sách đã thông báo) chỉ được chạm từ luồng đó
 * và không cần khóa.
 *
 * <h2>Luồng đặt giá</h2>
 * <pre>
 *  xác thực (đã đăng nhập, không phải ADMIN) → requestId trùng? trả biên nhận cũ
 *  → kiểm tra miền giá trị → lấy giờ server, cập nhật trạng thái theo giờ (mở/chốt nếu đã tới hạn)
 *  → đang ở phòng? đã mở? chưa kết thúc? đủ mức tối thiểu?
 *  → transaction: thêm bid + cập nhật giá/người dẫn đầu/version (+ gia hạn nếu trong 10 giây cuối)
 *  → commit → lưu biên nhận vào cache → phát sự kiện cho cả phòng → trả biên nhận
 * </pre>
 *
 * <h2>Thời gian</h2>
 * Server là nguồn thời gian ({@link Clock}, thay được trong test). Bid hợp lệ khi
 * {@code startsAt <= now < endsAt}. Mỗi thao tác tự kiểm tra mốc thời gian và tự mở/chốt phiên nếu đã
 * tới hạn ({@link #advance}), nên kết quả <b>không phụ thuộc scheduler đã chạy hay chưa</b>; scheduler
 * chỉ để phát sự kiện đúng giờ cho phòng không có ai thao tác.
 */
public final class AuctionServiceImpl implements AuctionService {

    private static final Logger log = LoggerFactory.getLogger(AuctionServiceImpl.class);

    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_PAGE = 100_000;
    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_DESCRIPTION_LENGTH = 5_000;
    static final int MAX_REQUEST_ID_LENGTH = 100;
    /** Số biên nhận gần nhất giữ lại để chống gửi trùng (chỉ trong bộ nhớ, mất khi restart). */
    static final int DEDUP_CACHE_SIZE = 10_000;

    private final BusinessExecutor executor;
    private final AuctionRepository auctions;
    private final BidRepository bids;
    private final UserRepository users;
    private final SettlementQueueRepository settlementQueue;
    private final TransactionManager transactions;
    private final Clock clock;
    private final AuctionSettings settings;

    // ---- chỉ chạm từ luồng executor
    private final RoomManager rooms = new RoomManager();
    private final Set<Long> announced = new HashSet<>();
    private final Map<DedupKey, BidReceipt> acceptedBids = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<DedupKey, BidReceipt> eldest) {
            return size() > DEDUP_CACHE_SIZE;
        }
    };

    private volatile AuctionLifecycleListener listener = AuctionLifecycleListener.NONE;

    public AuctionServiceImpl(BusinessExecutor executor,
                              AuctionRepository auctions,
                              BidRepository bids,
                              UserRepository users,
                              SettlementQueueRepository settlementQueue,
                              TransactionManager transactions,
                              Clock clock,
                              AuctionSettings settings) {
        this.executor = Objects.requireNonNull(executor);
        this.auctions = Objects.requireNonNull(auctions);
        this.bids = Objects.requireNonNull(bids);
        this.users = Objects.requireNonNull(users);
        this.settlementQueue = Objects.requireNonNull(settlementQueue);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
        this.settings = Objects.requireNonNull(settings);
    }

    /** Gắn UdpAnnouncer/SettlementClient. Gọi khi khởi động, trước khi mở cổng. */
    public void setLifecycleListener(AuctionLifecycleListener listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    // ================================================================== tra cứu

    @Override
    public CompletableFuture<PageResult<AuctionView>> listAuctions(AuctionStatus status, int page, int pageSize) {
        return executor.submit(() -> {
            if (page < 1 || page > MAX_PAGE) {
                throw validation("page phải từ 1 đến " + MAX_PAGE);
            }
            checkPageSize(pageSize, "pageSize");
            long now = clock.millis();
            List<Auction> rows = auctions.findPage(status, (page - 1) * pageSize, pageSize + 1);
            boolean hasMore = rows.size() > pageSize;
            List<AuctionView> items = rows.stream().limit(pageSize).map(a -> toView(a, now)).toList();
            return new PageResult<>(items, page, pageSize, hasMore);
        });
    }

    @Override
    public CompletableFuture<AuctionView> getAuction(long auctionId) {
        return executor.submit(() -> {
            long now = clock.millis();
            return toView(advance(load(auctionId), now), now);
        });
    }

    @Override
    public CompletableFuture<List<BidView>> getBidHistory(long auctionId, Long beforeBidId, int limit) {
        return executor.submit(() -> {
            checkPageSize(limit, "limit");
            load(auctionId);
            return bids.findHistory(auctionId, beforeBidId, limit).stream().map(this::toView).toList();
        });
    }

    @Override
    public CompletableFuture<List<AuctionView>> getMyAuctions(UserPrincipal user, MyAuctionFilter filter) {
        return executor.submit(() -> {
            requireUser(user);
            if (filter == null) {
                throw validation("Thiếu filter (PARTICIPATED hoặc WON)");
            }
            long now = clock.millis();
            List<Auction> rows = switch (filter) {
                case PARTICIPATED -> auctions.findParticipatedBy(user.userId());
                case WON -> auctions.findWonBy(user.userId());
            };
            return rows.stream().map(a -> toView(a, now)).toList();
        });
    }

    // ================================================================== phòng đấu giá

    @Override
    public CompletableFuture<RoomSnapshot> joinAuction(UserPrincipal user, ClientChannel channel, long auctionId) {
        return executor.submit(() -> {
            requireUser(user);
            Objects.requireNonNull(channel, "channel");
            long now = clock.millis();
            Auction auction = advance(load(auctionId), now);
            rooms.join(channel, auctionId);
            List<BidView> recent = bids.findHistory(auctionId, null, settings.snapshotBidCount())
                    .stream().map(this::toView).toList();
            return new RoomSnapshot(toView(auction, now), recent);
        });
    }

    @Override
    public CompletableFuture<Void> leaveAuction(ClientChannel channel, long auctionId) {
        return executor.run(() -> rooms.leave(Objects.requireNonNull(channel, "channel"), auctionId));
    }

    @Override
    public void onDisconnect(ClientChannel channel) {
        if (channel == null) {
            return;
        }
        executor.run(() -> rooms.leaveAll(channel)).exceptionally(e -> {
            log.warn("Không gỡ được {} khỏi các phòng: {}", channel.id(), e.toString());
            return null;
        });
    }

    @Override
    public CompletableFuture<BidReceipt> placeBid(UserPrincipal user, ClientChannel channel,
                                                  long auctionId, long amount, String requestId) {
        return executor.submit(() -> {
            requireUser(user);
            if (user.isAdmin()) {
                throw new ServiceException(ErrorCode.FORBIDDEN, "Quản trị viên không được đặt giá");
            }
            if (requestId != null && requestId.length() > MAX_REQUEST_ID_LENGTH) {
                throw validation("requestId quá dài");
            }
            DedupKey key = requestId == null || requestId.isBlank() ? null : new DedupKey(user.userId(), requestId);
            if (key != null) {
                BidReceipt previous = acceptedBids.get(key);
                if (previous != null) {
                    return previous; // gửi lại cùng requestId: trả biên nhận cũ, không tạo bid mới
                }
            }
            if (amount <= 0 || amount > AuctionSettings.MAX_PRICE) {
                throw validation("Giá đặt phải từ 1đ đến " + money(AuctionSettings.MAX_PRICE));
            }

            long now = clock.millis();
            Auction auction = advance(load(auctionId), now);

            if (!rooms.isInRoom(channel, auctionId)) {
                throw new ServiceException(ErrorCode.NOT_IN_ROOM, "Hãy vào phòng đấu giá trước khi đặt giá");
            }
            if (now < auction.startsAt()) {
                throw new ServiceException(ErrorCode.AUCTION_NOT_STARTED, "Phiên chưa bắt đầu",
                        Map.of("startsAt", auction.startsAt(), "serverTime", now));
            }
            if (auction.status() == AuctionStatus.FINISHED) {
                throw new ServiceException(ErrorCode.AUCTION_FINISHED, "Phiên đã kết thúc",
                        Map.of("endsAt", auction.endsAt(), "serverTime", now));
            }
            long minimum = minimumNextBid(auction);
            if (amount < minimum) {
                throw new ServiceException(ErrorCode.BID_TOO_LOW, "Giá tối thiểu hiện tại là " + money(minimum),
                        Map.of("minimumAllowed", minimum));
            }

            boolean extend = auction.endsAt() - now <= settings.antiSnipingWindowMillis()
                    && auction.extensionCount() < settings.maxExtensions();

            BidOutcome outcome = transactions.inTransaction(() -> {
                Bid bid = bids.insert(auctionId, user.userId(), amount, now, requestId);
                Auction updated = auction.withBid(amount, user.userId());
                if (extend) {
                    updated = updated.withExtension(updated.endsAt() + settings.antiSnipingExtensionMillis());
                }
                auctions.update(updated);
                return new BidOutcome(bid, updated);
            });

            // ---- chỉ tới đây (sau commit) mới báo thành công và phát sự kiện
            BidReceipt receipt = new BidReceipt(outcome.bid().id(), auctionId, amount, now, outcome.auction().version());
            if (key != null) {
                acceptedBids.put(key, receipt);
            }
            // Một sự kiện duy nhất: client bỏ qua bản cùng version, nên gửi hai sự kiện thì bản sau bị mất.
            MessageType event = extend ? MessageType.AUCTION_EXTENDED : MessageType.AUCTION_UPDATED;
            rooms.broadcast(auctionId, Message.event(event, toView(outcome.auction(), now)));
            return receipt;
        });
    }

    // ================================================================== quản trị

    @Override
    public CompletableFuture<AuctionView> createAuction(UserPrincipal admin, AuctionDraft draft) {
        return executor.submit(() -> {
            requireAdmin(admin);
            long now = clock.millis();
            AuctionDraft clean = validateDraft(draft, now);
            Auction created = transactions.inTransaction(() -> auctions.insert(new NewAuction(
                    clean.title(), clean.description(), clean.startingPrice(), clean.minIncrement(),
                    clean.startsAt(), clean.endsAt(), admin.userId(), now)));
            log.info("{} tạo phiên #{} \"{}\"", admin.username(), created.id(), created.title());
            return toView(created, now);
        });
    }

    @Override
    public CompletableFuture<AuctionView> updateAuction(UserPrincipal admin, long auctionId,
                                                        long expectedVersion, AuctionDraft draft) {
        return executor.submit(() -> {
            requireAdmin(admin);
            long now = clock.millis();
            Auction current = advance(load(auctionId), now);
            if (current.version() != expectedVersion) {
                throw new ServiceException(ErrorCode.VERSION_CONFLICT,
                        "Phiên đã bị thay đổi, hãy tải lại rồi sửa", Map.of("currentVersion", current.version()));
            }
            if (current.status() != AuctionStatus.SCHEDULED) {
                throw validation("Chỉ sửa được phiên chưa bắt đầu");
            }
            AuctionDraft clean = validateDraft(draft, now);
            Auction updated = current.withDetails(clean.title(), clean.description(), clean.startingPrice(),
                    clean.minIncrement(), clean.startsAt(), clean.endsAt());
            transactions.inTransaction(() -> {
                auctions.update(updated);
                return null;
            });
            announced.remove(auctionId); // giờ mở có thể đã đổi: thông báo lại
            AuctionView view = toView(updated, now);
            rooms.broadcast(auctionId, Message.event(MessageType.AUCTION_UPDATED, view));
            return view;
        });
    }

    // ================================================================== scheduler

    /**
     * Mở các phiên đã tới {@code startsAt}, chốt các phiên đã tới {@code endsAt}, báo các phiên sắp mở.
     * {@link AuctionScheduler} gọi định kỳ; {@code ServerMain} gọi một lần khi khởi động để chốt các phiên
     * quá hạn trong lúc server tắt (giữ nguyên {@code endsAt} đã lưu, không tự gia hạn).
     */
    public CompletableFuture<Void> processDueAuctions() {
        return executor.run(() -> {
            long now = clock.millis();
            for (Auction auction : auctions.findByStatus(AuctionStatus.SCHEDULED)) {
                if (now >= auction.startsAt()) {
                    advance(auction, now);
                } else if (auction.startsAt() - now <= settings.announceLeadMillis() && announced.add(auction.id())) {
                    AuctionView view = toView(auction, now);
                    notifyListener(l -> l.auctionStartingSoon(view), "auctionStartingSoon");
                }
            }
            for (Auction auction : auctions.findByStatus(AuctionStatus.ACTIVE)) {
                advance(auction, now);
            }
        });
    }

    // ================================================================== nội bộ

    /**
     * Đưa phiên về đúng trạng thái theo giờ {@code now}: SCHEDULED → ACTIVE khi tới {@code startsAt},
     * ACTIVE → FINISHED khi tới {@code endsAt}. Mỗi bước là một transaction, phát sự kiện sau commit.
     * Phiên đã FINISHED không bao giờ bị chốt lại, nên mỗi phiên chỉ chốt kết quả một lần.
     */
    private Auction advance(Auction auction, long now) {
        executor.checkExecutorThread();
        Auction current = auction;
        if (current.status() == AuctionStatus.SCHEDULED && now >= current.startsAt()) {
            current = start(current, now);
        }
        if (current.status() == AuctionStatus.ACTIVE && now >= current.endsAt()) {
            current = finish(current, now);
        }
        return current;
    }

    private Auction start(Auction auction, long now) {
        Auction started = auction.withStatus(AuctionStatus.ACTIVE, null);
        transactions.inTransaction(() -> {
            auctions.update(started);
            return null;
        });
        announced.remove(auction.id());
        log.info("Mở phiên #{} \"{}\"", started.id(), started.title());
        rooms.broadcast(started.id(), Message.event(MessageType.AUCTION_STARTED, toView(started, now)));
        return started;
    }

    private Auction finish(Auction auction, long now) {
        Auction finished = auction.withStatus(AuctionStatus.FINISHED, auction.currentBidderId());
        transactions.inTransaction(() -> {
            auctions.update(finished);
            settlementQueue.enqueue(finished.id()); // cùng transaction: chốt phiên ⇔ có việc lập biên bản
            return null;
        });
        AuctionView view = toView(finished, now);
        log.info("Chốt phiên #{} \"{}\": {}", finished.id(), finished.title(), finished.winnerId() == null
                ? "chưa có người thắng" : view.winnerDisplayName() + " thắng với " + money(finished.currentPrice()));
        rooms.broadcast(finished.id(), Message.event(MessageType.AUCTION_FINISHED, view));
        notifyListener(l -> l.auctionFinished(view), "auctionFinished");
        return finished;
    }

    private Auction load(long auctionId) {
        return auctions.findById(auctionId).orElseThrow(() ->
                new ServiceException(ErrorCode.AUCTION_NOT_FOUND, "Không tìm thấy phiên #" + auctionId));
    }

    private static long minimumNextBid(Auction auction) {
        if (!auction.hasBids()) {
            return auction.startingPrice();
        }
        try {
            return Math.addExact(auction.currentPrice(), auction.minIncrement());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private AuctionDraft validateDraft(AuctionDraft draft, long now) {
        if (draft == null) {
            throw validation("Thiếu thông tin phiên");
        }
        String title = draft.title() == null ? "" : draft.title().strip();
        String description = draft.description() == null ? "" : draft.description().strip();
        if (title.isEmpty()) {
            throw validation("Tên sản phẩm không được để trống");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw validation("Tên sản phẩm tối đa " + MAX_TITLE_LENGTH + " ký tự");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw validation("Mô tả tối đa " + MAX_DESCRIPTION_LENGTH + " ký tự");
        }
        checkPrice(draft.startingPrice(), "Giá khởi điểm");
        checkPrice(draft.minIncrement(), "Bước giá");
        if (draft.endsAt() <= draft.startsAt()) {
            throw validation("Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (draft.endsAt() <= now) {
            throw validation("Giờ kết thúc phải ở tương lai");
        }
        return new AuctionDraft(title, description, draft.startingPrice(), draft.minIncrement(),
                draft.startsAt(), draft.endsAt());
    }

    private static void checkPrice(long value, String name) {
        if (value <= 0 || value > AuctionSettings.MAX_PRICE) {
            throw validation(name + " phải từ 1đ đến " + money(AuctionSettings.MAX_PRICE));
        }
    }

    private static void checkPageSize(int size, String name) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw validation(name + " phải từ 1 đến " + MAX_PAGE_SIZE);
        }
    }

    private static void requireUser(UserPrincipal user) {
        if (user == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED, "Bạn chưa đăng nhập");
        }
    }

    private static void requireAdmin(UserPrincipal user) {
        requireUser(user);
        if (!user.isAdmin()) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "Chỉ quản trị viên được thực hiện thao tác này");
        }
    }

    private static ServiceException validation(String message) {
        return new ServiceException(ErrorCode.VALIDATION_ERROR, message);
    }

    private void notifyListener(Consumer<AuctionLifecycleListener> call, String name) {
        try {
            call.accept(listener);
        } catch (RuntimeException e) {
            log.error("Lỗi trong {} (bỏ qua, không ảnh hưởng phiên)", name, e);
        }
    }

    private AuctionView toView(Auction a, long now) {
        return new AuctionView(a.id(), a.title(), a.description(), a.status(), a.startingPrice(), a.minIncrement(),
                a.currentPrice(), a.hasBids(), displayName(a.currentBidderId()), displayName(a.winnerId()),
                a.startsAt(), a.endsAt(), a.extensionCount(), a.version(), now);
    }

    private BidView toView(Bid b) {
        return new BidView(b.id(), b.auctionId(), displayName(b.userId()), b.amount(), b.acceptedAt());
    }

    private String displayName(Long userId) {
        if (userId == null) {
            return null;
        }
        return users.findById(userId).map(User::displayName).orElse("(tài khoản #" + userId + ")");
    }

    /** 300000 → "300.000đ". */
    static String money(long amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', '.') + "đ";
    }

    private record DedupKey(long userId, String requestId) {
    }

    private record BidOutcome(Bid bid, Auction auction) {
    }
}
