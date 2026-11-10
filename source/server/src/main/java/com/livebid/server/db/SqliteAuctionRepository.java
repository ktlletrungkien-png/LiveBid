package com.livebid.server.db;

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
            + "created_at";
    private static final String ORDER = " ORDER BY starts_at, id";

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
    public List<Auction> findByStatus(AuctionStatus status) {
        return query("SELECT " + COLUMNS + " FROM auctions WHERE status = ?" + ORDER, status.name());
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
                + "created_by, created_at) VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, 0, ?, 1, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, n.title());
            ps.setString(2, n.description());
            ps.setLong(3, n.startingPrice());
            ps.setLong(4, n.minIncrement());
            ps.setLong(5, n.startingPrice());
            ps.setLong(6, n.startsAt());
            ps.setLong(7, n.endsAt());
            ps.setString(8, AuctionStatus.SCHEDULED.name());
            ps.setLong(9, n.createdBy());
            ps.setLong(10, n.createdAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Auction(keys.getLong(1), n.title(), n.description(), n.startingPrice(),
                        n.minIncrement(), n.startingPrice(), null, null, n.startsAt(), n.endsAt(), 0,
                        AuctionStatus.SCHEDULED, 1, n.createdBy(), n.createdAt());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Không thêm được phiên đấu giá", e);
        }
    }

    @Override
    public void update(Auction a) {
        String sql = "UPDATE auctions SET title = ?, description = ?, starting_price = ?, min_increment = ?, "
                + "current_price = ?, current_bidder_id = ?, winner_id = ?, starts_at = ?, ends_at = ?, "
                + "extension_count = ?, status = ?, version = ?, created_by = ?, created_at = ? WHERE id = ?";
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
            ps.setLong(15, a.id());
            if (ps.executeUpdate() == 0) {
                throw new DataAccessException("Không có phiên " + a.id(), null);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Không cập nhật được phiên " + a.id(), e);
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

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setLong(index, value);
        }
    }

    private static Auction map(ResultSet rs) throws SQLException {
        long bidder = rs.getLong("current_bidder_id");
        Long currentBidderId = rs.wasNull() ? null : bidder;
        long winner = rs.getLong("winner_id");
        Long winnerId = rs.wasNull() ? null : winner;
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
                rs.getLong("created_at"));
    }
}
