package com.livebid.common.payload;

/** REGISTER: username 3–30 ký tự [a-zA-Z0-9_], password ≥ 8 ký tự, displayName hỗ trợ tiếng Việt. */
public record RegisterPayload(String username, String password, String displayName) {
}
