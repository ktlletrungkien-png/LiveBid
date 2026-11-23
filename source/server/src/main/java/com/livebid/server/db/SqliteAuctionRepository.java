package com.livebid.server.db;

import com.livebid.common.dto.AuctionSort;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.server.model.Auction;
import com.livebid.server.model.NewAuction;
import com.livebid.server.repo.AuctionRepository;
import com.livebid.server.repo.DataAccessException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bản SQLite của {@link AuctionRepository}, bảng {@code auctions}. Chỉ gọi từ luồng executor.
 *
 * <p>Hành vi phải giống hệt {@code InMemoryAuctionRepository}: thứ tự {@code starts_at, id} và
 * {@code insert} đặt giá khởi điểm, trạng thái SCHEDULED, version 1.
 */
public final class SqliteAuctionRepository implements AuctionRepository {

    private static final String COLUMNS = "id, title, description, starting_price, min_increment, current_price, "
            + "current_bidder_id, winner_id, starts_at, ends_at, extension_count, status, version, created_by, "
            + "created_at, reject_reason, reviewed_by, reviewed_at, image_id";
    private static final String ORDER = " ORDER BY starts_at, id";
    private static final String PENDING_ORDER = " ORDER BY created_at, id";
    private static final String CREATED_ORDER = " ORDER BY created_at DESC, id DESC";

    private final SqliteDatabase db;

    public SqliteAuctionRepository(SqliteDatabase db) {
        this.db = Objects.requireNonNull(db);
    }

    @Override
    public Optional<Auction> findById(long id) {
        List<Auction> rows = query("SELECT " + COLUMNS + " FROM auctions WHERE id = ?", id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Auction> findPage(AuctionStatus status, int offset, int limit) {
        if (status == null) {
            return query("SELECT " + COLUMNS + " FROM auctions" + ORDER + " LIMIT ? OFFSET ?", limit, offset);
        }
        return query("SELECT " + COLUMNS + " FROM auctions WHERE status = ?" + ORDER + " LIMIT ? OFFSET ?",
                status.name(), limit, offset);
    }

    @Override
    public List<Auction> findPublicPage(AuctionStatus status, AuctionSort sort, int offset, int limit) {
        if (status == AuctionStatus.PENDING_APPROVAL || status == AuctionStatus.REJECTED) {
            return List.of();
        }
        String order = publicOrder(sort);
        if (status == null) {
            return query("SELECT " + COLUMNS + " FROM auctions WHERE status IN ('SCHEDULED', 'ACTIVE', 'FINISHED')"
                    + order + " LIMIT ? OFFSET ?", limit, offset);
        }
        return query("SELECT " + COLUMNS + " FROM auctions WHERE status = ?" + order + " LIMIT ? OFFSET ?",
                status.name(), limit, offset);
    }

    /** Mệnh đề ORDER BY cố định cho từng {@link AuctionSort}; không ghép chuỗi từ client. */
    private static String publicOrder(AuctionSort sort) {
        return switch (sort == null ? AuctionSort.DEFAULT : sort) {
            case DEFAULT -> " ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'SCHEDULED' THEN 1 ELSE 2 END,"
                    + " CASE WHEN status = 'ACTIVE' THEN ends_at END,"
                    + " CASE WHEN status = 'SCHEDULED' THEN starts_at END,"
                    + " CASE WHEN status = 'FINISHED' THEN ends_at END DESC,"
                    + " id";
            case ENDS_SOONEST -> " ORDER BY ends_at, id";
            case ENDS_LATEST -> " ORDER BY ends_at DESC, id";
            case STARTS_SOONEST -> " ORDER BY starts_at, id";
            case STARTS_LATEST -> " ORDER BY starts_at DESC, id";
        };
    }

    @Override
    public List<Auction> findByStatus(AuctionStatus status) {
        return query("SELECT " + COLUMNS + " FROM auctions WHERE status = ?" + ORDER, status.name());
    }

    @Override
    public List<Auction> findPendingApproval() {
        return query("SELECT " + COLUMNS + " FROM auctions WHERE status = ?" + PENDING_ORDER,
                AuctionStatus.PENDING_APPROVAL.name());
    }

    @Override
    public List<Auction> findCreatedBy(long userId) {
        return query("SELECT " + COLUMNS + " FROM auctions WHERE created_by = ?" + CREATED_ORDER, userId);
    }

    @Override
    public int countPendingBy(long userId) {
        String sql = "SELECT COUNT(*) FROM auctions WHERE created_by = ? AND status = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, AuctionStatus.PENDING_APPROVAL.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi đếm phiên chờ duyệt", e);
        }
    }

