package com.livebid.server.db;

import com.livebid.server.model.SettlementTask;
import com.livebid.server.repo.DataAccessException;
import com.livebid.server.repo.SettlementQueueRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bản SQLite của {@link SettlementQueueRepository}, bảng {@code settlement_queue}. Chỉ gọi từ luồng executor. */
public final class SqliteSettlementQueueRepository implements SettlementQueueRepository {

    private final SqliteDatabase db;

    public SqliteSettlementQueueRepository(SqliteDatabase db) {
        this.db = Objects.requireNonNull(db);
    }

    /** {@code INSERT OR IGNORE}: phiên đã có trong hàng đợi thì không thêm nữa (auction_id là UNIQUE). */
    @Override
    public void enqueue(long auctionId) {
        execute("INSERT OR IGNORE INTO settlement_queue (auction_id, status, attempts) VALUES (?, 'PENDING', 0)",
                auctionId);
    }

    @Override
    public List<SettlementTask> findPending() {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, auction_id, status, attempts, last_attempt_at FROM settlement_queue "
                        + "WHERE status = 'PENDING' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            List<SettlementTask> result = new ArrayList<>();
            while (rs.next()) {
                long last = rs.getLong("last_attempt_at");
                Long lastAttemptAt = rs.wasNull() ? null : last;
                result.add(new SettlementTask(
                        rs.getLong("id"),
                        rs.getLong("auction_id"),
                        SettlementTask.Status.valueOf(rs.getString("status")),
                        rs.getInt("attempts"),
                        lastAttemptAt));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi đọc hàng đợi quyết toán", e);
        }
    }

    @Override
    public void markDone(long taskId) {
        execute("UPDATE settlement_queue SET status = 'DONE' WHERE id = ?", taskId);
    }

    @Override
    public void recordFailure(long taskId, long attemptAt) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE settlement_queue SET attempts = attempts + 1, last_attempt_at = ? WHERE id = ?")) {
            ps.setLong(1, attemptAt);
            ps.setLong(2, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Không ghi được lỗi quyết toán " + taskId, e);
        }
    }

    private void execute(String sql, long param) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setLong(1, param);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi ghi hàng đợi quyết toán", e);
        }
    }
}
