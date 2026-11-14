package com.livebid.server.http;

import com.google.gson.JsonParseException;
import com.livebid.common.dto.AuctionDraft;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.LoginResult;
import com.livebid.common.dto.MyAuctionFilter;
import com.livebid.common.payload.LoginPayload;
import com.livebid.common.payload.RegisterPayload;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.rmi.SettlementSummaryDto;
import com.livebid.server.core.AuctionService;
import com.livebid.server.core.AuthService;
import com.livebid.server.core.ServiceException;
import com.livebid.server.core.UserPrincipal;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cổng HTTP của server: REST API dưới {@code /api} và phục vụ file tĩnh của trang web.
 * Chạy cùng tiến trình với Auction Server và gọi cùng một {@link AuctionService}; không chứa logic đấu giá.
 *
 * <p>Quy ước:
 * <ul>
 *   <li>Thành công: body là DTO trực tiếp, không bọc phong bì {@code Message}.</li>
 *   <li>Lỗi: mã HTTP lấy từ {@link ErrorCode#httpStatus()}, body
 *       {@code {"error":{"code":"...","message":"...","details":{...}}}}.</li>
 *   <li>Xác thực: header {@code Authorization: Bearer <token>} cho mọi {@code /api/*} trừ
 *       {@code register} và {@code login}.</li>
 *   <li>Kết quả bất đồng bộ được trả bằng {@code ctx.future(...)}; <b>không</b> {@code join()} trên luồng HTTP.</li>
 * </ul>
 */
public final class HttpGateway implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HttpGateway.class);

    private static final String JSON = "application/json; charset=utf-8";
    private static final String ATTR_PRINCIPAL = "principal";
    private static final String ATTR_TOKEN = "token";
    private static final String BEARER = "Bearer ";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_BID_LIMIT = 20;

    /**
     * @param port    cổng HTTP; 0 = cổng ngẫu nhiên (dùng cho test)
     * @param webRoot thư mục chứa trang web tĩnh, phục vụ tại {@code /}; không tồn tại thì chỉ chạy API
     */
    public record Settings(int port, Path webRoot) {
    }

    /** Body của {@code PUT /api/auctions/{id}}; id lấy từ đường dẫn. */
    private record UpdateBody(long expectedVersion, AuctionDraft draft) {
    }

    private final Settings settings;
    private final AuthService auth;
    private final AuctionService auctions;
    private final Supplier<Optional<SettlementSummaryDto>> stats;
    private Javalin app;

    /**
     * @param stats nguồn thống kê của Settlement; trả rỗng khi dịch vụ chưa sẵn sàng. Có thể chặn (gọi RMI),
     *              được gọi trên luồng HTTP chứ không phải executor nghiệp vụ.
     */
    public HttpGateway(Settings settings, AuthService auth, AuctionService auctions,
                       Supplier<Optional<SettlementSummaryDto>> stats) {
        this.settings = Objects.requireNonNull(settings);
        this.auth = Objects.requireNonNull(auth);
        this.auctions = Objects.requireNonNull(auctions);
        this.stats = Objects.requireNonNull(stats);
    }

    /** Mở cổng và bắt đầu phục vụ. */
    public void start() {
        if (app != null) {
            throw new IllegalStateException("HttpGateway đã khởi động");
        }
        Path webRoot = settings.webRoot();
        boolean serveStatic = webRoot != null && Files.isDirectory(webRoot);
        if (!serveStatic) {
            log.warn("Không tìm thấy thư mục web {}, chỉ phục vụ API", webRoot);
        }
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            if (serveStatic) {
                config.staticFiles.add(webRoot.toAbsolutePath().toString(), Location.EXTERNAL);
            }
        });
        registerRoutes(app);
        app.start(settings.port());
        log.info("HTTP/REST lắng nghe tại cổng {}", app.port());
    }

    /** Cổng thực tế sau {@link #start()} (hữu ích khi cấu hình cổng 0). */
    public int port() {
        if (app == null) {
            throw new IllegalStateException("HttpGateway chưa khởi động");
        }
        return app.port();
    }

    @Override
    public void close() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    // ---------------------------------------------------------------- định tuyến

    private void registerRoutes(Javalin app) {
        app.before("/api/*", this::authenticate);

        app.post("/api/register", this::register);
        app.post("/api/login", this::login);
        app.post("/api/logout", this::logout);
        app.get("/api/me", this::me);
        app.get("/api/me/auctions", this::myAuctions);
        app.get("/api/auctions", this::listAuctions);
        app.post("/api/auctions", this::createAuction);
        app.get("/api/auctions/{id}", this::getAuction);
        app.put("/api/auctions/{id}", this::updateAuction);
        app.get("/api/auctions/{id}/bids", this::bidHistory);
        app.get("/api/stats", this::stats);

        // Lỗi đồng bộ (JSON hỏng, tham số sai, chưa đăng nhập...) đều đổi thành JSON lỗi thống nhất.
        app.exception(Exception.class, (e, ctx) -> writeError(ctx, e));
    }

    /** Lọc xác thực cho mọi {@code /api/*} trừ đăng ký và đăng nhập. */
    private void authenticate(Context ctx) {
        String path = ctx.path();
        if (path.equals("/api/register") || path.equals("/api/login")) {
            return;
        }
        String header = ctx.header("Authorization");
        String token = header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()).trim() : null;
        Optional<UserPrincipal> principal = token == null ? Optional.empty() : auth.resolveToken(token);
        if (principal.isEmpty()) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED, "Bạn cần đăng nhập");
        }
        ctx.attribute(ATTR_PRINCIPAL, principal.get());
        ctx.attribute(ATTR_TOKEN, token);
    }

    // ---------------------------------------------------------------- tài khoản

    private void register(Context ctx) {
        RegisterPayload p = body(ctx, RegisterPayload.class);
        respond(ctx, 201, auth.register(p.username(), p.password(), p.displayName()).thenApply(this::loginResult));
    }

    private void login(Context ctx) {
        LoginPayload p = body(ctx, LoginPayload.class);
        respond(ctx, 200, auth.login(p.username(), p.password()).thenApply(this::loginResult));
    }

    private void logout(Context ctx) {
        auth.revokeToken(ctx.attribute(ATTR_TOKEN));
        ctx.status(204);
    }

    private void me(Context ctx) {
        respondNow(ctx, 200, principal(ctx).toView());
    }

    private LoginResult loginResult(UserPrincipal user) {
        return new LoginResult(user.toView(), auth.issueToken(user));
    }

    // ---------------------------------------------------------------- phiên đấu giá

    private void listAuctions(Context ctx) {
        AuctionStatus status = enumParam(ctx, "status", AuctionStatus.class);
        int page = intParam(ctx, "page", DEFAULT_PAGE);
        int pageSize = intParam(ctx, "pageSize", DEFAULT_PAGE_SIZE);
        respond(ctx, 200, auctions.listAuctions(status, page, pageSize));
    }

    private void getAuction(Context ctx) {
        respond(ctx, 200, auctions.getAuction(auctionId(ctx)));
    }

    private void bidHistory(Context ctx) {
        long id = auctionId(ctx);
        Long before = longParam(ctx, "before");
        int limit = intParam(ctx, "limit", DEFAULT_BID_LIMIT);
        respond(ctx, 200, auctions.getBidHistory(id, before, limit));
    }

    private void myAuctions(Context ctx) {
        MyAuctionFilter filter = enumParam(ctx, "filter", MyAuctionFilter.class);
        if (filter == null) {
            throw validation("Thiếu tham số filter (participated hoặc won)");
        }
        respond(ctx, 200, auctions.getMyAuctions(principal(ctx), filter));
    }

    private void createAuction(Context ctx) {
        AuctionDraft draft = body(ctx, AuctionDraft.class);
        respond(ctx, 201, auctions.createAuction(principal(ctx), draft));
    }

    private void updateAuction(Context ctx) {
        long id = auctionId(ctx);
        UpdateBody body = body(ctx, UpdateBody.class);
        respond(ctx, 200, auctions.updateAuction(principal(ctx), id, body.expectedVersion(), body.draft()));
    }

    private void stats(Context ctx) {
        Optional<SettlementSummaryDto> summary = stats.get();
        if (summary.isEmpty()) {
            throw new ServiceException(ErrorCode.SERVER_BUSY, "Dịch vụ thống kê chưa sẵn sàng");
        }
        respondNow(ctx, 200, summary.get());
    }

    // ---------------------------------------------------------------- đọc yêu cầu

    private static UserPrincipal principal(Context ctx) {
        UserPrincipal principal = ctx.attribute(ATTR_PRINCIPAL);
        if (principal == null) {
            throw new ServiceException(ErrorCode.UNAUTHORIZED, "Bạn cần đăng nhập");
        }
        return principal;
    }

    private static <T> T body(Context ctx, Class<T> type) {
        try {
            T value = JsonCodec.gson().fromJson(ctx.body(), type);
            if (value == null) {
                throw new ServiceException(ErrorCode.INVALID_MESSAGE, "Thiếu nội dung JSON");
            }
            return value;
        } catch (JsonParseException e) {
            throw new ServiceException(ErrorCode.INVALID_MESSAGE, "Nội dung JSON không hợp lệ");
        }
    }

    private static long auctionId(Context ctx) {
        try {
            return Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw validation("Mã phiên không hợp lệ");
        }
    }

    private static int intParam(Context ctx, String name, int defaultValue) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw validation("Tham số " + name + " phải là số nguyên");
        }
    }

    private static Long longParam(Context ctx, String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw validation("Tham số " + name + " phải là số nguyên");
        }
    }

    /** Đọc tham số kiểu enum, không phân biệt hoa thường; không có tham số thì trả {@code null}. */
    private static <E extends Enum<E>> E enumParam(Context ctx, String name, Class<E> type) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw validation("Giá trị không hợp lệ cho tham số " + name + ": " + raw);
        }
    }

    private static ServiceException validation(String message) {
        return new ServiceException(ErrorCode.VALIDATION_ERROR, message);
    }

    // ---------------------------------------------------------------- ghi phản hồi

    /** Trả kết quả bất đồng bộ: không chặn luồng HTTP, lỗi đổi thành JSON lỗi. */
    private <T> void respond(Context ctx, int status, CompletableFuture<T> future) {
        ctx.future(() -> future.handle((value, error) -> {
            if (error != null) {
                writeError(ctx, error);
            } else {
                respondNow(ctx, status, value);
            }
            return null;
        }));
    }

    private static void respondNow(Context ctx, int status, Object value) {
        ctx.status(status).contentType(JSON).result(JsonCodec.gson().toJson(value));
    }

    private static void writeError(Context ctx, Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        Map<String, Object> info = new LinkedHashMap<>();
        int status;
        if (cause instanceof ServiceException se) {
            status = se.code().httpStatus();
            info.put("code", se.code().name());
            info.put("message", se.getMessage());
            if (se.details() != null) {
                info.put("details", se.details());
            }
        } else {
            // Lỗi không lường trước: ghi log đầy đủ, không gửi stack trace cho client.
            log.error("Lỗi không mong đợi khi xử lý {} {}", ctx.method(), ctx.path(), cause);
            status = ErrorCode.INTERNAL_ERROR.httpStatus();
            info.put("code", ErrorCode.INTERNAL_ERROR.name());
            info.put("message", "Lỗi hệ thống, vui lòng thử lại sau");
        }
        respondNow(ctx, status, Map.of("error", info));
    }
}
