package com.livebid.common.payload;

/** AUTH (chỉ WebSocket): token nhận được từ REST /api/login. */
public record AuthPayload(String token) {
}
