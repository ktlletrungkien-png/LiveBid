package com.livebid.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.rmi.AuctionResultDto;
import com.livebid.common.rmi.SettlementSummaryDto;
import com.livebid.common.rmi.SettlementSummaryDto.WinnerStat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Kiểm tra {@link SettlementServiceImpl} ở mức nghiệp vụ, không cần RMI. */
class SettlementServiceImplTest {

    @TempDir
    Path outputDir;

    private static AuctionResultDto result(long auctionId, Long winnerId, String winnerName, long finalPrice,
            int bidCount) {
        return new AuctionResultDto(auctionId, "Phiên " + auctionId, winnerId, winnerName, finalPrice, bidCount,
                1_760_000_000_000L, 1_760_000_300_000L);
    }

    private static long jsonLines(Path outputDir) throws Exception {
        Path file = outputDir.resolve("results.jsonl");
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> !line.isBlank()).count();
    }

    @Test
    void ghiMotPhienCoNguoiThang() throws Exception {
        SettlementServiceImpl service = new SettlementServiceImpl(outputDir);
        service.recordResult(result(1, 7L, "Nguyễn Văn A", 280_000L, 12));

        SettlementSummaryDto summary = service.getSummary();
        assertEquals(1, summary.totalAuctions());
        assertEquals(1, summary.auctionsWithWinner());
        assertEquals(280_000L, summary.totalValue());
        assertEquals(List.of(new WinnerStat(7L, "Nguyễn Văn A", 1, 280_000L)), summary.topWinners());

        assertEquals(1, jsonLines(outputDir));
        Path minutes = outputDir.resolve("bien-ban-phien-1.txt");
        assertTrue(Files.exists(minutes), "Phải có biên bản dễ đọc");
        String text = Files.readString(minutes, StandardCharsets.UTF_8);
        assertTrue(text.contains("Phiên 1"));
        assertTrue(text.contains("Nguyễn Văn A"));
        assertTrue(text.contains("280.000đ"));
        assertTrue(text.contains("12"));
    }

    @Test
    void goiLaiCungPhienKhongCongTrung() throws Exception {
        SettlementServiceImpl service = new SettlementServiceImpl(outputDir);
        AuctionResultDto same = result(1, 7L, "A", 100_000L, 3);
        service.recordResult(same);
        service.recordResult(same);
        service.recordResult(result(1, 7L, "A", 100_000L, 3));

        assertEquals(1, service.getSummary().totalAuctions());
        assertEquals(1, service.getSummary().auctionsWithWinner());
        assertEquals(100_000L, service.getSummary().totalValue());
        assertEquals(1, jsonLines(outputDir), "Chỉ được ghi một dòng cho mỗi auctionId");
    }

    @Test
    void phienKhongCoNguoiThang() throws Exception {
        SettlementServiceImpl service = new SettlementServiceImpl(outputDir);
        service.recordResult(result(2, null, null, 0L, 0));

        SettlementSummaryDto summary = service.getSummary();
        assertEquals(1, summary.totalAuctions());
        assertEquals(0, summary.auctionsWithWinner());
        assertEquals(0, summary.totalValue());
        assertTrue(summary.topWinners().isEmpty());

        String text = Files.readString(outputDir.resolve("bien-ban-phien-2.txt"), StandardCharsets.UTF_8);
        assertTrue(text.contains("Chưa có người thắng"));
    }

    @Test
    void topWinnersSapXepVaGioiHanNam() throws Exception {
        SettlementServiceImpl service = new SettlementServiceImpl(outputDir);
        // userId 2: 2 trận, 500.000đ; userId 1: 2 trận, 300.000đ; userId 3..7: mỗi người 1 trận, 10.000đ.
        service.recordResult(result(10, 1L, "Một", 100_000L, 1));
        service.recordResult(result(11, 1L, "Một", 200_000L, 1));
        service.recordResult(result(12, 2L, "Hai", 200_000L, 1));
        service.recordResult(result(13, 2L, "Hai", 300_000L, 1));
        for (long userId = 3; userId <= 7; userId++) {
            service.recordResult(result(100 + userId, userId, "U" + userId, 10_000L, 1));
        }

        List<WinnerStat> top = service.getSummary().topWinners();
        assertEquals(5, top.size(), "Chỉ lấy tối đa 5 người");
        assertEquals(2L, top.get(0).userId(), "Nhiều trận thắng hơn đứng trước");
        assertEquals(1L, top.get(1).userId(), "Cùng số trận thì tổng tiền lớn hơn đứng trước");
        assertEquals(3L, top.get(2).userId(), "Bằng nhau thì userId nhỏ hơn đứng trước");
        assertEquals(4L, top.get(3).userId());
        assertEquals(5L, top.get(4).userId());
    }

    @Test
    void tenHienThiMoiNhatDuocGiuLai() throws Exception {
        SettlementServiceImpl service = new SettlementServiceImpl(outputDir);
        service.recordResult(result(1, 7L, "Tên cũ", 100_000L, 1));
        service.recordResult(result(2, 7L, "Tên mới", 200_000L, 1));

        WinnerStat stat = service.getSummary().topWinners().get(0);
        assertEquals("Tên mới", stat.displayName());
        assertEquals(2, stat.wins());
        assertEquals(300_000L, stat.totalSpent());
    }

    @Test
    void khoiDongLaiNapLaiDuLieu() throws Exception {
        SettlementServiceImpl first = new SettlementServiceImpl(outputDir);
        first.recordResult(result(1, 7L, "A", 100_000L, 3));
        first.recordResult(result(2, 8L, "B", 200_000L, 4));
        SettlementSummaryDto before = first.getSummary();

        SettlementServiceImpl second = new SettlementServiceImpl(outputDir);
        assertEquals(before, second.getSummary(), "Khởi động lại phải nạp lại đúng thống kê");

        second.recordResult(result(1, 7L, "A", 100_000L, 3));
        assertEquals(before, second.getSummary(), "Phiên đã ghi trước đó vẫn không bị cộng trùng");
        assertEquals(2, jsonLines(outputDir));
    }

    @Test
    void tuChoiKetQuaKhongHopLe() throws Exception {
        SettlementServiceImpl service = new SettlementServiceImpl(outputDir);
        assertThrows(RemoteException.class, () -> service.recordResult(null));
        assertThrows(RemoteException.class, () -> service.recordResult(result(0, null, null, 0L, 0)));
        assertEquals(0, service.getSummary().totalAuctions());
    }
}
