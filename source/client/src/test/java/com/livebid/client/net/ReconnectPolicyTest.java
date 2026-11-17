package com.livebid.client.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Kiểm tra dãy thời gian chờ và việc dừng sau số lần thử tối đa của {@link ReconnectPolicy}. */
class ReconnectPolicyTest {

    @Test
    void delayTangGapDoiRoiGiuOMucToiDa() {
        assertEquals(1_000, ReconnectPolicy.delayFor(1));
        assertEquals(2_000, ReconnectPolicy.delayFor(2));
        assertEquals(4_000, ReconnectPolicy.delayFor(3));
        assertEquals(8_000, ReconnectPolicy.delayFor(4));
        assertEquals(10_000, ReconnectPolicy.delayFor(5));
        assertEquals(10_000, ReconnectPolicy.delayFor(6));
        assertEquals(10_000, ReconnectPolicy.delayFor(1_000));
    }

    @Test
    void delayForTuChoiSoLanThuKhongHopLe() {
        assertThrows(IllegalArgumentException.class, () -> ReconnectPolicy.delayFor(0));
        assertThrows(IllegalArgumentException.class, () -> ReconnectPolicy.delayFor(-3));
    }

    @Test
    void nextDelayMillisTraDungThuTu() {
        ReconnectPolicy policy = new ReconnectPolicy();
        assertEquals(1_000, policy.nextDelayMillis());
        assertEquals(2_000, policy.nextDelayMillis());
        assertEquals(4_000, policy.nextDelayMillis());
        assertEquals(3, policy.attempts());
    }

    @Test
    void dungLaiSauSoLanThuToiDa() {
        ReconnectPolicy policy = new ReconnectPolicy();
        for (int i = 0; i < ReconnectPolicy.MAX_ATTEMPTS; i++) {
            assertFalse(policy.exhausted(), "chưa hết lượt ở lần " + i);
            policy.nextDelayMillis();
        }
        assertTrue(policy.exhausted());
        assertEquals(ReconnectPolicy.MAX_ATTEMPTS, policy.attempts());
    }

    @Test
    void resetXoaBoDem() {
        ReconnectPolicy policy = new ReconnectPolicy();
        policy.nextDelayMillis();
        policy.nextDelayMillis();
        policy.reset();
        assertEquals(0, policy.attempts());
        assertFalse(policy.exhausted());
        assertEquals(1_000, policy.nextDelayMillis());
    }
}
