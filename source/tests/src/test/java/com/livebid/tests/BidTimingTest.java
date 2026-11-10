package com.livebid.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.Role;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.Message;
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
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T07: bid tại hoặc sau {@code endsAt} bị từ chối, <b>kể cả khi scheduler chưa chạy</b>.
 *
 * <p>Test không tạo {@code AuctionScheduler}: phiên chỉ được mở/chốt nhờ chính lượt bid kiểm tra giờ server.
 * Giờ server là một {@link Clock} điều khiển được, nên không phải chờ thật.
 */
class BidTimingTest {

    private static final long STARTS_AT = 1_793_080_800_000L;
    private static final long ENDS_AT = STARTS_AT + 5 * 60_000;

    private final MutableClock clock = new MutableClock(STARTS_AT - 60_000);
    private final BusinessExecutor executor = new BusinessExecutor();
    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryBidRepository bids = new InMemoryBidRepository();
    private final InMemoryAuctionRepository auctions = new InMemoryAuctionRepository(bids);
    // Tắt anti-sniping để mốc endsAt cố định trong test này.
    private final AuctionServiceImpl service = new AuctionServiceImpl(executor, auctions, bids, users,
            new InMemorySettlementQueueRepository(), new InMemoryTransactionManager(), clock,
            new AuctionSettings(0, 0, 0, 5 * 60_000, 20));

    private UserPrincipal alice;
    private UserPrincipal bob;
    private final ClientChannel aliceChannel = new SilentChannel("alice");
    private final ClientChannel bobChannel = new SilentChannel("bob");
    private long auctionId;

    @BeforeEach
    void setUp() {
        UserPrincipal admin = user("admin", Role.ADMIN);
        alice = user("alice", Role.USER);
        bob = user("bob", Role.USER);
        auctionId = service.createAuction(admin,
                new AuctionDraft("Bàn phím cơ", "", 200_000, 20_000, STARTS_AT, ENDS_AT)).join().auctionId();
        service.joinAuction(alice, aliceChannel, auctionId).join();
        service.joinAuction(bob, bobChannel, auctionId).join();
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void bidIsAcceptedOnlyWhileStartsAtLeNowLtEndsAt() {
        clock.set(STARTS_AT - 1);
        assertEquals(ErrorCode.AUCTION_NOT_STARTED, rejection(bid(alice, aliceChannel, 200_000)));

        clock.set(STARTS_AT); // đúng giờ mở: được nhận, phiên tự chuyển ACTIVE dù chưa có scheduler
        assertNotNull(bid(alice, aliceChannel, 200_000).join());
        assertEquals(AuctionStatus.ACTIVE, auctions.findById(auctionId).orElseThrow().status());

        clock.set(ENDS_AT - 1); // mili giây cuối cùng: vẫn được nhận
        assertNotNull(bid(bob, bobChannel, 220_000).join());
        assertEquals(AuctionStatus.ACTIVE, auctions.findById(auctionId).orElseThrow().status(),
                "trước lượt bid kế tiếp, phiên vẫn ACTIVE trong DB (scheduler không chạy)");

        clock.set(ENDS_AT); // đúng endsAt: từ chối
        assertEquals(ErrorCode.AUCTION_FINISHED, rejection(bid(alice, aliceChannel, 240_000)));

        clock.set(ENDS_AT + 10_000); // sau endsAt: vẫn từ chối
        assertEquals(ErrorCode.AUCTION_FINISHED, rejection(bid(alice, aliceChannel, 1_000_000)));

        AuctionView result = service.getAuction(auctionId).join();
        assertEquals(AuctionStatus.FINISHED, result.status());
        assertEquals(220_000, result.currentPrice(), "bid bị từ chối không làm đổi giá");
        assertEquals("Người bob", result.winnerDisplayName(), "người thắng là bid hợp lệ cuối cùng");
        assertEquals(2, bids.countByAuction(auctionId));
    }

    private CompletableFuture<?> bid(UserPrincipal user, ClientChannel channel, long amount) {
        return service.placeBid(user, channel, auctionId, amount, java.util.UUID.randomUUID().toString());
    }

    private static ErrorCode rejection(CompletableFuture<?> result) {
        CompletionException e = assertThrows(CompletionException.class, result::join);
        return assertInstanceOf(ServiceException.class, e.getCause()).code();
    }

    private UserPrincipal user(String username, Role role) {
        User u = users.insert(new NewUser(username, "Người " + username, "hash", "salt", 1, role, 0));
        return new UserPrincipal(u.id(), u.username(), u.displayName(), u.role());
    }

    /** Đồng hồ chỉnh tay được. */
    static final class MutableClock extends Clock {

        private volatile long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        void set(long value) {
            millis = value;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    /** Kênh giả bỏ qua mọi thông điệp. */
    static final class SilentChannel implements ClientChannel {

        private final String id;

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
            return "test";
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
    }
}
