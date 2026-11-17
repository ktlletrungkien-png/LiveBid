package com.livebid.client.room;

/**
 * Đọc và định dạng số tiền VND ở phía client.
 *
 * <p>Người dùng Việt Nam hay gõ {@code 280.000}, {@code 280,000} hoặc {@code 280 000}; cả ba đều là
 * 280000 đồng. Lớp này gom các dạng đó về {@code long} trước khi gửi {@code PLACE_BID}, và định dạng
 * ngược lại thành {@code 280.000đ} để hiển thị.
 *
 * <p>Chỉ nhận số nguyên dương; tiền âm, chữ cái, ký tự lạ và số vượt {@link Long#MAX_VALUE} đều báo lỗi
 * tiếng Việt để giao diện hiện ngay, không gửi lên server.
 */
public final class MoneyFormat {

    private static final char GROUP_SEPARATOR = '.';
    private static final char CURRENCY_SUFFIX = 'đ';

    private MoneyFormat() {
    }

    /**
     * Đọc chuỗi người dùng nhập thành số tiền.
     *
     * <p>Chấp nhận dấu chấm, dấu phẩy và khoảng trắng (kể cả không ngắt dòng) làm dấu phân cách nghìn.
     *
     * @throws IllegalArgumentException khi rỗng, có ký tự không phải chữ số, {@code <= 0} hoặc quá lớn
     */
    public static long parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Chưa nhập số tiền");
        }
        String digits = stripSeparators(text);
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("Chưa nhập số tiền");
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException(
                        "Số tiền chỉ được chứa chữ số, dấu chấm, dấu phẩy hoặc khoảng trắng: " + text.trim());
            }
        }
        long value;
        try {
            value = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Số tiền quá lớn: " + text.trim(), e);
        }
        if (value <= 0) {
            throw new IllegalArgumentException("Số tiền phải lớn hơn 0");
        }
        return value;
    }

    /**
     * Định dạng số tiền theo nhóm ba chữ số bằng dấu chấm, ví dụ {@code 280000 -> "280.000đ"}.
     *
     * @throws IllegalArgumentException khi {@code amount} âm
     */
    public static String format(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Số tiền không được âm: " + amount);
        }
        String digits = Long.toString(amount);
        StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        int firstGroup = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
        out.append(digits, 0, firstGroup);
        for (int i = firstGroup; i < digits.length(); i += 3) {
            out.append(GROUP_SEPARATOR).append(digits, i, i + 3);
        }
        return out.append(CURRENCY_SUFFIX).toString();
    }

    private static String stripSeparators(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == ',' || Character.isWhitespace(c)
                    || c == '\u00A0' || c == '\u202F' || c == '\u2009') {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
