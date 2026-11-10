package com.livebid.server.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Băm mật khẩu bằng PBKDF2WithHmacSHA256.
 *
 * <ul>
 *   <li>Mỗi mật khẩu có một salt 16 byte ngẫu nhiên riêng; khóa dẫn xuất 256 bit.</li>
 *   <li>Số vòng lặp truyền qua constructor và <b>được lưu cùng hash</b>, nên đổi cấu hình sau này không làm
 *       hỏng mật khẩu cũ: {@link #verify} luôn dùng số vòng lặp đã lưu.</li>
 *   <li>So sánh bằng {@link MessageDigest#isEqual}, thời gian không phụ thuộc vị trí byte khác nhau.</li>
 *   <li>Không log mật khẩu. Người gọi tự xóa mảng {@code char[]} sau khi dùng.</li>
 * </ul>
 *
 * <p>Lớp này không giữ trạng thái khi băm nên có thể dùng từ nhiều luồng.
 */
public final class PasswordHasher {

    /** Số vòng lặp mặc định khi chạy thật; test dùng số nhỏ hơn cho nhanh. */
    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param iterations số vòng lặp PBKDF2, phải ≥ 1
     */
    public PasswordHasher(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("Số vòng lặp PBKDF2 phải ≥ 1, nhận được " + iterations);
        }
        this.iterations = iterations;
    }

    /** Kết quả băm: hash và salt đều ở dạng Base64, cùng số vòng lặp đã dùng. */
    public record HashedPassword(String hash, String salt, int iterations) {
    }

    /** Băm mật khẩu mới với salt ngẫu nhiên. Không sửa mảng {@code password}. */
    public HashedPassword hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] key = derive(password, salt, iterations);
        return new HashedPassword(encode(key), encode(salt), iterations);
    }

    /**
     * Kiểm tra mật khẩu với hash, salt và số vòng lặp đã lưu.
     *
     * @return {@code true} nếu khớp
     */
    public boolean verify(char[] password, String hash, String salt, int iterations) {
        byte[] expected = decode(hash);
        byte[] actual = derive(password, decode(salt), iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        KeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Thuật toán " + ALGORITHM + " không khả dụng trên JVM này", e);
        } finally {
            ((PBEKeySpec) spec).clearPassword();
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] decode(String text) {
        return Base64.getDecoder().decode(text);
    }
}
