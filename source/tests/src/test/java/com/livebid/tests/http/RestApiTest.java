package com.livebid.tests.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.livebid.common.dto.Role;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.server.auth.AuthServiceImpl;
import com.livebid.server.auth.PasswordHasher;
import com.livebid.server.core.AuctionServiceImpl;
import com.livebid.server.core.AuctionSettings;
import com.livebid.server.core.BusinessExecutor;
import com.livebid.server.http.HttpGateway;
import com.livebid.server.model.NewUser;
import com.livebid.server.repo.memory.InMemoryAuctionRepository;
import com.livebid.server.repo.memory.InMemoryBidRepository;
import com.livebid.server.repo.memory.InMemorySettlementQueueRepository;
import com.livebid.server.repo.memory.InMemoryTransactionManager;
import com.livebid.server.repo.memory.InMemoryUserRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm tra REST API qua HTTP thật trên cổng ngẫu nhiên. Dựng đủ phụ thuộc thật bằng repository trong
 * bộ nhớ nên không cần SQLite, không cần server chạy sẵn, không cần mạng ngoài.
 */
class RestApiTest {

    private static final PasswordHasher HASHER = new PasswordHasher(1000);

    @TempDir
    Path webRoot;

    private BusinessExecutor executor;
    private AuthServiceImpl auth;
    private HttpGateway gateway;
    private InMemoryUserRepository usersRepo;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws IOException {
        executor = new BusinessExecutor();
        var bidsRepo = new InMemoryBidRepository();
        var auctionsRepo = new InMemoryAuctionRepository(bidsRepo);
        usersRepo = new InMemoryUserRepository();
        var service = new AuctionServiceImpl(executor, auctionsRepo, bidsRepo, usersRepo,
                new InMemorySettlementQueueRepository(), new InMemoryTransactionManager(),
                Clock.systemUTC(), AuctionSettings.defaults());
        auth = new AuthServiceImpl(executor, usersRepo, HASHER, Clock.systemUTC());
        Files.writeString(webRoot.resolve("index.html"), "<h1>LiveBid</h1>");
        gateway = new HttpGateway(new HttpGateway.Settings(0, webRoot), auth, service, Optional::empty);
        gateway.start();
    }

    @AfterEach
    void tearDown() {
        gateway.close();
        auth.close();
        executor.close();
    }

    // ---- Tài khoản ----

    @Test
    void registerReturnsCreatedWithTokenAndUser() throws Exception {
        Resp r = register("hoe_01", "matkhau123", "Hoè");

        assertEquals(201, r.status());
        JsonObject json = r.json();
        assertFalse(json.get("token").getAsString().isEmpty());
        assertEquals("hoe_01", json.getAsJsonObject("user").get("username").getAsString());
        assertEquals("USER", json.getAsJsonObject("user").get("role").getAsString());
    }

    @Test
    void duplicateRegisterIsConflict() throws Exception {
        register("hoe_01", "matkhau123", "Hoè");

        Resp r = register("HOE_01", "matkhau456", "Khác");

        assertEquals(409, r.status());
        assertEquals("USERNAME_TAKEN", errorCode(r));
    }

    @Test
    void invalidRegisterDataIsBadRequest() throws Exception {
        Resp r = register("ab", "matkhau123", "Tên");

        assertEquals(400, r.status());
        assertEquals("VALIDATION_ERROR", errorCode(r));
    }

    @Test
    void wrongLoginIsUnauthorized() throws Exception {
        register("hoe_01", "matkhau123", "Hoè");

        Resp r = call("POST", "/api/login", null, Map.of("username", "hoe_01", "password", "saimatkhau"));

        assertEquals(401, r.status());
        assertEquals("INVALID_CREDENTIALS", errorCode(r));
    }

    @Test
    void loginThenMeThenLogoutRevokesToken() throws Exception {
        register("hoe_01", "matkhau123", "Hoè");
        Resp login = call("POST", "/api/login", null, Map.of("username", "hoe_01", "password", "matkhau123"));
        assertEquals(200, login.status());
        String token = login.json().get("token").getAsString();

        Resp me = call("GET", "/api/me", token, null);
        assertEquals(200, me.status());
        assertEquals("hoe_01", me.json().get("username").getAsString());

        assertEquals(204, call("POST", "/api/logout", token, null).status());
        assertEquals(401, call("GET", "/api/me", token, null).status());
    }

