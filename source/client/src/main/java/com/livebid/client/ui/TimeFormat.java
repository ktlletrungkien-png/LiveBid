package com.livebid.client.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Định dạng thời gian hiển thị trên giao diện, theo múi giờ của máy đang chạy.
 *
 * <p>Mốc thời gian trong giao thức là epoch ms UTC; người xem chỉ cần giờ địa phương nên đổi tại đây. Cả
 * {@link AuctionListPanel} và {@link RoomPanel} dùng chung để chuỗi ngày giờ không lệch nhau.
 */
final class TimeFormat {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM", Locale.ROOT);
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private TimeFormat() {
    }

    /** {@code HH:mm dd/MM}, ví dụ {@code "20:30 16/11"}. */
    static String dateTime(long epochMillis) {
        return DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    /** {@code HH:mm}, ví dụ {@code "20:30"}. */
    static String clock(long epochMillis) {
        return CLOCK.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    /**
     * Đồng hồ đếm ngược: {@code mm:ss}, hoặc {@code h:mm:ss} khi còn từ một giờ trở lên. Số âm coi là 0.
     */
    static String duration(long millis) {
        long totalSeconds = Math.max(0, millis) / 1_000;
        long hours = totalSeconds / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}
