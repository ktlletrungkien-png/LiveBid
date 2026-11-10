package com.livebid.tests.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.Role;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.server.core.ServiceException;
import com.livebid.server.db.SqliteAuctionRepository;
import com.livebid.server.db.SqliteBidRepository;
import com.livebid.server.db.SqliteDatabase;
import com.livebid.server.db.SqliteSettlementQueueRepository;
import com.livebid.server.db.SqliteTransactionManager;
import com.livebid.server.db.SqliteUserRepository;
import com.livebid.server.model.Auction;
import com.livebid.server.model.Bid;
import com.livebid.server.model.NewAuction;
import com.livebid.server.model.NewUser;
import com.livebid.server.model.SettlementTask;
import com.livebid.server.model.User;
import com.livebid.server.repo.DataAccessException;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm tra các bản SQLite của repository. Chạy đơn luồng trên test, đúng như quy ước (repository chỉ
 * được gọi từ luồng executor, mà test này là luồng duy nhất).
 */
class SqliteRepositoryTest {

    @TempDir
    Path dir;

    private SqliteDatabase db;
    private SqliteUserRepository users;
    private SqliteAuctionRepository auctions;
    private SqliteBidRepository bids;
    private SqliteSettlementQueueRepository queue;
    private SqliteTransactionManager tx;

