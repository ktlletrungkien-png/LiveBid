package com.livebid.client.net;

/**
 * Chính sách thử lại khi mất kết nối TCP: chờ 1, 2, 4, 8 giây rồi giữ ở 10 giây.
 *
 * <p>Lớp thuần, không phụ thuộc Swing/đồng hồ thật nên test được bằng cách gọi thẳng
 * {@link #delayFor(int)}. {@code MainFrame} dùng {@link #nextDelayMillis()} để hẹn giờ và
 * {@link #exhausted()} để dừng sau {@value #MAX_ATTEMPTS} lần, tránh vòng thử lại chạy mãi khi server tắt.
 *
 * <p>Không giữ mật khẩu hay địa chỉ server; chỉ đếm số lần đã thử.
 */
public final class ReconnectPolicy {

    /** Thời gian chờ của lần thử đầu tiên (mili giây). */
    public static final long FIRST_DELAY_MILLIS = 1_000;

    /** Thời gian chờ tối đa giữa hai lần thử (mili giây). */
    public static final long MAX_DELAY_MILLIS = 10_000;

    /** Số lần thử tự động tối đa trước khi dừng và chờ người dùng bấm “Kết nối lại”. */
    public static final int MAX_ATTEMPTS = 8;

    private int attempts;

    /**
     * Thời gian chờ của lần thử thứ {@code attempt} (tính từ 1): 1, 2, 4, 8, 10, 10… giây.
     *
     * @throws IllegalArgumentException khi {@code attempt < 1}
     */
    public static long delayFor(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("Số lần thử phải >= 1: " + attempt);
        }
        if (attempt >= 32) {
            return MAX_DELAY_MILLIS;
        }
        long delay = FIRST_DELAY_MILLIS << (attempt - 1);
        return Math.min(delay, MAX_DELAY_MILLIS);
    }

    /** Tăng bộ đếm và trả thời gian chờ cho lần thử vừa bắt đầu. */
    public long nextDelayMillis() {
        attempts++;
        return delayFor(attempts);
    }

    /** Số lần đã gọi {@link #nextDelayMillis()}. */
    public int attempts() {
        return attempts;
    }

    /** {@code true} khi đã dùng hết {@value #MAX_ATTEMPTS} lần thử tự động. */
    public boolean exhausted() {
        return attempts >= MAX_ATTEMPTS;
    }

    /** Xoá bộ đếm sau khi kết nối lại thành công hoặc khi người dùng thử lại thủ công. */
    public void reset() {
        attempts = 0;
    }
}
