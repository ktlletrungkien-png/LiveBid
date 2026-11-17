package com.livebid.client.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.AuctionDraft;
import org.junit.jupiter.api.Test;

/** Kiểm tra {@link AuctionForm}: chuẩn hoá chuỗi, đọc tiền và các điều kiện thời gian. */
class AuctionFormTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    void taoDraftHopLeVaCatKhoangTrang() {
        AuctionDraft draft = AuctionForm.toDraft("  Đồng hồ cổ  ", "  Mô tả  ",
                "280.000", "10 000", NOW + 1_000, NOW + 60_000, NOW);
        assertEquals("Đồng hồ cổ", draft.title());
        assertEquals("Mô tả", draft.description());
        assertEquals(280_000, draft.startingPrice());
        assertEquals(10_000, draft.minIncrement());
        assertEquals(NOW + 1_000, draft.startsAt());
        assertEquals(NOW + 60_000, draft.endsAt());
    }

    @Test
    void tenTrongBiTuChoi() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AuctionForm.toDraft("   ", "", "1000", "100", NOW, NOW + 1_000, NOW));
        assertTrue(error.getMessage().contains("Tên sản phẩm"));
    }

    @Test
    void tenQuaDaiBiTuChoi() {
        String title = "a".repeat(AuctionForm.MAX_TITLE_LENGTH + 1);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AuctionForm.toDraft(title, "", "1000", "100", NOW, NOW + 1_000, NOW));
        assertTrue(error.getMessage().contains("200"));
    }

    @Test
    void moTaQuaDaiBiTuChoi() {
        String description = "a".repeat(AuctionForm.MAX_DESCRIPTION_LENGTH + 1);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AuctionForm.toDraft("Tên", description, "1000", "100", NOW, NOW + 1_000, NOW));
        assertTrue(error.getMessage().contains("5000"));
    }

    @Test
    void giaKhongHopLeBiTuChoi() {
        assertThrows(IllegalArgumentException.class,
                () -> AuctionForm.toDraft("Tên", "", "abc", "100", NOW, NOW + 1_000, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> AuctionForm.toDraft("Tên", "", "0", "100", NOW, NOW + 1_000, NOW));
        IllegalArgumentException tooLarge = assertThrows(IllegalArgumentException.class,
                () -> AuctionForm.toDraft("Tên", "", "1000", "1000000000001", NOW, NOW + 1_000, NOW));
        assertTrue(tooLarge.getMessage().contains("Bước giá"));
    }

    @Test
    void gioKetThucPhaiSauGioBatDau() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AuctionForm.toDraft("Tên", "", "1000", "100", NOW + 5_000, NOW + 5_000, NOW));
        assertTrue(error.getMessage().contains("sau giờ bắt đầu"));
    }

    @Test
    void gioKetThucPhaiOTuongLai() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> AuctionForm.toDraft("Tên", "", "1000", "100", NOW - 10_000, NOW, NOW));
        assertTrue(error.getMessage().contains("tương lai"));
    }
}
