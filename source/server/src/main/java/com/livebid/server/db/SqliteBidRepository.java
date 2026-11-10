package com.livebid.server.db;

import com.livebid.server.model.Bid;
import com.livebid.server.repo.BidRepository;
import com.livebid.server.repo.DataAccessException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bản SQLite của {@link BidRepository}, bảng {@code bids}. Chỉ gọi từ luồng executor. */
public final class SqliteBidRepository implements BidRepository {

    private final SqliteDatabase db;

    public SqliteBidRepository(SqliteDatabase db) {
        this.db = Objects.requireNonNull(db);
    }

    @Override
    public Bid insert(long auctionId, long userId, long amount, long acceptedAt, String requestId) {
        String sql = "INSERT INTO bids (auction_id, user_id, amount, accepted_at, request_id) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, auctionId);
            ps.setLong(2, userId);
            ps.setLong(3, amount);
            ps.setLong(4, acceptedAt);
            ps.setString(5, requestId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Bid(keys.getLong(1), auctionId, userId, amount, acceptedAt, requestId);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Không thêm được bid vào phiên " + auctionId, e);
        }
    }

    @Override
    public List<Bid> findHistory(long auctionId, Long beforeBidId, int limit) {
        String sql = "SELECT id, auction_id, user_id, amount, accepted_at, request_id FROM bids WHERE auction_id = ?"
                + (beforeBidId == null ? "" : " AND id < ?")
                + " ORDER BY id DESC LIMIT ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            int index = 1;
            ps.setLong(index++, auctionId);
            if (beforeBidId != null) {
                ps.setLong(index++, beforeBidId);
            }
            ps.setInt(index, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Bid> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new Bid(
                            rs.getLong("id"),
                            rs.getLong("auction_id"),
                            rs.getLong("user_id"),
                            rs.getLong("amount"),
                            rs.getLong("accepted_at"),
                            rs.getString("request_id")));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi đọc lịch sử bid của phiên " + auctionId, e);
        }
    }

    @Override
    public int countByAuction(long auctionId) {
        try (PreparedStatement ps = db.connection().prepareStatement("SELECT COUNT(*) FROM bids WHERE auction_id = ?")) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi đếm bid của phiên " + auctionId, e);
        }
    }
}