    @Override
    public List<Auction> findParticipatedBy(long userId) {
        return query("SELECT " + COLUMNS + " FROM auctions WHERE id IN "
                + "(SELECT auction_id FROM bids WHERE user_id = ?)" + ORDER, userId);
    }

    @Override
    public List<Auction> findWonBy(long userId) {
        return query("SELECT " + COLUMNS + " FROM auctions WHERE winner_id = ?" + ORDER, userId);
    }

    @Override
    public Auction insert(NewAuction n) {
        String sql = "INSERT INTO auctions (title, description, starting_price, min_increment, current_price, "
                + "current_bidder_id, winner_id, starts_at, ends_at, extension_count, status, version, "
                + "created_by, created_at, reject_reason, reviewed_by, reviewed_at, image_id) "
                + "VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, 0, ?, 1, ?, ?, NULL, NULL, NULL, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, n.title());
            ps.setString(2, n.description());
            ps.setLong(3, n.startingPrice());
            ps.setLong(4, n.minIncrement());
            ps.setLong(5, n.startingPrice());
            ps.setLong(6, n.startsAt());
            ps.setLong(7, n.endsAt());
            ps.setString(8, n.initialStatus().name());
            ps.setLong(9, n.createdBy());
            ps.setLong(10, n.createdAt());
            setNullableString(ps, 11, n.imageId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Auction(keys.getLong(1), n.title(), n.description(), n.startingPrice(),
                        n.minIncrement(), n.startingPrice(), null, null, n.startsAt(), n.endsAt(), 0,
                        n.initialStatus(), 1, n.createdBy(), n.createdAt(), null, null, null, n.imageId());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Không thêm được phiên đấu giá", e);
        }
    }

    @Override
    public void update(Auction a) {
        String sql = "UPDATE auctions SET title = ?, description = ?, starting_price = ?, min_increment = ?, "
                + "current_price = ?, current_bidder_id = ?, winner_id = ?, starts_at = ?, ends_at = ?, "
                + "extension_count = ?, status = ?, version = ?, created_by = ?, created_at = ?, "
                + "reject_reason = ?, reviewed_by = ?, reviewed_at = ?, image_id = ? WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, a.title());
            ps.setString(2, a.description());
            ps.setLong(3, a.startingPrice());
            ps.setLong(4, a.minIncrement());
            ps.setLong(5, a.currentPrice());
            setNullableLong(ps, 6, a.currentBidderId());
            setNullableLong(ps, 7, a.winnerId());
            ps.setLong(8, a.startsAt());
            ps.setLong(9, a.endsAt());
            ps.setInt(10, a.extensionCount());
            ps.setString(11, a.status().name());
            ps.setLong(12, a.version());
            ps.setLong(13, a.createdBy());
            ps.setLong(14, a.createdAt());
            setNullableString(ps, 15, a.rejectReason());
            setNullableLong(ps, 16, a.reviewedBy());
            setNullableLong(ps, 17, a.reviewedAt());
            setNullableString(ps, 18, a.imageId());
            ps.setLong(19, a.id());
            if (ps.executeUpdate() == 0) {
                throw new DataAccessException("Không có phiên " + a.id(), null);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Không cập nhật được phiên " + a.id(), e);
        }
    }

    @Override
    public boolean deleteIfPending(long auctionId) {
        String sql = "DELETE FROM auctions WHERE id = ? AND status = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            ps.setString(2, AuctionStatus.PENDING_APPROVAL.name());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Không hủy được phiên " + auctionId, e);
        }
    }

    private List<Auction> query(String sql, Object... params) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Auction> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi đọc bảng auctions", e);
        }
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setLong(index, value);
        }
    }

    private static Auction map(ResultSet rs) throws SQLException {
        Long currentBidderId = nullableLong(rs, "current_bidder_id");
        Long winnerId = nullableLong(rs, "winner_id");
        Long reviewedBy = nullableLong(rs, "reviewed_by");
        Long reviewedAt = nullableLong(rs, "reviewed_at");
        return new Auction(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getLong("starting_price"),
                rs.getLong("min_increment"),
                rs.getLong("current_price"),
                currentBidderId,
                winnerId,
                rs.getLong("starts_at"),
                rs.getLong("ends_at"),
                rs.getInt("extension_count"),
                AuctionStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                rs.getLong("created_by"),
                rs.getLong("created_at"),
                rs.getString("reject_reason"),
                reviewedBy,
                reviewedAt,
                rs.getString("image_id"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
