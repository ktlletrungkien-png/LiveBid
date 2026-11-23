package com.livebid.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.MyAuctionFilter;
import com.livebid.common.dto.Role;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.Message;
import com.livebid.server.core.AuctionLifecycleListener;
import com.livebid.server.core.AuctionServiceImpl;
import com.livebid.server.core.AuctionSettings;
import com.livebid.server.core.BusinessExecutor;
import com.livebid.server.core.ClientChannel;
import com.livebid.server.core.ServiceException;
import com.livebid.server.core.UserPrincipal;
import com.livebid.server.model.NewUser;
import com.livebid.server.model.User;
import com.livebid.server.repo.memory.InMemoryAuctionRepository;
import com.livebid.server.repo.memory.InMemoryBidRepository;
import com.livebid.server.repo.memory.InMemorySettlementQueueRepository;
import com.livebid.server.repo.memory.InMemoryTransactionManager;
import com.livebid.server.repo.memory.InMemoryUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kiểm thử nghiệp vụ USER đăng phiên — ADMIN duyệt/từ chối — chủ phiên sửa/hủy và chặn tự đặt giá.
 */
class AuctionApprovalServiceTest {

    private static final long T0 = 1_793_080_800_000L;
    private static final long LEAD_30M = 30 * 60_000L;

    private final MutableClock clock = new MutableClock(T0);
    private final BusinessExecutor executor = new BusinessExecutor();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryBidRepository bids = new InMemoryBidRepository();
    private final InMemoryAuctionRepository auctions = new InMemoryAuctionRepository(bids);
    private final AuctionServiceImpl service = new AuctionServiceImpl(executor, auctions, bids, users,
            new InMemorySettlementQueueRepository(), new InMemoryTransactionManager(), clock,
            AuctionSettings.defaults());

    private UserPrincipal admin;
    private UserPrincipal alice;
    private UserPrincipal bob;
    private final ClientChannel aliceChannel = new SilentChannel("alice");
    private final ClientChannel bobChannel = new SilentChannel("bob");

