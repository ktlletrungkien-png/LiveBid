package com.livebid.settlement;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.rmi.AuctionResultDto;
import com.livebid.common.rmi.SettlementService;
import com.livebid.common.rmi.SettlementSummaryDto;
import com.livebid.common.rmi.SettlementSummaryDto.WinnerStat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cài đặt {@link SettlementService}: ghi biên bản từng phiên ra đĩa rồi cộng dồn thống kê.
 *
 * <p>Đây là lớp thường (không kế thừa {@code UnicastRemoteObject}) để test được mà không cần RMI;
 * {@link SettlementMain} export nó khi chạy thật.
 *
 * <p>Hai tệp trong thư mục đầu ra:
 *
 * <ul>
 *   <li>{@code results.jsonl} — mỗi dòng một {@link AuctionResultDto} dạng JSON, dùng để nạp lại khi
 *       khởi động.</li>
 *   <li>{@code bien-ban-phien-<auctionId>.txt} — biên bản dễ đọc cho người xem.</li>
 * </ul>
 *
 * <p>{@link #recordResult(AuctionResultDto)} <b>idempotent</b> theo {@code auctionId}: server có thể gửi
 * lại cùng một kết quả khi mạng chập chờn, và lần gửi lại không được cộng thống kê hai lần.
 *
 * <p>Thứ tự ghi: <b>file trước, bộ nhớ sau</b>. Nếu ghi file lỗi thì ném {@link java.rmi.RemoteException}
 * để server thử lại; bộ nhớ chỉ được cập nhật khi dữ liệu đã nằm an toàn trên đĩa.
 */
public final class SettlementServiceImpl implements SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementServiceImpl.class);

    private static final String RESULTS_FILE = "results.jsonl";
    private static final String MINUTES_FILE_PREFIX = "bien-ban-phien-";
    private static final String MINUTES_FILE_SUFFIX = ".txt";

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("HH:mm:ss dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
            .withZone(VIETNAM);

    /** Tối đa số người thắng trong bảng xếp hạng của {@link #getSummary()}. */
    private static final int TOP_WINNER_LIMIT = 5;

    private final Path outputDir;
    private final Path resultsFile;
    private final Gson gson = JsonCodec.gson();

    /** auctionId → kết quả đã nhận; LinkedHashMap giữ thứ tự nhận để "tên hiển thị mới nhất" thắng. */
    private final Map<Long, AuctionResultDto> results = new LinkedHashMap<>();

    /**
     * @param outputDir thư mục ghi biên bản; được tạo nếu chưa có, và được nạp lại từ {@code results.jsonl}
     * @throws IOException nếu không tạo được thư mục hoặc không đọc được dữ liệu cũ
     */
    public SettlementServiceImpl(Path outputDir) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("Thư mục biên bản không được null");
        }
        this.outputDir = outputDir;
        this.resultsFile = outputDir.resolve(RESULTS_FILE);
        Files.createDirectories(outputDir);
        loadExistingResults();
    }

    @Override
    public synchronized void recordResult(AuctionResultDto result) throws RemoteException {
        if (result == null) {
            throw new RemoteException("Kết quả phiên không được null");
        }
        if (result.auctionId() <= 0) {
            throw new RemoteException("auctionId không hợp lệ: " + result.auctionId());
        }
        if (results.containsKey(result.auctionId())) {
            log.info("Bỏ qua kết quả phiên {}: đã nhận trước đó", result.auctionId());
            return;
        }

        try {
            appendResult(result);
            writeMinutes(result);
        } catch (IOException e) {
            throw new RemoteException("Không ghi được biên bản", e);
        }

        results.put(result.auctionId(), result);
        log.info("Đã ghi biên bản phiên {}: {}", result.auctionId(), result.title());
    }

    @Override
    public synchronized SettlementSummaryDto getSummary() {
        long totalAuctions = results.size();
        long auctionsWithWinner = 0;
        long totalValue = 0;

        Map<Long, WinnerStat> byWinner = new HashMap<>();
        for (AuctionResultDto result : results.values()) {
            if (!result.hasWinner()) {
                continue;
            }
            auctionsWithWinner++;
            totalValue += result.finalPrice();
            byWinner.merge(result.winnerUserId(),
                    new WinnerStat(result.winnerUserId(), result.winnerDisplayName(), 1, result.finalPrice()),
                    (oldStat, newStat) -> new WinnerStat(
                            oldStat.userId(),
                            // Tên hiển thị mới nhất (theo thứ tự nhận) được giữ lại.
                            newStat.displayName(),
                            oldStat.wins() + newStat.wins(),
                            oldStat.totalSpent() + newStat.totalSpent()));
        }

        List<WinnerStat> topWinners = new ArrayList<>(byWinner.values());
        topWinners.sort(Comparator
                .comparingInt(WinnerStat::wins).reversed()
                .thenComparing(Comparator.comparingLong(WinnerStat::totalSpent).reversed())
                .thenComparingLong(WinnerStat::userId));
        if (topWinners.size() > TOP_WINNER_LIMIT) {
            topWinners = topWinners.subList(0, TOP_WINNER_LIMIT);
        }

        return new SettlementSummaryDto(totalAuctions, auctionsWithWinner, totalValue, List.copyOf(topWinners));
    }

    /** Thư mục đang ghi biên bản (dùng cho log và test). */
    public Path outputDir() {
        return outputDir;
    }

    private void loadExistingResults() throws IOException {
        if (!Files.exists(resultsFile)) {
            return;
        }
        List<String> lines = Files.readAllLines(resultsFile, StandardCharsets.UTF_8);
        int loaded = 0;
        int skipped = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                AuctionResultDto result = gson.fromJson(line, AuctionResultDto.class);
                if (result == null || result.auctionId() <= 0) {
                    skipped++;
                    continue;
                }
                if (results.putIfAbsent(result.auctionId(), result) == null) {
                    loaded++;
                }
            } catch (JsonParseException e) {
                skipped++;
                log.warn("Bỏ qua một dòng hỏng trong {}: {}", RESULTS_FILE, e.getMessage());
            }
        }
        log.info("Nạp lại {} kết quả từ {} (bỏ qua {})", loaded, resultsFile.toAbsolutePath(), skipped);
    }

    private void appendResult(AuctionResultDto result) throws IOException {
        Files.writeString(resultsFile, gson.toJson(result) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
    }

    private void writeMinutes(AuctionResultDto result) throws IOException {
        Path minutesFile = outputDir.resolve(MINUTES_FILE_PREFIX + result.auctionId() + MINUTES_FILE_SUFFIX);
        StringBuilder text = new StringBuilder()
                .append("BIÊN BẢN PHIÊN ĐẤU GIÁ\n")
                .append("======================\n\n")
                .append("Mã phiên        : ").append(result.auctionId()).append('\n')
                .append("Tên phiên       : ").append(result.title()).append('\n')
                .append("Người thắng     : ");
        if (result.hasWinner()) {
            text.append(result.winnerDisplayName() == null ? "Người dùng #" + result.winnerUserId()
                    : result.winnerDisplayName())
                    .append(" (ID ").append(result.winnerUserId()).append(")\n");
        } else {
            text.append("Chưa có người thắng\n");
        }
        text.append("Giá thắng       : ").append(formatVnd(result.finalPrice())).append('\n')
                .append("Số lượt đặt giá : ").append(result.bidCount()).append('\n')
                .append("Bắt đầu         : ").append(formatTime(result.startsAt())).append('\n')
                .append("Kết thúc        : ").append(formatTime(result.finishedAt())).append('\n');

        Files.writeString(minutesFile, text.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /** {@code 280000} → {@code "280.000đ"}. */
    static String formatVnd(long amount) {
        return String.format(Locale.US, "%,d", amount).replace(',', '.') + "đ";
    }

    private static String formatTime(long epochMillis) {
        return TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }
}
