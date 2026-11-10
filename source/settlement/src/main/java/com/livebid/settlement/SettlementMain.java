package com.livebid.settlement;

import com.livebid.common.Protocol;
import java.nio.file.Path;
import java.rmi.server.ExportException;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Điểm khởi động dịch vụ Settlement (RMI), chạy như một tiến trình riêng.
 *
 * <p>Các bước: tạo {@link SettlementServiceImpl} → export → tạo (hoặc dùng lại) RMI Registry →
 * {@code rebind} tên {@link Protocol#RMI_SETTLEMENT_NAME} → giữ tiến trình sống tới khi {@code Ctrl+C}.
 *
 * <p>Cấu hình bằng system property (truyền qua {@code mvn ... -Dkey=value}):
 *
 * <ul>
 *   <li>{@code settlement.port} — cổng RMI Registry, mặc định {@link Protocol#DEFAULT_RMI_PORT}.</li>
 *   <li>{@code settlement.outputDir} — nơi ghi biên bản, mặc định {@code settlement/output}.</li>
 *   <li>{@code java.rmi.server.hostname} — địa chỉ IP máy này mà client/server khác phải gọi tới;
 *       bắt buộc đặt khi chạy khác máy, ví dụ {@code -Djava.rmi.server.hostname=192.168.1.10}.</li>
 * </ul>
 */
public final class SettlementMain {

    private static final Logger log = LoggerFactory.getLogger(SettlementMain.class);

    /** Giữ tham chiếu tĩnh để đối tượng đã export không bị thu gom rác. */
    private static SettlementServiceImpl service;
    private static Remote stub;

    private SettlementMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("settlement.port", Protocol.DEFAULT_RMI_PORT);
        Path outputDir = Path.of(System.getProperty("settlement.outputDir", "settlement/output"));

        service = new SettlementServiceImpl(outputDir);
        stub = UnicastRemoteObject.exportObject(service, 0);

        Registry registry = openRegistry(port);
        registry.rebind(Protocol.RMI_SETTLEMENT_NAME, stub);

        String host = System.getProperty("java.rmi.server.hostname", "localhost");
        log.info("Settlement sẵn sàng: rmi://{}:{}/{}, biên bản ghi tại {}",
                host, port, Protocol.RMI_SETTLEMENT_NAME, outputDir.toAbsolutePath());

        Runtime.getRuntime().addShutdownHook(new Thread(SettlementMain::shutdown, "settlement-shutdown"));

        // Giữ tiến trình sống tới khi người dùng bấm Ctrl+C.
        new CountDownLatch(1).await();
    }

    /**
     * Tạo registry ở cổng đã cho; nếu cổng đã có registry (lần chạy trước chưa tắt) thì dùng lại thay vì
     * báo lỗi, để buổi demo không phải khởi động lại máy.
     */
    private static Registry openRegistry(int port) throws Exception {
        try {
            return LocateRegistry.createRegistry(port);
        } catch (ExportException e) {
            log.warn("Cổng RMI {} đã có registry, dùng lại registry sẵn có", port);
            return LocateRegistry.getRegistry(port);
        }
    }

    private static void shutdown() {
        try {
            if (stub != null) {
                UnicastRemoteObject.unexportObject(service, true);
            }
            log.info("Settlement đã dừng");
        } catch (Exception e) {
            log.warn("Không gỡ được đối tượng RMI khi dừng: {}", e.getMessage());
        }
    }
}
