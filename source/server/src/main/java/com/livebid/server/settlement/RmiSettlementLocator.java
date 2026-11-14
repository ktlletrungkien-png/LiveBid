package com.livebid.server.settlement;

import com.livebid.common.Protocol;
import com.livebid.common.rmi.SettlementService;
import java.rmi.registry.LocateRegistry;
import java.util.Objects;

/**
 * Lấy stub từ RMI Registry ở {@code host:port}, tên {@link Protocol#RMI_SETTLEMENT_NAME}.
 *
 * <p>Không giữ stub: mỗi lần {@link #lookup()} đều hỏi Registry, nên nếu Settlement khởi động lại (stub cũ
 * chết) thì lần gọi sau vẫn lấy được stub mới. {@link SettlementClient} tự nhớ stub để không phải hỏi
 * Registry mỗi việc, và tự bỏ stub khi gặp lỗi.
 */
public final class RmiSettlementLocator implements SettlementServiceLocator {

    private final String host;
    private final int port;

    public RmiSettlementLocator(String host, int port) {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("Cổng RMI không hợp lệ: " + port);
        }
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
    }

    @Override
    public SettlementService lookup() throws Exception {
        return (SettlementService) LocateRegistry.getRegistry(host, port)
                .lookup(Protocol.RMI_SETTLEMENT_NAME);
    }

    @Override
    public String toString() {
        return "rmi://" + host + ":" + port + "/" + Protocol.RMI_SETTLEMENT_NAME;
    }
}