    // ---- Xác thực ----

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        assertEquals(401, call("GET", "/api/auctions", null, null).status());
        assertEquals(401, call("GET", "/api/auctions/1", null, null).status(), "đường dẫn lồng nhau cũng phải bị chặn");
        assertEquals(401, call("GET", "/api/me", "token-la", null).status());
        assertEquals("UNAUTHORIZED", errorCode(call("GET", "/api/auctions", null, null)));
    }

    // ---- Phiên đấu giá ----

    @Test
    void adminCreatesAuctionAndUserCannot() throws Exception {
        String admin = adminToken();
        String user = userToken("nguoi_dung");

        Resp created = call("POST", "/api/auctions", admin, draft("Bàn phím cơ"));
        assertEquals(201, created.status());
        assertEquals("Bàn phím cơ", created.json().get("title").getAsString());
        assertEquals("SCHEDULED", created.json().get("status").getAsString());

        Resp forbidden = call("POST", "/api/auctions", user, draft("Không được"));
        assertEquals(403, forbidden.status());
        assertEquals("FORBIDDEN", errorCode(forbidden));
    }

    @Test
    void listAndDetailOfAuctions() throws Exception {
        String admin = adminToken();
        long id = call("POST", "/api/auctions", admin, draft("Đồng hồ")).json().get("auctionId").getAsLong();
        String user = userToken("nguoi_dung");

        Resp list = call("GET", "/api/auctions?status=SCHEDULED&page=1&pageSize=10", user, null);
        assertEquals(200, list.status());
        assertEquals(1, list.json().getAsJsonArray("items").size());
        assertFalse(list.json().get("hasMore").getAsBoolean());

        Resp detail = call("GET", "/api/auctions/" + id, user, null);
        assertEquals(200, detail.status());
        assertEquals("Đồng hồ", detail.json().get("title").getAsString());

        Resp bids = call("GET", "/api/auctions/" + id + "/bids", user, null);
        assertEquals(200, bids.status());
        assertEquals("[]", bids.body());
    }

    @Test
    void unknownAuctionIsNotFound() throws Exception {
        String user = userToken("nguoi_dung");

        Resp r = call("GET", "/api/auctions/999", user, null);

        assertEquals(404, r.status());
        assertEquals("AUCTION_NOT_FOUND", errorCode(r));
    }

    @Test
    void adminUpdatesAuctionAndStaleVersionConflicts() throws Exception {
        String admin = adminToken();
        JsonObject created = call("POST", "/api/auctions", admin, draft("Cũ")).json();
        long id = created.get("auctionId").getAsLong();
        long version = created.get("version").getAsLong();

        Resp ok = call("PUT", "/api/auctions/" + id, admin,
                Map.of("expectedVersion", version, "draft", draft("Mới")));
        assertEquals(200, ok.status());
        assertEquals("Mới", ok.json().get("title").getAsString());

        Resp stale = call("PUT", "/api/auctions/" + id, admin,
                Map.of("expectedVersion", version, "draft", draft("Lại")));
        assertEquals(409, stale.status());
        assertEquals("VERSION_CONFLICT", errorCode(stale));
    }

    @Test
    void myAuctionsRequiresValidFilter() throws Exception {
        String user = userToken("nguoi_dung");

        assertEquals(200, call("GET", "/api/me/auctions?filter=participated", user, null).status());
        assertEquals(200, call("GET", "/api/me/auctions?filter=WON", user, null).status());
        assertEquals(400, call("GET", "/api/me/auctions?filter=linh_tinh", user, null).status());
        assertEquals(400, call("GET", "/api/me/auctions", user, null).status());
    }

    @Test
    void badNumericParametersAreBadRequest() throws Exception {
        String user = userToken("nguoi_dung");

        assertEquals(400, call("GET", "/api/auctions/abc", user, null).status());
        assertEquals(400, call("GET", "/api/auctions?page=x", user, null).status());
        assertEquals(400, call("GET", "/api/auctions?status=KHONG_CO", user, null).status());
        assertEquals(400, call("GET", "/api/auctions?page=0", user, null).status());
    }

    // ---- Thống kê, JSON hỏng, file tĩnh ----

    @Test
    void statsIsServiceUnavailableWithoutSettlement() throws Exception {
        String user = userToken("nguoi_dung");

        Resp r = call("GET", "/api/stats", user, null);

        assertEquals(503, r.status());
        assertEquals("SERVER_BUSY", errorCode(r));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{hỏng"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        Resp r = new Resp(response.statusCode(), response.body());

        assertEquals(400, r.status());
        assertEquals("INVALID_MESSAGE", errorCode(r));
    }

    @Test
    void servesStaticFilesFromWebRoot() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/index.html")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("LiveBid"));
    }

    // ---- Tiện ích ----

    private record Resp(int status, String body) {
        JsonObject json() {
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + gateway.port() + path);
    }

    private Resp call(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JsonCodec.gson().toJson(body));
        HttpResponse<String> response = http.send(builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body());
    }

    private Resp register(String username, String password, String displayName) throws Exception {
        return call("POST", "/api/register", null,
                Map.of("username", username, "password", password, "displayName", displayName));
    }

    private String userToken(String username) throws Exception {
        return register(username, "matkhau123", "Người dùng").json().get("token").getAsString();
    }

    /** Tạo ADMIN trực tiếp trong repository (đăng ký công khai luôn là USER), rồi đăng nhập qua REST. */
    private String adminToken() throws Exception {
        PasswordHasher.HashedPassword hashed = HASHER.hash("matkhauadmin".toCharArray());
        usersRepo.insert(new NewUser("admin", "Quản trị", hashed.hash(), hashed.salt(), hashed.iterations(),
                Role.ADMIN, System.currentTimeMillis()));
        Resp login = call("POST", "/api/login", null, Map.of("username", "admin", "password", "matkhauadmin"));
        assertEquals(200, login.status());
        return login.json().get("token").getAsString();
    }

    private static Map<String, Object> draft(String title) {
        long now = System.currentTimeMillis();
        return Map.of("title", title, "description", "Mô tả", "startingPrice", 200_000L,
                "minIncrement", 20_000L, "startsAt", now + 600_000L, "endsAt", now + 900_000L);
    }

    private static String errorCode(Resp r) {
        return r.json().getAsJsonObject("error").get("code").getAsString();
    }
}
