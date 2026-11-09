package com.livebid.server.repo.memory;

import com.livebid.server.repo.TransactionManager;

/** Chạy thẳng khối lệnh, không rollback. Chỉ dùng cho test và phát triển khi chưa có SQLite. */
public final class InMemoryTransactionManager implements TransactionManager {

    @Override
    public <T> T inTransaction(Work<T> work) {
        return work.run();
    }
}
