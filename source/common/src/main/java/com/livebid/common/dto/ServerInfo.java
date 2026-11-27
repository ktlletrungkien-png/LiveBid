package com.livebid.common.dto;

/**
 * Kết quả SERVER_INFO.
 *
 * @param httpPort cổng HTTP của server (REST, ảnh); client ghép với địa chỉ đang kết nối TCP
 */
public record ServerInfo(int httpPort) {
}
