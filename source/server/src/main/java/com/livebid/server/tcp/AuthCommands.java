package com.livebid.server.tcp;

import static com.livebid.server.tcp.CommandSupport.payload;
import static com.livebid.server.tcp.CommandSupport.requireLogin;

import com.livebid.common.dto.LoginResult;
import com.livebid.common.payload.LoginPayload;
import com.livebid.common.payload.RegisterPayload;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageType;
import com.livebid.common.protocol.ProtocolException;
import com.livebid.server.core.AuctionService;
import com.livebid.server.core.AuthService;
import com.livebid.server.core.ServiceException;
import com.livebid.server.core.UserPrincipal;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * REGISTER, LOGIN, LOGOUT trên kết nối TCP. Kiểm tra tài khoản do {@link AuthService} (Hoè) làm; lớp này chỉ
 * gắn kết quả vào kết nối: đăng nhập xong thì {@link ClientSession#setPrincipal} giữ người dùng cho các lệnh sau.
 *
 * <p>Một kết nối chỉ đại diện cho một tài khoản. Đang đăng nhập mà REGISTER/LOGIN lại bị từ chối, phải
 * LOGOUT trước, để không ai "đổi người" giữa chừng khi còn đứng trong phòng đấu giá.
 *
 * <p>Qua TCP không phát token ({@link LoginResult#token()} = null): kết nối tự giữ trạng thái đăng nhập.
 */
public final class AuthCommands {

    private final AuthService auth;
    private final AuctionService auctions;

    public AuthCommands(AuthService auth, AuctionService auctions) {
        this.auth = Objects.requireNonNull(auth);
        this.auctions = Objects.requireNonNull(auctions);
    }

    public void registerTo(MessageRouter router) {
        router.register(MessageType.REGISTER, this::register);
        router.register(MessageType.LOGIN, this::login);
        router.register(MessageType.LOGOUT, this::logout);
    }

    private CompletableFuture<LoginResult> register(ClientSession session, Message request)
            throws ProtocolException {
        requireLoggedOut(session);
        RegisterPayload p = payload(request, RegisterPayload.class);
        return auth.register(p.username(), p.password(), p.displayName())
                .thenApply(user -> signIn(session, user));
    }

    private CompletableFuture<LoginResult> login(ClientSession session, Message request)
            throws ProtocolException {
        requireLoggedOut(session);
        LoginPayload p = payload(request, LoginPayload.class);
        return auth.login(p.username(), p.password()).thenApply(user -> signIn(session, user));
    }

    /** Rời mọi phòng rồi bỏ đăng nhập. Việc rời phòng đi qua executor nghiệp vụ nên đúng thứ tự với bid. */
    private CompletableFuture<Void> logout(ClientSession session, Message request) {
        requireLogin(session);
        session.setPrincipal(null);
        auctions.onDisconnect(session);
        return CompletableFuture.completedFuture(null);
    }

    private static void requireLoggedOut(ClientSession session) {
        if (session.principal() != null) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR,
                    "Kết nối này đã đăng nhập, hãy LOGOUT trước");
        }
    }

    private static LoginResult signIn(ClientSession session, UserPrincipal user) {
        session.setPrincipal(user);
        return new LoginResult(user.toView(), null);
    }
}
