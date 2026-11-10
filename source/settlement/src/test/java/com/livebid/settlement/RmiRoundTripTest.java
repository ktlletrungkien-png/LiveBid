package com.livebid.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.Protocol;
import com.livebid.common.rmi.AuctionResultDto;
import com.livebid.common.rmi.SettlementService;
import com.livebid.common.rmi.SettlementSummaryDto;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gọi {@link SettlementService} qua RMI Registry thật trên cổng ngẫu nhiên.
 *
 * <p>Không dùng cổng 1099 cố định để test chạy được song song và không đụng tiến trình Settlement đang chạy.
 */
class RmiRoundTripTest {

    @TempDir
    Path outputDir;

    private SettlementServiceImpl impl;
    private Registry registry;

    @AfterEach
    void cleanup() {
        if (impl != null) {
            try {
                UnicastRemoteObject.unexportObject(impl, true);
            } catch (Exception ignored) {
                // Đã gỡ hoặc chưa export được — không ảnh hưởng kết quả test.
            }
        }
        if (registry != null) {
            try {
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (Exception ignored) {
                // Registry có thể đã dừng cùng tiến trình.
            }
        }
    }

    @Test
    void goiRecordResultVaGetSummaryQuaRegistry() throws Exception {
        int port = freePort();

        impl = new SettlementServiceImpl(outputDir);
        Remote stub = UnicastRemoteObject.exportObject(impl, 0);
        registry = LocateRegistry.createRegistry(port);
        registry.rebind(Protocol.RMI_SETTLEMENT_NAME, stub);

        Registry remote = LocateRegistry.getRegistry("127.0.0.1", port);
        SettlementService service = (SettlementService) remote.lookup(Protocol.RMI_SETTLEMENT_NAME);

        service.recordResult(new AuctionResultDto(1, "Phiên qua RMI", 5L, "Bình", 150_000L, 3,
                1_760_000_000_000L, 1_760_000_060_000L));

        SettlementSummaryDto summary = service.getSummary();
        assertEquals(1, summary.totalAuctions());
        assertEquals(1, summary.auctionsWithWinner());
        assertEquals(150_000L, summary.totalValue());
        assertEquals(1, summary.topWinners().size());
        assertEquals(5L, summary.topWinners().get(0).userId());
        assertEquals("Bình", summary.topWinners().get(0).displayName());

        // Gửi lại cùng auctionId qua RMI cũng không được cộng trùng.
        service.recordResult(new AuctionResultDto(1, "Phiên qua RMI", 5L, "Bình", 150_000L, 3,
                1_760_000_000_000L, 1_760_000_060_000L));
        assertEquals(1, service.getSummary().totalAuctions());

        assertTrue(Files.exists(outputDir.resolve("bien-ban-phien-1.txt")));
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
