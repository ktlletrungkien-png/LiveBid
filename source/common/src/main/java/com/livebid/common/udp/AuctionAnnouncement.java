package com.livebid.common.udp;

/**
 * Gói multicast (239.255.42.99:5002) gửi khi một phiên còn 5 phút nữa bắt đầu.
 * Chỉ để hiển thị thông báo; dữ liệu chuẩn luôn lấy lại qua TCP/REST.
 */
public record AuctionAnnouncement(long auctionId, String title, long startsAt) {
}
