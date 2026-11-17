package com.livebid.client.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Kiểm tra {@link MoneyFormat}: các dạng người dùng hay gõ, lỗi nhập và tràn số. */
class MoneyFormatTest {

    @Test
    void docCacDangNhapThongDung() {
        assertEquals(280_000, MoneyFormat.parse("280000"));
        assertEquals(280_000, MoneyFormat.parse("280.000"));
        assertEquals(280_000, MoneyFormat.parse("280,000"));
        assertEquals(280_000, MoneyFormat.parse("280 000"));
        assertEquals(280_000, MoneyFormat.parse("  280.000  "));
        assertEquals(280_000, MoneyFormat.parse("280\u00A0000"), "Khoảng trắng không ngắt dòng");
        assertEquals(1_000_000, MoneyFormat.parse("1.000.000"));
        assertEquals(1, MoneyFormat.parse("1"));
    }

    @Test
    void tuChoiChuoiRongVaKyTuLa() {
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse(null));
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse(""));
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("hai trăm"));
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("280.000đ"),
                "Dạng hiển thị có 'đ' không được dùng làm đầu vào");
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("-280000"));
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("28a000"));
    }

    @Test
    void tuChoiSoKhongDuong() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> MoneyFormat.parse("0"));
        assertTrue(zero.getMessage().contains("lớn hơn 0"), zero.getMessage());
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.parse("0.000"));
    }

    @Test
    void baoLoiKhiSoQuaLong() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MoneyFormat.parse("99999999999999999999"));
        assertTrue(error.getMessage().contains("quá lớn"), error.getMessage());
    }

    @Test
    void dinhDangTheoNhomBaChuSo() {
        assertEquals("0đ", MoneyFormat.format(0));
        assertEquals("999đ", MoneyFormat.format(999));
        assertEquals("1.000đ", MoneyFormat.format(1_000));
        assertEquals("280.000đ", MoneyFormat.format(280_000));
        assertEquals("1.234.567đ", MoneyFormat.format(1_234_567));
        assertEquals("9.223.372.036.854.775.807đ", MoneyFormat.format(Long.MAX_VALUE));
    }

    @Test
    void dinhDangRoiDocLaiVanDung() {
        long amount = 280_000;
        String formatted = MoneyFormat.format(amount);
        String withoutSuffix = formatted.substring(0, formatted.length() - 1);
        assertEquals(amount, MoneyFormat.parse(withoutSuffix));
    }

    @Test
    void khongDinhDangSoAm() {
        assertThrows(IllegalArgumentException.class, () -> MoneyFormat.format(-1));
    }
}
