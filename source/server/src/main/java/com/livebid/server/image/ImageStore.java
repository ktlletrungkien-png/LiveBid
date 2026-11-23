package com.livebid.server.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Kho ảnh đại diện của phiên: mỗi ảnh là một file trong một thư mục, tên file chính là mã ảnh
 * ({@code <32 ký tự hex>.<jpg|png|webp>}).
 *
 * <p>Chỉ nhận JPEG, PNG, WebP, nhận diện theo <b>chữ ký đầu file</b> chứ không tin tên file hay
 * Content-Type do client gửi. Mã ảnh ngẫu nhiên 128 bit, không đoán được và không chứa đường dẫn.
 * Ảnh không bao giờ bị ghi đè nên có thể cho trình duyệt cache lâu.
 */
public final class ImageStore {

    /** Dung lượng tối đa một ảnh. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final Pattern ID_PATTERN = Pattern.compile("[0-9a-f]{32}\\.(jpg|png|webp)");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path directory;

    public ImageStore(Path directory) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
    }

    /** Mã ảnh đúng định dạng (không kiểm tra file có tồn tại hay không). */
    public static boolean isValidId(String imageId) {
        return imageId != null && ID_PATTERN.matcher(imageId).matches();
    }

    /** Lỗi khi ảnh tải lên không hợp lệ; thông điệp hiển thị được cho người dùng. */
    public static final class InvalidImageException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidImageException(String message) {
            super(message);
        }
    }

    /**
     * Đọc ảnh từ {@code in} (tối đa {@link #MAX_BYTES} byte), kiểm tra định dạng rồi lưu.
     *
     * @return mã ảnh mới
     * @throws InvalidImageException ảnh rỗng, quá lớn hoặc không phải JPEG/PNG/WebP
     */
    public String save(InputStream in) throws InvalidImageException, IOException {
        byte[] data = in.readNBytes(MAX_BYTES + 1);
        if (data.length == 0) {
            throw new InvalidImageException("Chưa chọn ảnh");
        }
        if (data.length > MAX_BYTES) {
            throw new InvalidImageException("Ảnh tối đa " + (MAX_BYTES / 1024 / 1024) + " MB");
        }
        String extension = detectExtension(data);
        if (extension == null) {
            throw new InvalidImageException("Chỉ nhận ảnh JPEG, PNG hoặc WebP");
        }
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        String imageId = HexFormat.of().formatHex(random) + "." + extension;

        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "upload-", ".tmp");
        try {
            Files.write(temp, data);
            Files.move(temp, directory.resolve(imageId), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        return imageId;
    }

    /** Ảnh có tồn tại trong kho không; mã sai định dạng coi như không tồn tại. */
    public boolean exists(String imageId) {
        return find(imageId).isPresent();
    }

    /** Đường dẫn file của ảnh nếu mã hợp lệ và file tồn tại. */
    public Optional<Path> find(String imageId) {
        if (!isValidId(imageId)) {
            return Optional.empty();
        }
        Path file = directory.resolve(imageId);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    /** Content-Type theo phần mở rộng của mã ảnh (mã phải hợp lệ). */
    public static String contentType(String imageId) {
        if (imageId.endsWith(".png")) {
            return "image/png";
        }
        if (imageId.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private static String detectExtension(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
