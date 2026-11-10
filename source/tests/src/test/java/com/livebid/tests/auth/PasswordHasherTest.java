package com.livebid.tests.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.server.auth.PasswordHasher;
import com.livebid.server.auth.PasswordHasher.HashedPassword;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    /** Số vòng lặp nhỏ để test chạy nhanh. */
    private final PasswordHasher hasher = new PasswordHasher(1000);

    @Test
    void samePasswordTwiceGivesDifferentSaltAndHash() {
        HashedPassword first = hasher.hash("matkhau123".toCharArray());
        HashedPassword second = hasher.hash("matkhau123".toCharArray());

        assertNotEquals(first.salt(), second.salt());
        assertNotEquals(first.hash(), second.hash());
    }

    @Test
    void saltIsSixteenBytesAndHashIsDerivedKeyOfTwoHundredFiftySixBits() {
        HashedPassword h = hasher.hash("matkhau123".toCharArray());

        assertEquals(16, Base64.getDecoder().decode(h.salt()).length);
        assertEquals(32, Base64.getDecoder().decode(h.hash()).length);
        assertEquals(1000, h.iterations());
    }

    @Test
    void verifyAcceptsCorrectPasswordAndRejectsWrongOne() {
        HashedPassword h = hasher.hash("matkhau123".toCharArray());

        assertTrue(hasher.verify("matkhau123".toCharArray(), h.hash(), h.salt(), h.iterations()));
        assertFalse(hasher.verify("matkhau124".toCharArray(), h.hash(), h.salt(), h.iterations()));
    }

    @Test
    void verifyUsesStoredIterationsNotCurrentSetting() {
        HashedPassword h = hasher.hash("matkhau123".toCharArray());
        PasswordHasher otherSetting = new PasswordHasher(5000);

        // Hash được tạo với 1000 vòng: kiểm tra bằng máy đang cấu hình 5000 vẫn phải khớp.
        assertTrue(otherSetting.verify("matkhau123".toCharArray(), h.hash(), h.salt(), h.iterations()));
        // Sai số vòng lặp đã lưu thì không khớp.
        assertFalse(hasher.verify("matkhau123".toCharArray(), h.hash(), h.salt(), 999));
    }

    @Test
    void rejectsNonPositiveIterations() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(0));
    }
}
