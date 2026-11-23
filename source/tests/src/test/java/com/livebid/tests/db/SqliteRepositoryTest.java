package com.livebid.tests.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.AuctionSort;
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
import com.livebid.server.repo.AuctionRepository;
import com.livebid.server.repo.DataAccessException;
import com.livebid.server.repo.memory.InMemoryAuctionRepository;
import com.livebid.server.repo.memory.InMemoryBidRepository;
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
        assertEquals(3, scalar("SELECT version FROM schema_version"));
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

    // 9. Migration V1 -> V2 & hàm mới cho duyệt phiên

    @Test
    void migratesV1DatabaseWithExistingDataToV2() throws Exception {
        Path v1Path = dir.resolve("v1_legacy.db");
        try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + v1Path);
             Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("CREATE TABLE schema_version (version INTEGER NOT NULL)");
            for (String sql : SqliteDatabase.readStatements("db/V1__init.sql")) {
                st.execute(sql);
            }
            st.execute("INSERT INTO schema_version (version) VALUES (1)");
            st.execute("INSERT INTO users (id, username, display_name, password_hash, password_salt, "
                    + "password_iterations, role, created_at) VALUES (1, 'admin', 'Admin', 'h', 's', 1000, 'ADMIN', 1)");
            st.execute("INSERT INTO users (id, username, display_name, password_hash, password_salt, "
                    + "password_iterations, role, created_at) VALUES (2, 'an', 'An', 'h', 's', 1000, 'USER', 1)");
            st.execute("INSERT INTO auctions (id, title, description, starting_price, min_increment, current_price, "
                    + "current_bidder_id, winner_id, starts_at, ends_at, extension_count, status, version, "
                    + "created_by, created_at) VALUES (10, 'Phiên cũ', 'Mô tả', 1000, 100, 1100, 2, 2, "
                    + "100, 200, 0, 'FINISHED', 2, 1, 50)");
            st.execute("INSERT INTO bids (id, auction_id, user_id, amount, accepted_at, request_id) "
                    + "VALUES (20, 10, 2, 1100, 150, 'req-1')");
            st.execute("INSERT INTO settlement_queue (id, auction_id, status, attempts) VALUES (30, 10, 'DONE', 1)");
        }

        try (SqliteDatabase migrated = SqliteDatabase.open(v1Path)) {
            var repo = new SqliteAuctionRepository(migrated);
            Auction old = repo.findById(10).orElseThrow();
            assertEquals("Phiên cũ", old.title());
            assertEquals(AuctionStatus.FINISHED, old.status());
            assertNull(old.rejectReason());
            assertNull(old.imageId());

            Auction pending = repo.insert(new NewAuction("Phiên mới", "", 2000, 100, 300, 400, 2, 60,
                    AuctionStatus.PENDING_APPROVAL));
            assertEquals(AuctionStatus.PENDING_APPROVAL, repo.findById(pending.id()).orElseThrow().status());

            try (Statement st = migrated.connection().createStatement();
                 ResultSet rs = st.executeQuery("SELECT version FROM schema_version")) {
                rs.next();
                assertEquals(3, rs.getInt(1));
            }
        }

        // Mở lần hai không chạy lại V2
        try (SqliteDatabase reopened = SqliteDatabase.open(v1Path);
             Statement st = reopened.connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*), MAX(version) FROM schema_version")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
            assertEquals(3, rs.getInt(2));
        }
    }

    @Test
    void approvalQueriesAndDeleteIfPendingBehaveConsistently() {
        long adminId = users.insert(newUser("admin", Role.ADMIN)).id();
        long aliceId = users.insert(newUser("alice", Role.USER)).id();

        Auction p1 = auctions.insert(new NewAuction("Chờ 1", "", 1000, 100, 2000, 3000, aliceId, 10,
                AuctionStatus.PENDING_APPROVAL));
        Auction p2 = auctions.insert(new NewAuction("Chờ 2", "", 1000, 100, 2000, 3000, aliceId, 20,
                AuctionStatus.PENDING_APPROVAL));
        Auction pub = auctions.insert(new NewAuction("Công khai", "", 1000, 100, 1000, 3000, adminId, 30,
                AuctionStatus.SCHEDULED));

        assertEquals(List.of(pub.id()), ids(auctions.findPublicPage(null, null, 0, 10)));
        assertTrue(auctions.findPublicPage(AuctionStatus.PENDING_APPROVAL, null, 0, 10).isEmpty());
        assertEquals(List.of(p1.id(), p2.id()), ids(auctions.findPendingApproval()));
        assertEquals(List.of(p2.id(), p1.id()), ids(auctions.findCreatedBy(aliceId)));
        assertEquals(2, auctions.countPendingBy(aliceId));

        assertTrue(auctions.deleteIfPending(p1.id()));
        assertFalse(auctions.deleteIfPending(p1.id()));
        assertFalse(auctions.deleteIfPending(pub.id()));
        assertEquals(1, auctions.countPendingBy(aliceId));
    }

    // Tiện ích

    @Test
    void publicPageSortsTheSameInSqliteAndMemory() {
        long ownerId = users.insert(newUser("owner", Role.USER)).id();
        AuctionRepository memory = new InMemoryAuctionRepository(new InMemoryBidRepository());
        for (AuctionRepository repo : List.of(auctions, memory)) {
            // id 1..7 theo thứ tự chèn
            insert(repo, ownerId, 100, 900, AuctionStatus.FINISHED);   // 1: kết thúc sớm
            insert(repo, ownerId, 200, 950, AuctionStatus.FINISHED);   // 2: vừa kết thúc
            insert(repo, ownerId, 300, 5000, AuctionStatus.ACTIVE);    // 3: còn lâu
            insert(repo, ownerId, 400, 2000, AuctionStatus.ACTIVE);    // 4: sắp hết giờ
            insert(repo, ownerId, 3000, 9000, AuctionStatus.SCHEDULED); // 5: mở muộn
            insert(repo, ownerId, 1500, 8000, AuctionStatus.SCHEDULED); // 6: mở sớm
            insert(repo, ownerId, 1500, 8000, AuctionStatus.PENDING_APPROVAL); // 7: không công khai

            assertEquals(List.of(4L, 3L, 6L, 5L, 2L, 1L), ids(repo.findPublicPage(null, null, 0, 10)));
            assertEquals(List.of(4L, 3L, 6L, 5L, 2L, 1L),
                    ids(repo.findPublicPage(null, AuctionSort.DEFAULT, 0, 10)));
            assertEquals(List.of(1L, 2L, 4L, 3L, 6L, 5L),
                    ids(repo.findPublicPage(null, AuctionSort.ENDS_SOONEST, 0, 10)));
            assertEquals(List.of(5L, 6L, 3L, 4L, 2L, 1L),
                    ids(repo.findPublicPage(null, AuctionSort.ENDS_LATEST, 0, 10)));
            assertEquals(List.of(1L, 2L, 3L, 4L, 6L, 5L),
                    ids(repo.findPublicPage(null, AuctionSort.STARTS_SOONEST, 0, 10)));
            assertEquals(List.of(5L, 6L, 4L, 3L, 2L, 1L),
                    ids(repo.findPublicPage(null, AuctionSort.STARTS_LATEST, 0, 10)));
            assertEquals(List.of(2L, 1L), ids(repo.findPublicPage(AuctionStatus.FINISHED, null, 0, 10)));
            assertEquals(List.of(3L, 6L), ids(repo.findPublicPage(null, AuctionSort.DEFAULT, 1, 2)));
        }
    }

    @Test
    void imageIdIsStoredAndUpdated() {
        long ownerId = users.insert(newUser("owner", Role.USER)).id();
        String imageId = "a".repeat(32) + ".jpg";
        Auction created = auctions.insert(new NewAuction("Có ảnh", "", 1000, 100, 2000, 3000, ownerId, 1,
                AuctionStatus.SCHEDULED, imageId));
        assertEquals(imageId, auctions.findById(created.id()).orElseThrow().imageId());

        Auction withoutImage = created.withDetails("Có ảnh", "", 1000, 100, 2000, 3000, null);
        auctions.update(withoutImage);
        assertNull(auctions.findById(created.id()).orElseThrow().imageId());

        // Các thay đổi khác (mở phiên) không làm mất ảnh
        Auction again = auctions.insert(new NewAuction("Có ảnh 2", "", 1000, 100, 2000, 3000, ownerId, 1,
                AuctionStatus.SCHEDULED, imageId));
        auctions.update(again.withStatus(AuctionStatus.ACTIVE, null));
        assertEquals(imageId, auctions.findById(again.id()).orElseThrow().imageId());
    }

    private static void insert(AuctionRepository repo, long ownerId, long startsAt, long endsAt,
            AuctionStatus status) {
        repo.insert(new NewAuction("Phiên", "", 1000, 100, startsAt, endsAt, ownerId, 1, status));
    }

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
