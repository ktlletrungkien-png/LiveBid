package com.livebid.server.db;

import com.livebid.server.repo.DataAccessException;
import com.livebid.server.repo.TransactionManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transaction JDBC cho SQLite. Chỉ gọi từ luồng executor nghiệp vụ nên dùng chung một {@link Connection}.
 *
 * <ul>
 *   <li>Ngoài cùng: {@code setAutoCommit(false)} → chạy khối → {@code commit()}.</li>
 *   <li>Khối ném {@link RuntimeException} hoặc {@link Error}: {@code rollback()} rồi ném lại đúng exception đó,
 *       để {@code ServiceException} của tầng nghiệp vụ đi xuyên qua.</li>
 *   <li>Lồng nhau: nếu đã trong transaction thì chỉ chạy khối; transaction ngoài quyết định commit/rollback.</li>
 *   <li>{@link SQLException} được bọc thành {@link DataAccessException}.</li>
 * </ul>
 */
public final class SqliteTransactionManager implements TransactionManager {

    private static final Logger log = LoggerFactory.getLogger(SqliteTransactionManager.class);

    private final SqliteDatabase db;
    /** Độ sâu lồng nhau; chỉ đọc/ghi trên luồng executor. */
    private int depth;

    public SqliteTransactionManager(SqliteDatabase db) {
        this.db = Objects.requireNonNull(db);
    }

    @Override
    public <T> T inTransaction(Work<T> work) {
        if (depth > 0) {
            return work.run();
        }
        Connection connection = db.connection();
        setAutoCommit(connection, false);
        depth++;
        try {
            T result = work.run();
            commit(connection);
            return result;
        } catch (RuntimeException | Error e) {
            rollbackAfterFailure(connection, e);
            throw e;
        } finally {
            depth--;
            setAutoCommit(connection, true);
        }
    }

    private static void setAutoCommit(Connection connection, boolean value) {
        try {
            connection.setAutoCommit(value);
        } catch (SQLException e) {
            throw new DataAccessException("Không đổi được chế độ auto-commit", e);
        }
    }

    private static void commit(Connection connection) {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new DataAccessException("Không commit được transaction", e);
        }
    }

    private static void rollbackAfterFailure(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            // Vẫn ném lỗi gốc; ghi lại lỗi rollback để không mất thông tin.
            original.addSuppressed(e);
            log.error("Rollback thất bại sau lỗi nghiệp vụ", e);
        }
    }
}
