/**
 * Tầng TCP cho desktop client. <b>Phụ trách: Lê Trung Kiên.</b>
 *
 * <ul>
 *   <li>{@code TcpServer}: mở cổng, nhận kết nối, giới hạn số kết nối.</li>
 *   <li>{@code ClientSession}: một kết nối; luồng đọc riêng, hàng đợi gửi có giới hạn + một luồng ghi.</li>
 *   <li>{@code LineFrameDecoder}: đóng khung JSON một dòng kết thúc {@code '\n'}, tối đa 64 KiB.</li>
 *   <li>{@code MessageRouter}: kiểm tra phong bì, chuyển lệnh tới handler, đổi lỗi thành RESPONSE.</li>
 * </ul>
 */
package com.livebid.server.tcp;
