package com.livebid.server.tcp;

import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.ProtocolException;
import com.livebid.server.core.ClientChannel;
import com.livebid.server.core.ServiceException;
import com.livebid.server.core.UserPrincipal;

/**
 * Các bước kiểm tra dùng chung của handler lệnh TCP. Danh tính luôn lấy từ {@link ClientChannel#principal()}
 * (do server gán sau LOGIN), không bao giờ từ dữ liệu client gửi lên.
 */
final class CommandSupport {

    private CommandSupport() {
    }

    /** Lệnh cần đăng nhập: chưa đăng nhập thì {@code UNAUTHORIZED}. */
    static UserPrincipal requireLogin(ClientChannel session) {
        UserPrincipal user = session.principal();
        if (user == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED, "Cần đăng nhập trước");
        }
        return user;
    }

    /** Lệnh quản trị: đã đăng nhập nhưng không phải ADMIN thì {@code FORBIDDEN}. */
    static UserPrincipal requireAdmin(ClientChannel session) {
        UserPrincipal user = requireLogin(session);
        if (!user.isAdmin()) {
            throw new ServiceException(ErrorCode.FORBIDDEN, "Chỉ ADMIN được dùng lệnh này");
        }
        return user;
    }

    /** Đọc payload bắt buộc; thiếu hoặc sai cấu trúc thì {@code INVALID_MESSAGE}. */
    static <T> T payload(Message request, Class<T> type) throws ProtocolException {
        T value = request.payloadAs(type);
        if (value == null) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "Thiếu payload cho lệnh " + request.type());
        }
        return value;
    }
}
