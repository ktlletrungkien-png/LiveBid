package com.livebid.client.net;

import com.livebid.common.protocol.ErrorInfo;
import java.io.IOException;
import java.util.Objects;

/**
 * Lỗi nghiệp vụ server trả về trong RESPONSE {@code success=false}.
 *
 * <p>Đây là lỗi "mong đợi" (sai mật khẩu, giá quá thấp, chưa đăng nhập...), khác với lỗi mạng. Giao diện
 * hiển thị nguyên {@link ErrorInfo#message()} cho người dùng và dùng {@link ErrorInfo#code()} khi cần xử lý
 * riêng (ví dụ {@code BID_TOO_LOW} thì đọc {@code details.minimumAllowed}).
 */
public class RemoteErrorException extends IOException {

    private static final long serialVersionUID = 1L;

    private final transient ErrorInfo error;

    public RemoteErrorException(ErrorInfo error) {
        super(Objects.requireNonNull(error, "error").message());
        this.error = error;
    }

    /** Mã lỗi, thông báo tiếng Việt và dữ liệu thêm do server gửi. */
    public ErrorInfo error() {
        return error;
    }
}
