package com.livebid.client;

import com.livebid.common.Protocol;

/**
 * Điểm khởi động Desktop client (Swing).
 *
 * TODO: màn hình Kết nối (tìm server bằng UDP / nhập IP), Đăng nhập, Danh sách phiên,
 * Phòng đấu giá, Lịch sử cá nhân, Quản trị. Mọi tác vụ mạng chạy ngoài luồng giao diện.
 */
public final class ClientMain {

    public static void main(String[] args) {
        System.out.println("LiveBid desktop client - protocol v" + Protocol.PROTOCOL_VERSION
                + " (not implemented yet)");
    }
}
