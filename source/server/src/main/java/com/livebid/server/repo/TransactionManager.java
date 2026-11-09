package com.livebid.server.repo;

/**
 * Chạy một khối thao tác trong một transaction. <b>Hoè cài đặt bằng JDBC</b>
 * ({@code setAutoCommit(false)} → commit; ném lỗi thì rollback rồi ném tiếp).
 *
 * <p>Chỉ được gọi từ executor nghiệp vụ (một luồng), nên bản SQLite có thể dùng chung một
 * {@code Connection}.
 */
public interface TransactionManager {

    <T> T inTransaction(Work<T> work);

    @FunctionalInterface
    interface Work<T> {
        T run();
    }
}
