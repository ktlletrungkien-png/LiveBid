package com.livebid.tests.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.HashMap;
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

    @TempDir
    Path imageDir;

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
        gateway = new HttpGateway(new HttpGateway.Settings(0, webRoot, imageDir), auth, service, Optional::empty);
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
    void adminCreatesScheduledAndUserCreatesPendingWithLeadTime() throws Exception {
        String admin = adminToken();
        String user = userToken("nguoi_dung");

        Resp created = call("POST", "/api/auctions", admin, draft("Bàn phím cơ"));
        assertEquals(201, created.status());
        assertEquals("Bàn phím cơ", created.json().get("title").getAsString());
        assertEquals("SCHEDULED", created.json().get("status").getAsString());

        Resp tooSoon = call("POST", "/api/auctions", user, draft("Quá gấp"));
        assertEquals(400, tooSoon.status());
        assertEquals("VALIDATION_ERROR", errorCode(tooSoon));

        Resp userCreated = call("POST", "/api/auctions", user, userDraft("User đăng hợp lệ"));
        assertEquals(201, userCreated.status());
        assertEquals("PENDING_APPROVAL", userCreated.json().get("status").getAsString());
    }

    @Test
    void userApprovalWorkflowOverRest() throws Exception {
        String admin = adminToken();
        String owner = userToken("chu_phien");
        String stranger = userToken("nguoi_la");

        // User đăng phiên -> PENDING_APPROVAL
        JsonObject created = call("POST", "/api/auctions", owner, userDraft("Đồng hồ cổ")).json();
        long id = created.get("auctionId").getAsLong();
        long version = created.get("version").getAsLong();
        assertEquals("PENDING_APPROVAL", created.get("status").getAsString());

        // Không lộ trong danh sách công khai; người lạ xem chi tiết -> 404
        assertEquals(0, call("GET", "/api/auctions", stranger, null).json().getAsJsonArray("items").size());
        assertEquals(404, call("GET", "/api/auctions/" + id, stranger, null).status());
        assertEquals(200, call("GET", "/api/auctions/" + id, owner, null).status());

        // User không được xem hàng chờ duyệt
        assertEquals(403, call("GET", "/api/auctions/pending", owner, null).status());
        assertEquals(1, call("GET", "/api/auctions/pending", admin, null).status() == 200 ? 1 : 0);

        // Chủ phiên sửa khi đang chờ duyệt
        Resp updated = call("PUT", "/api/auctions/" + id, owner,
                Map.of("expectedVersion", version, "draft", userDraft("Đồng hồ cổ sửa")));
        assertEquals(200, updated.status());
        assertEquals("Đồng hồ cổ sửa", updated.json().get("title").getAsString());

        // ADMIN duyệt -> SCHEDULED, lộ ở danh sách công khai
        Resp approved = call("POST", "/api/auctions/" + id + "/approve", admin, null);
        assertEquals(200, approved.status());
        assertEquals("SCHEDULED", approved.json().get("status").getAsString());
        assertEquals(1, call("GET", "/api/auctions", stranger, null).json().getAsJsonArray("items").size());

        // Duyệt lại phiên đã duyệt -> 409 AUCTION_NOT_PENDING
        Resp approveAgain = call("POST", "/api/auctions/" + id + "/approve", admin, null);
        assertEquals(409, approveAgain.status());
        assertEquals("AUCTION_NOT_PENDING", errorCode(approveAgain));

        // User đăng phiên thứ 2 -> ADMIN từ chối; đăng phiên thứ 3 -> tự hủy (204)
        long id2 = call("POST", "/api/auctions", owner, userDraft("Phiên từ chối")).json()
                .get("auctionId").getAsLong();
        Resp rejected = call("POST", "/api/auctions/" + id2 + "/reject", admin, Map.of("reason", "Thiếu mô tả"));
        assertEquals(200, rejected.status());
        assertEquals("REJECTED", rejected.json().get("status").getAsString());
        assertEquals("Thiếu mô tả", rejected.json().get("rejectReason").getAsString());

        long id3 = call("POST", "/api/auctions", owner, userDraft("Phiên tự hủy")).json()
                .get("auctionId").getAsLong();
        assertEquals(204, call("DELETE", "/api/auctions/" + id3, owner, null).status());
        assertEquals(404, call("GET", "/api/auctions/" + id3, owner, null).status());
        assertEquals(200, call("GET", "/api/me/auctions?filter=created", owner, null).status());
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

    // ---- Ảnh đại diện ----

    /** Chữ ký PNG + vài byte: đủ để server nhận là PNG. */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H'};

    @Test
    void uploadImageThenServeItWithoutToken() throws Exception {
        String user = userToken("nguoi_dung");

        Resp uploaded = upload(user, PNG);
        assertEquals(201, uploaded.status());
        String imageId = uploaded.json().get("imageId").getAsString();
        assertTrue(imageId.matches("[0-9a-f]{32}\\.png"), imageId);

        // <img> không gửi được token nên GET ảnh là công khai
        HttpResponse<byte[]> image = http.send(HttpRequest.newBuilder(uri("/api/images/" + imageId)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, image.statusCode());
        assertEquals("image/png", image.headers().firstValue("Content-Type").orElse(""));
        assertArrayEquals(PNG, image.body());

        assertEquals(404, call("GET", "/api/images/" + "0".repeat(32) + ".png", null, null).status());
        assertEquals(404, call("GET", "/api/images/..%2Fauction.db", null, null).status());
    }

    @Test
    void rejectsInvalidImageUploads() throws Exception {
        String user = userToken("nguoi_dung");

        assertEquals(401, upload(null, PNG).status());

        Resp notImage = upload(user, "<script>alert(1)</script>".getBytes());
        assertEquals(400, notImage.status());
        assertEquals("VALIDATION_ERROR", errorCode(notImage));

        byte[] tooBig = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, tooBig, 0, PNG.length);
        assertEquals(400, upload(user, tooBig).status());

        assertEquals(400, upload(user, new byte[0]).status());
    }

    @Test
    void auctionImageIsKeptReplacedAndRemovedOnUpdate() throws Exception {
        String admin = adminToken();
        String first = upload(admin, PNG).json().get("imageId").getAsString();
        String second = upload(admin, PNG).json().get("imageId").getAsString();

        Map<String, Object> withImage = new HashMap<>(draft("Tranh sơn dầu"));
        withImage.put("imageId", first);
        JsonObject created = call("POST", "/api/auctions", admin, withImage).json();
        long id = created.get("auctionId").getAsLong();
        assertEquals(first, created.get("imageId").getAsString());

        // Không gửi imageId (như client Swing) -> giữ ảnh cũ
        JsonObject kept = update(admin, id, created.get("version").getAsLong(), draft("Tranh sơn dầu"));
        assertEquals(first, kept.get("imageId").getAsString());

        Map<String, Object> replace = new HashMap<>(draft("Tranh sơn dầu"));
        replace.put("imageId", second);
        JsonObject replaced = update(admin, id, kept.get("version").getAsLong(), replace);
        assertEquals(second, replaced.get("imageId").getAsString());

        Map<String, Object> remove = new HashMap<>(draft("Tranh sơn dầu"));
        remove.put("removeImage", true);
        JsonObject removed = update(admin, id, replaced.get("version").getAsLong(), remove);
        assertFalse(removed.has("imageId") && !removed.get("imageId").isJsonNull());
    }

    @Test
    void cannotAttachUnknownOrMalformedImage() throws Exception {
        String admin = adminToken();

        Map<String, Object> unknown = new HashMap<>(draft("Phiên"));
        unknown.put("imageId", "0".repeat(32) + ".png");
        Resp r1 = call("POST", "/api/auctions", admin, unknown);
        assertEquals(400, r1.status());
        assertEquals("VALIDATION_ERROR", errorCode(r1));

        Map<String, Object> malformed = new HashMap<>(draft("Phiên"));
        malformed.put("imageId", "../../data/auction.db");
        assertEquals(400, call("POST", "/api/auctions", admin, malformed).status());
    }

    private Resp upload(String token, byte[] data) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/images"))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(data));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body());
    }

    private JsonObject update(String token, long id, long version, Map<String, Object> draft) throws Exception {
        Resp r = call("PUT", "/api/auctions/" + id, token, Map.of("expectedVersion", version, "draft", draft));
        assertEquals(200, r.status(), r.body());
        return r.json();
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

    private static Map<String, Object> userDraft(String title) {
        long now = System.currentTimeMillis();
        return Map.of("title", title, "description", "Mô tả", "startingPrice", 200_000L,
                "minIncrement", 20_000L, "startsAt", now + 2_400_000L, "endsAt", now + 3_600_000L);
    }

    private static String errorCode(Resp r) {
        return r.json().getAsJsonObject("error").get("code").getAsString();
    }
}
