package com.livebid.server.tcp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Tách luồng byte TCP thành từng thông điệp "một dòng JSON, kết thúc bằng {@code '\n'}".
 *
 * <p>TCP không giữ ranh giới thông điệp: một lần {@code read()} có thể trả về nửa thông điệp, hoặc
 * vài thông điệp dính nhau. Bộ tách này giữ phần còn dở giữa các lần {@link #feed} và chỉ trả ra
 * một khung khi gặp {@code '\n'}.
 *
 * <ul>
 *   <li>Giới hạn {@code maxFrameBytes} được kiểm tra <b>ngay khi nhận byte</b>, nên bộ đệm không bao giờ
 *       vượt quá giới hạn (khác với {@code BufferedReader.readLine()}, vốn đọc dòng dài bao nhiêu cũng được).</li>
 *   <li>Tách theo byte {@code 0x0A} rồi mới giải mã UTF-8: trong UTF-8, byte {@code 0x0A} không bao giờ
 *       nằm giữa một ký tự nhiều byte, nên ký tự tiếng Việt bị cắt đôi giữa hai lần đọc vẫn ghép đúng.</li>
 *   <li>Chấp nhận {@code "\r\n"} (bỏ {@code '\r'} cuối) và bỏ qua dòng trống.</li>
 * </ul>
 *
 * <p>Không an toàn đa luồng: mỗi kết nối dùng một bộ tách riêng, chỉ luồng đọc của kết nối đó gọi.
 */
public final class LineFrameDecoder {

    private static final int INITIAL_CAPACITY = 1024;

    private final int maxFrameBytes;
    private byte[] buffer = new byte[INITIAL_CAPACITY];
    private int length;

    /** @param maxFrameBytes số byte tối đa của một thông điệp, không tính {@code '\n'} */
    public LineFrameDecoder(int maxFrameBytes) {
        if (maxFrameBytes <= 0) {
            throw new IllegalArgumentException("maxFrameBytes phải > 0");
        }
        this.maxFrameBytes = maxFrameBytes;
    }

    /**
     * Nạp thêm byte vừa đọc được; mỗi thông điệp hoàn chỉnh được đưa cho {@code sink} theo đúng thứ tự.
     *
     * @throws FrameTooLargeException khi thông điệp đang ghép vượt quá giới hạn. Các thông điệp hoàn chỉnh
     *                                đứng trước nó trong cùng lần nạp đã được đưa cho {@code sink}.
     *                                Sau lỗi này kết nối phải bị đóng.
     */
    public void feed(byte[] data, int offset, int count, Consumer<String> sink) throws FrameTooLargeException {
        int end = offset + count;
        for (int i = offset; i < end; i++) {
            byte b = data[i];
            if (b == '\n') {
                emit(sink);
            } else {
                if (length == maxFrameBytes) {
                    throw new FrameTooLargeException(maxFrameBytes);
                }
                append(b);
            }
        }
    }

    /** Số byte của thông điệp đang ghép dở (chưa gặp {@code '\n'}). */
    public int pendingBytes() {
        return length;
    }

    private void emit(Consumer<String> sink) {
        int frameLength = length;
        if (frameLength > 0 && buffer[frameLength - 1] == '\r') {
            frameLength--;
        }
        length = 0;
        if (frameLength > 0) {
            sink.accept(new String(buffer, 0, frameLength, StandardCharsets.UTF_8));
        }
    }

    private void append(byte b) {
        if (length == buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.min(buffer.length * 2, maxFrameBytes));
        }
        buffer[length++] = b;
    }
}
