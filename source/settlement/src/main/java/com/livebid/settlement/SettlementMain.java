package com.livebid.settlement;

import com.livebid.common.Protocol;

/**
 * Điểm khởi động dịch vụ Settlement (RMI).
 *
 * TODO (tuần 5): định nghĩa interface SettlementService trong module common,
 * cài đặt ở đây, đăng ký vào RMI Registry, ghi biên bản và cộng dồn thống kê.
 */
public final class SettlementMain {

    public static void main(String[] args) {
        System.out.println("LiveBid settlement - RMI registry " + Protocol.DEFAULT_RMI_PORT
                + ", name " + Protocol.RMI_SETTLEMENT_NAME + " (not implemented yet)");
    }
}
