package com.livebid.common.dto;

/**
 * Kết quả REQUEST_UPLOAD_TICKET: vé tải <b>một</b> ảnh lên {@code POST /api/images}, gửi trong header
 * {@link com.livebid.common.Protocol#UPLOAD_TICKET_HEADER}. Vé gắn với người xin vé, dùng được một lần và
 * hết hạn sau ít phút.
 *
 * @param ticket    chuỗi ngẫu nhiên
 * @param expiresAt thời điểm hết hạn (epoch ms, giờ server)
 */
public record UploadTicket(String ticket, long expiresAt) {
}
