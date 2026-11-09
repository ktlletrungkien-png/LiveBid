package com.livebid.common.protocol;

import com.google.gson.JsonObject;

/**
 * Phần {@code error} của RESPONSE khi {@code success=false}.
 *
 * @param code    mã lỗi máy đọc được
 * @param message câu thông báo cho người dùng (tiếng Việt)
 * @param details dữ liệu thêm, có thể {@code null}; ví dụ {@code {"minimumAllowed":300000}}
 */
public record ErrorInfo(ErrorCode code, String message, JsonObject details) {
}