    @BeforeEach
    void setUp() {
        db = SqliteDatabase.open(dir.resolve("test.db"));
        users = new SqliteUserRepository(db);
        auctions = new SqliteAuctionRepository(db);
        bids = new SqliteBidRepository(db);
        queue = new SqliteSettlementQueueRepository(db);
        tx = new SqliteTransactionManager(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    // 1. Mở CSDL

    @Test
    void openTwiceDoesNotFailOrRerunMigration() throws SQLException {
        db.close();
        db = SqliteDatabase.open(dir.resolve("test.db"));

        assertEquals(1, scalar("SELECT COUNT(*) FROM schema_version"));
        assertEquals(1, scalar("SELECT version FROM schema_version"));
        for (String table : List.of("users", "auctions", "bids", "settlement_queue")) {
            assertEquals(1, scalar("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'"),
                    "thiếu bảng " + table);
        }
    }

    // 2. User

    @Test
    void userInsertStoresLowercaseAndFindsCaseInsensitively() {
        User saved = users.insert(newUser("ABC", Role.USER));

        assertEquals("abc", saved.username());
        assertEquals("abc", users.findByUsername("ABC").orElseThrow().username());
        assertEquals(saved.id(), users.findById(saved.id()).orElseThrow().id());
    }

    @Test
    void duplicateUsernameThrowsDataAccessException() {
        users.insert(newUser("abc", Role.USER));

        assertThrows(DataAccessException.class, () -> users.insert(newUser("ABC", Role.USER)));
    }

    // 3. Phiên đấu giá

    @Test
    void auctionInsertUsesDefaults() {
        long adminId = users.insert(newUser("admin", Role.ADMIN)).id();
        Auction a = auctions.insert(newAuction("Bàn phím", 1000, 1, 2000, adminId));

        Auction reloaded = auctions.findById(a.id()).orElseThrow();
        assertEquals(1000, reloaded.currentPrice());
        assertEquals(AuctionStatus.SCHEDULED, reloaded.status());
        assertEquals(1, reloaded.version());
        assertEquals(0, reloaded.extensionCount());
        assertNull(reloaded.currentBidderId());
        assertNull(reloaded.winnerId());
    }

    @Test
    void auctionUpdateWithBidPersistsPriceLeaderAndVersion() {
        long adminId = users.insert(newUser("admin", Role.ADMIN)).id();
        long userId = users.insert(newUser("nguoi", Role.USER)).id();
        Auction a = auctions.insert(newAuction("Đồng hồ", 1000, 10, 2000, adminId));

        auctions.update(a.withBid(1500, userId));

        Auction reloaded = auctions.findById(a.id()).orElseThrow();
        assertEquals(1500, reloaded.currentPrice());
        assertEquals(userId, reloaded.currentBidderId());
        assertEquals(2, reloaded.version());
    }

    @Test
    void auctionUpdateOfMissingRowThrows() {
        long adminId = users.insert(newUser("admin", Role.ADMIN)).id();
        Auction a = auctions.insert(newAuction("Ghế", 1000, 10, 2000, adminId));
        Auction ghost = new Auction(999, a.title(), a.description(), a.startingPrice(), a.minIncrement(),
                a.currentPrice(), null, null, a.startsAt(), a.endsAt(), 0, a.status(), 1, a.createdBy(), a.createdAt());

        assertThrows(DataAccessException.class, () -> auctions.update(ghost));
    }

    @Test
    void findPageFiltersOrdersAndPaginates() {
        long adminId = users.insert(newUser("admin", Role.ADMIN)).id();
        Auction late = auctions.insert(newAuction("C", 1000, 10, 3000, adminId));
        Auction early = auctions.insert(newAuction("A", 1000, 10, 1000, adminId));
        Auction middle = auctions.insert(newAuction("B", 1000, 10, 2000, adminId));
        auctions.update(middle.withStatus(AuctionStatus.ACTIVE, null));

        // Thứ tự starts_at tăng dần, không lọc.
        assertEquals(List.of(early.id(), middle.id(), late.id()),
                ids(auctions.findPage(null, 0, 10)));
        // Lọc theo trạng thái.
        assertEquals(List.of(middle.id()), ids(auctions.findPage(AuctionStatus.ACTIVE, 0, 10)));
        // Phân trang: bỏ 1, lấy 1.
        assertEquals(List.of(middle.id()), ids(auctions.findPage(null, 1, 1)));
        assertEquals(List.of(late.id()), ids(auctions.findPage(null, 2, 5)));
        // findByStatus cùng thứ tự.
        assertEquals(List.of(early.id(), late.id()), ids(auctions.findByStatus(AuctionStatus.SCHEDULED)));
    }

    // 4. Bid

    @Test
    void bidHistoryNewestFirstAndPaginatesWithBeforeId() {
        long adminId = users.insert(newUser("admin", Role.ADMIN)).id();
        long userId = users.insert(newUser("nguoi", Role.USER)).id();
        long auctionId = auctions.insert(newAuction("Tranh", 1000, 10, 2000, adminId)).id();
        Bid b1 = bids.insert(auctionId, userId, 1100, 1L, "r1");
        Bid b2 = bids.insert(auctionId, userId, 1200, 2L, "r2");
        Bid b3 = bids.insert(auctionId, userId, 1300, 3L, "r3");

        assertEquals(List.of(b3.id(), b2.id(), b1.id()),
                bids.findHistory(auctionId, null, 10).stream().map(Bid::id).toList());
        assertEquals(List.of(b2.id(), b1.id()),
                bids.findHistory(auctionId, b3.id(), 10).stream().map(Bid::id).toList());
        assertEquals(List.of(b3.id()), bids.findHistory(auctionId, null, 1).stream().map(Bid::id).toList());
        assertEquals(3, bids.countByAuction(auctionId));
        assertEquals(0, bids.countByAuction(auctionId + 100));
    }

    // 5. Phiên đã tham gia / đã thắng

    @Test
    void findParticipatedByAndFindWonBy() {
        long adminId = users.insert(newUser("admin", Role.ADMIN)).id();
        long alice = users.insert(newUser("alice", Role.USER)).id();
        long bob = users.insert(newUser("bob", Role.USER)).id();
        Auction a1 = auctions.insert(newAuction("A1", 1000, 10, 1000, adminId));
        Auction a2 = auctions.insert(newAuction("A2", 1000, 10, 2000, adminId));
        bids.insert(a1.id(), alice, 1100, 1L, "x1");
        bids.insert(a2.id(), bob, 1100, 2L, "x2");
        bids.insert(a2.id(), alice, 1200, 3L, "x3");

        assertEquals(List.of(a1.id(), a2.id()), ids(auctions.findParticipatedBy(alice)));
        assertEquals(List.of(a2.id()), ids(auctions.findParticipatedBy(bob)));

        auctions.update(a2.withStatus(AuctionStatus.FINISHED, alice));
        assertEquals(List.of(a2.id()), ids(auctions.findWonBy(alice)));
        assertTrue(auctions.findWonBy(bob).isEmpty());
    }

    // 6. Hàng đợi quyết toán

    @Test
    void settlementQueueEnqueueOnceFailureCountAndDone() {
        long adminId = users.insert(newUser("admin", Role.ADMIN)).id();
        long auctionId = auctions.insert(newAuction("Xe", 1000, 10, 2000, adminId)).id();

        queue.enqueue(auctionId);
        queue.enqueue(auctionId);
        List<SettlementTask> pending = queue.findPending();
        assertEquals(1, pending.size());
        long taskId = pending.get(0).id();

        queue.recordFailure(taskId, 5000L);
        SettlementTask failed = queue.findPending().get(0);
        assertEquals(1, failed.attempts());
        assertEquals(Long.valueOf(5000L), failed.lastAttemptAt());

        queue.markDone(taskId);
        assertTrue(queue.findPending().isEmpty());
    }

    // 7. Transaction

    @Test
    void transactionRollsBackAndRethrowsSameException() {
        ServiceException original = new ServiceException(ErrorCode.VALIDATION_ERROR, "lỗi thử nghiệm");

        ServiceException thrown = assertThrows(ServiceException.class, () -> tx.inTransaction(() -> {
            users.insert(newUser("tam", Role.USER));
            throw original;
        }));

        assertTrue(thrown == original, "phải ném đúng exception ban đầu");
        assertFalse(users.findByUsername("tam").isPresent(), "user không được lưu sau rollback");
    }

    @Test
    void transactionCommitsOnSuccess() {
        tx.inTransaction(() -> users.insert(newUser("thanhcong", Role.USER)));

        assertTrue(users.findByUsername("thanhcong").isPresent());
    }

    // 8. Khóa ngoại

    @Test
    void bidForMissingAuctionViolatesForeignKey() {
        long userId = users.insert(newUser("nguoi", Role.USER)).id();

        assertThrows(DataAccessException.class, () -> bids.insert(424242L, userId, 1000, 1L, "fk"));
    }

    // Tiện ích

    private static NewUser newUser(String username, Role role) {
        return new NewUser(username, "Tên " + username, "hash", "salt", 1000, role, 1L);
    }

    private static NewAuction newAuction(String title, long startingPrice, long minIncrement, long startsAt,
                                         long createdBy) {
        return new NewAuction(title, "", startingPrice, minIncrement, startsAt, startsAt + 300_000L, createdBy, 1L);
    }

    private static List<Long> ids(List<Auction> auctions) {
        return auctions.stream().map(Auction::id).toList();
    }

    private long scalar(String sql) throws SQLException {
        try (Statement st = db.connection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