    @BeforeEach
    void setUp() {
        admin = insertUser("admin", "Ban quản trị", Role.ADMIN);
        alice = insertUser("alice", "Nguyễn Thị An", Role.USER);
        bob = insertUser("bob", "Trần Văn Bình", Role.USER);
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void fullApprovalLifecycleAndOwnAuctionRule() {
        long startsAt = T0 + LEAD_30M;
        long endsAt = startsAt + 3_600_000L;
        AuctionView pending = service.createAuction(alice,
                new AuctionDraft("Máy ảnh", "Đẹp", 1_000_000, 100_000, startsAt, endsAt)).join();
        assertEquals(AuctionStatus.PENDING_APPROVAL, pending.status());
        assertEquals(alice.userId(), pending.ownerId());
        assertEquals("Nguyễn Thị An", pending.ownerDisplayName());
        assertEquals(Role.USER, pending.ownerRole());

        // Không lộ cho người lạ và danh sách công khai
        assertTrue(service.listAuctions(null, null, 1, 20).join().items().isEmpty());
        assertTrue(service.listAuctions(AuctionStatus.PENDING_APPROVAL, null, 1, 20).join().items().isEmpty());
        assertEquals(ErrorCode.AUCTION_NOT_FOUND, rejection(service.getAuction(bob, pending.auctionId())));
        assertEquals(ErrorCode.AUCTION_NOT_FOUND, rejection(service.joinAuction(bob, bobChannel, pending.auctionId())));
        assertEquals(ErrorCode.VALIDATION_ERROR, rejection(service.joinAuction(alice, aliceChannel, pending.auctionId())));

        // ADMIN duyệt -> SCHEDULED, lộ công khai
        AuctionView approved = service.approveAuction(admin, pending.auctionId()).join();
        assertEquals(AuctionStatus.SCHEDULED, approved.status());
        assertEquals(pending.version() + 1, approved.version());
        assertEquals(1, service.listAuctions(null, null, 1, 20).join().items().size());

        // Đến giờ mở: Bob đặt giá được, Alice (chủ phiên) bị OWN_AUCTION
        clock.set(startsAt);
        service.joinAuction(bob, bobChannel, pending.auctionId()).join();
        service.joinAuction(alice, aliceChannel, pending.auctionId()).join();

        assertEquals(1_000_000, service.placeBid(bob, bobChannel, pending.auctionId(), 1_000_000, "b1")
                .join().acceptedAmount());
        assertEquals(ErrorCode.OWN_AUCTION, rejection(service.placeBid(alice, aliceChannel,
                pending.auctionId(), 1_100_000, "a1")));
    }

    @Test
    void maxPendingLimitAndCancelOrRejectFlow() {
        long startsAt = T0 + LEAD_30M + 60_000L;
        long endsAt = startsAt + 3_600_000L;
        for (int i = 1; i <= 5; i++) {
            service.createAuction(alice, new AuctionDraft("Phiên " + i, "", 100_000, 10_000, startsAt, endsAt)).join();
        }
        assertEquals(ErrorCode.TOO_MANY_PENDING, rejection(service.createAuction(alice,
                new AuctionDraft("Phiên 6", "", 100_000, 10_000, startsAt, endsAt))));

        List<AuctionView> mine = service.getMyAuctions(alice, MyAuctionFilter.CREATED).join();
        assertEquals(5, mine.size());
        long firstId = mine.get(mine.size() - 1).auctionId();

        // Từ chối bắt buộc lý do hợp lệ
        assertEquals(ErrorCode.VALIDATION_ERROR, rejection(service.rejectAuction(admin, firstId, "   ")));
        AuctionView rejected = service.rejectAuction(admin, firstId, "Không rõ nguồn gốc").join();
        assertEquals(AuctionStatus.REJECTED, rejected.status());
        assertEquals("Không rõ nguồn gốc", rejected.rejectReason());
        assertEquals(ErrorCode.AUCTION_NOT_FOUND, rejection(service.getAuction(bob, firstId)));

        // Còn 4 phiên chờ -> đăng thêm 1 phiên mới rồi tự hủy
        AuctionView extra = service.createAuction(alice,
                new AuctionDraft("Phiên thay", "", 100_000, 10_000, startsAt, endsAt)).join();
        service.cancelAuction(alice, extra.auctionId()).join();
        assertEquals(ErrorCode.AUCTION_NOT_FOUND, rejection(service.getAuction(alice, extra.auctionId())));
    }

    @Test
    void approvePastStartTimeFailsAndSchedulerIgnoresPendingOrRejected() {
        long startsAt = T0 + LEAD_30M;
        long endsAt = startsAt + 3_600_000L;
        long pendingId = service.createAuction(alice,
                new AuctionDraft("Chờ quá giờ", "", 100_000, 10_000, startsAt, endsAt)).join().auctionId();

        List<Long> announcedIds = new CopyOnWriteArrayList<>();
        service.setLifecycleListener(new AuctionLifecycleListener() {
            @Override
            public void auctionStartingSoon(AuctionView auction) {
                announcedIds.add(auction.auctionId());
            }

            @Override
            public void auctionFinished(AuctionView auction) {
            }
        });

        // Sát giờ mở nhưng vẫn PENDING_APPROVAL -> scheduler không phát multicast và không tự mở
        clock.set(startsAt - 60_000L);
        service.processDueAuctions().join();
        assertTrue(announcedIds.isEmpty());

        clock.set(startsAt);
        service.processDueAuctions().join();
        assertEquals(AuctionStatus.PENDING_APPROVAL, auctions.findById(pendingId).orElseThrow().status());

        // Duyệt khi startsAt <= now -> VALIDATION_ERROR, vẫn giữ PENDING_APPROVAL
        assertEquals(ErrorCode.VALIDATION_ERROR, rejection(service.approveAuction(admin, pendingId)));
        assertEquals(AuctionStatus.PENDING_APPROVAL, auctions.findById(pendingId).orElseThrow().status());
        assertFalse(service.getPendingAuctions(admin).join().isEmpty());
    }

    private UserPrincipal insertUser(String username, String displayName, Role role) {
        User u = users.insert(new NewUser(username, displayName, "h", "s", 1000, role, T0));
        return new UserPrincipal(u.id(), u.username(), u.displayName(), u.role());
    }

    private static ErrorCode rejection(CompletableFuture<?> future) {
        CompletionException e = assertThrows(CompletionException.class, future::join);
        return ((ServiceException) e.getCause()).code();
    }

    private static final class MutableClock extends Clock {
        private long now;

        MutableClock(long now) {
            this.now = now;
        }

        void set(long now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now);
        }
    }

    private static final class SilentChannel implements ClientChannel {
        private final String id;
        private UserPrincipal principal;

        SilentChannel(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Transport transport() {
            return Transport.TCP;
        }

        @Override
        public String remoteAddress() {
            return "127.0.0.1";
        }

        @Override
        public void send(Message message) {
        }

        @Override
        public void close(String reason) {
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public UserPrincipal principal() {
            return principal;
        }

        @Override
        public void setPrincipal(UserPrincipal principal) {
            this.principal = principal;
        }
    }
}
