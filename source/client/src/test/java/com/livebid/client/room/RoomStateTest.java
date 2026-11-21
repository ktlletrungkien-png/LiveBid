package com.livebid.client.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import org.junit.jupiter.api.Test;

/** Kiểm tra {@link RoomState}: version, đồng hồ theo giờ server và giá tối thiểu. */
class RoomStateTest {

    private static final long NOW = 1_000_000L;

    @Test
    void banDauChuaCoDuLieu() {
        RoomState state = new RoomState();

        assertFalse(state.isPresent());
        assertEquals(0, state.remainingMillis(NOW));
        assertEquals(0, state.minimumNextBid());
    }

    @Test
    void nhanBanDauTien() {
        RoomState state = new RoomState();

        assertTrue(state.apply(view(7, 1, AuctionStatus.SCHEDULED, 200_000, 20_000, 200_000, false,
                null, NOW, NOW + 60_000, NOW), NOW));

        assertTrue(state.isPresent());
        assertEquals(7, state.auctionId());
        assertEquals(1, state.version());
        assertEquals("Phiên 7", state.title());
        assertEquals(AuctionStatus.SCHEDULED, state.status());
        assertEquals(200_000, state.startingPrice());
        assertEquals(0, state.serverOffsetMillis());
    }

    @Test
    void boQuaVersionCuVaBang() {
        RoomState state = new RoomState();
        state.apply(view(1, 5, AuctionStatus.ACTIVE, 200_000, 20_000, 240_000, true, "An",
                NOW, NOW + 60_000, NOW), NOW);
        long priceBefore = state.currentPrice();

        assertFalse(state.apply(view(1, 5, AuctionStatus.FINISHED, 200_000, 20_000, 999_000, true, "Bình",
                NOW, NOW + 60_000, NOW), NOW), "Cùng version phải bị bỏ qua");
        assertFalse(state.apply(view(1, 4, AuctionStatus.FINISHED, 200_000, 20_000, 999_000, true, "Bình",
                NOW, NOW + 60_000, NOW), NOW), "Version cũ phải bị bỏ qua");
        assertEquals(priceBefore, state.currentPrice(), "Bản cũ không được ghi đè trạng thái mới");

        assertTrue(state.apply(view(1, 6, AuctionStatus.FINISHED, 200_000, 20_000, 260_000, true, "Bình",
                NOW, NOW + 60_000, NOW), NOW));
        assertEquals(260_000, state.currentPrice());
        assertTrue(state.isFinished());
    }

    @Test
    void demNguocTheoOffsetGioServer() {
        RoomState state = new RoomState();
        // Máy đang ở 1.000, server báo 5.000 => máy chậm 4.000 ms; phiên kết thúc lúc 65.000 theo giờ server.
        state.apply(view(1, 1, AuctionStatus.ACTIVE, 200_000, 20_000, 200_000, false, null,
                0, 65_000, 5_000), 1_000);

        assertEquals(4_000, state.serverOffsetMillis());
        assertEquals(60_000, state.remainingMillis(1_000));
        assertEquals(56_000, state.remainingMillis(5_000));
        assertEquals(1_000, state.remainingMillis(60_000));
        assertEquals(0, state.remainingMillis(61_000));
        assertEquals(0, state.remainingMillis(999_999), "Không bao giờ trả số âm");
    }

    @Test
    void giaToiThieuTruocVaSauBidDau() {
        RoomState state = new RoomState();
        state.apply(view(1, 1, AuctionStatus.ACTIVE, 200_000, 20_000, 200_000, false, null,
                NOW, NOW + 60_000, NOW), NOW);
        assertEquals(200_000, state.minimumNextBid(), "Chưa có bid: giá khởi điểm");

        state.apply(view(1, 2, AuctionStatus.ACTIVE, 200_000, 20_000, 220_000, true, "An",
                NOW, NOW + 60_000, NOW), NOW);
        assertEquals(240_000, state.minimumNextBid(), "Đã có bid: giá hiện tại + bước giá");
    }

    @Test
    void banCuaPhienKhacVanNhan() {
        RoomState state = new RoomState();
        state.apply(view(1, 10, AuctionStatus.ACTIVE, 200_000, 20_000, 300_000, true, "An",
                NOW, NOW + 60_000, NOW), NOW);

        assertTrue(state.apply(view(2, 1, AuctionStatus.SCHEDULED, 100_000, 10_000, 100_000, false, null,
                NOW, NOW + 60_000, NOW), NOW), "So version chỉ trong cùng một phiên");
        assertEquals(2, state.auctionId());
        assertEquals(1, state.version());
    }

    private static AuctionView view(long auctionId, long version, AuctionStatus status, long startingPrice,
            long minIncrement, long currentPrice, boolean hasBids, String leader, long startsAt, long endsAt,
            long serverTime) {
        return new AuctionView(auctionId, "Phiên " + auctionId, "Mô tả", status, startingPrice, minIncrement,
                currentPrice, hasBids, leader, null, startsAt, endsAt, 0, version, serverTime,
                1L, "Ban tổ chức", com.livebid.common.dto.Role.ADMIN, null);
    }
}
