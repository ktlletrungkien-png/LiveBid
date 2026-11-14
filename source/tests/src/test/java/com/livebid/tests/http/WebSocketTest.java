package com.livebid.tests.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.Role;
import com.livebid.common.payload.AuctionIdPayload;
import com.livebid.common.payload.AuthPayload;
import com.livebid.common.payload.PlaceBidPayload;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageKind;
import com.livebid.common.protocol.MessageType;
import com.livebid.common.protocol.ProtocolException;
import com.livebid.server.auth.AuthServiceImpl;
import com.livebid.server.auth.PasswordHasher;
import com.livebid.server.core.AuctionServiceImpl;
import com.livebid.server.core.AuctionSettings;
import com.livebid.server.core.BusinessExecutor;
import com.livebid.server.core.UserPrincipal;
import com.livebid.server.http.HttpGateway;
import com.livebid.server.repo.memory.InMemoryAuctionRepository;
import com.livebid.server.repo.memory.InMemoryBidRepository;
import com.livebid.server.repo.memory.InMemorySettlementQueueRepository;
import com.livebid.server.repo.memory.InMemoryTransactionManager;
import com.livebid.server.repo.memory.InMemoryUserRepository;
import com.livebid.server.tcp.AuctionCommands;
import com.livebid.server.tcp.AuthCommands;
import com.livebid.server.tcp.MessageRouter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kiểm tra WebSocket {@code /ws} bằng client WebSocket của JDK trên cổng ngẫu nhiên. Dùng đúng
 * {@code MessageRouter} và các handler như TCP, nên cùng một bộ lệnh chạy được trên cả hai đường.
 */
class WebSocketTest {

    private static final long WAIT_SECONDS = 5;

    @TempDir
    Path webRoot;

    private BusinessExecutor executor;
    private AuctionServiceImpl service;
    private AuthServiceImpl auth;
    private HttpGateway gateway;
    private final HttpClient http = HttpClient.newHttpClient();
    private final List<String> disconnected = new CopyOnWriteArrayList<>();
    private final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void setUp() {
        executor = new BusinessExecutor();
        var bidsRepo = new InMemoryBidRepository();
        var usersRepo = new InMemoryUserRepository();
        service = new AuctionServiceImpl(executor, new InMemoryAuctionRepository(bidsRepo), bidsRepo, usersRepo,
                new InMemorySettlementQueueRepository(), new InMemoryTransactionManager(),
                Clock.systemUTC(), AuctionSettings.defaults());
        auth = new AuthServiceImpl(executor, usersRepo, new PasswordHasher(1000), Clock.systemUTC());

        MessageRouter router = new MessageRouter(Clock.systemUTC());
        new AuthCommands(auth, service).registerTo(router);
        new AuctionCommands(service).registerTo(router);

        gateway = new HttpGateway(new HttpGateway.Settings(0, webRoot), auth, service, Optional::empty);
        gateway.enableWebSocket(router, channel -> {
            disconnected.add(channel.id());
            service.onDisconnect(channel);
        });
        gateway.start();
    }

    @AfterEach
    void tearDown() {
        clients.forEach(TestClient::closeQuietly);
        gateway.close();
        auth.close();
        executor.close();
    }

    @Test
    void pingReturnsServerTimeWithoutLogin() throws Exception {
        TestClient client = connect();

        Message response = client.request(MessageType.PING, null);

        assertTrue(response.isSuccess());
        assertTrue(response.payload().getAsJsonObject().get("serverTime").getAsLong() > 0);
    }

    @Test
    void placeBidBeforeAuthIsUnauthorized() throws Exception {
        TestClient client = connect();

        Message response = client.request(MessageType.PLACE_BID, new PlaceBidPayload(1, 100_000));

        assertFalse(response.isSuccess());
        assertEquals(ErrorCode.UNAUTHORIZED, response.error().code());
    }

    @Test
    void authWithValidTokenSucceeds() throws Exception {
        String token = registerViaRest("hoe_01");
        TestClient client = connect();

        Message response = client.request(MessageType.AUTH, new AuthPayload(token));

        assertTrue(response.isSuccess(), "AUTH phải thành công: " + response.error());
        JsonObject user = response.payload().getAsJsonObject().getAsJsonObject("user");
        assertEquals("hoe_01", user.get("username").getAsString());
    }

    @Test
    void authWithInvalidTokenIsUnauthorized() throws Exception {
        TestClient client = connect();

        Message response = client.request(MessageType.AUTH, new AuthPayload("token-la"));

        assertFalse(response.isSuccess());
        assertEquals(ErrorCode.UNAUTHORIZED, response.error().code());
    }

    @Test
    void malformedJsonGetsErrorAndKeepsConnectionOpen() throws Exception {
        TestClient client = connect();

        client.sendRaw("{hỏng");
        Message error = client.next();

        assertFalse(error.isSuccess());
        assertEquals(ErrorCode.INVALID_MESSAGE, error.error().code());
        assertTrue(client.request(MessageType.PING, null).isSuccess(), "kết nối vẫn dùng được sau lỗi JSON");
    }

    @Test
    void bidFromOneUserIsPushedToBothUsersInRoom() throws Exception {
        long auctionId = openAuction();
        TestClient alice = connectAuthenticated("alice");
        TestClient bob = connectAuthenticated("bob");
        assertTrue(alice.request(MessageType.JOIN_AUCTION, new AuctionIdPayload(auctionId)).isSuccess());
        assertTrue(bob.request(MessageType.JOIN_AUCTION, new AuctionIdPayload(auctionId)).isSuccess());

        Message receipt = alice.request(MessageType.PLACE_BID, new PlaceBidPayload(auctionId, 200_000));
        assertTrue(receipt.isSuccess(), "đặt giá phải thành công: " + receipt.error());

        assertEquals(200_000, priceOf(alice.awaitEvent(MessageType.AUCTION_UPDATED)));
        assertEquals(200_000, priceOf(bob.awaitEvent(MessageType.AUCTION_UPDATED)));

        Message second = bob.request(MessageType.PLACE_BID, new PlaceBidPayload(auctionId, 220_000));
        assertTrue(second.isSuccess(), "bob đặt giá phải thành công: " + second.error());
        assertEquals(220_000, priceOf(alice.awaitEvent(MessageType.AUCTION_UPDATED)));
        assertEquals(220_000, priceOf(bob.awaitEvent(MessageType.AUCTION_UPDATED)));
    }

    @Test
    void closingConnectionNotifiesDisconnectListener() throws Exception {
        TestClient client = connect();
        assertTrue(client.request(MessageType.PING, null).isSuccess());

        client.closeQuietly();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (disconnected.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, disconnected.size(), "server phải gọi onDisconnect đúng một lần");
    }

    @Test
    void oversizedMessageClosesConnection() throws Exception {
        TestClient client = connect();

        client.sendRaw("x".repeat(70_000));

        assertEquals(1009, client.closeCode().get(WAIT_SECONDS, TimeUnit.SECONDS),
                "thông điệp quá 64 KiB phải bị đóng với mã 1009");
    }

    // ---- Tiện ích ----

    private TestClient connect() throws Exception {
        TestClient client = new TestClient();
        client.socket = http.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + gateway.port() + "/ws"), client)
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
        clients.add(client);
        return client;
    }

    private TestClient connectAuthenticated(String username) throws Exception {
        TestClient client = connect();
        Message response = client.request(MessageType.AUTH, new AuthPayload(registerViaRest(username)));
        assertTrue(response.isSuccess(), "AUTH phải thành công: " + response.error());
        return client;
    }

    /** Đăng ký qua REST và lấy token, đúng như trình duyệt làm trước khi mở WebSocket. */
    private String registerViaRest(String username) throws Exception {
        String body = JsonCodec.gson().toJson(
                Map.of("username", username, "password", "matkhau123", "displayName", "Người " + username));
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + gateway.port() + "/api/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode(), response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject().get("token").getAsString();
    }

    /** Tạo một phiên đã đến giờ mở rồi cho service chuyển sang ACTIVE, không cần chờ scheduler. */
    private long openAuction() throws Exception {
        long now = System.currentTimeMillis();
        var draft = new com.livebid.common.dto.AuctionDraft("Bàn phím cơ", "", 200_000, 20_000,
                now - 1_000, now + 300_000);
        AuctionView created = service.createAuction(new UserPrincipal(1, "admin", "Quản trị", Role.ADMIN), draft)
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
        service.processDueAuctions().get(WAIT_SECONDS, TimeUnit.SECONDS);
        return created.auctionId();
    }

    private static long priceOf(Message event) throws ProtocolException {
        return event.payloadAs(AuctionView.class).currentPrice();
    }

    /** Client WebSocket thử nghiệm: gom các khung văn bản thành thông điệp và chờ có thời hạn. */
    private static final class TestClient implements WebSocket.Listener {

        private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();
        private final List<Message> backlog = new ArrayList<>();
        private final StringBuilder partial = new StringBuilder();
        private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
        WebSocket socket;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                try {
                    inbox.add(JsonCodec.decode(partial.toString()));
                } catch (ProtocolException e) {
                    throw new AssertionError("server gửi thông điệp sai định dạng: " + partial, e);
                }
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeCode.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closeCode.completeExceptionally(error);
        }

        CompletableFuture<Integer> closeCode() {
            return closeCode;
        }

        void sendRaw(String text) {
            socket.sendText(text, true).join();
        }

        /** Gửi REQUEST rồi chờ RESPONSE cùng requestId; các thông điệp khác được giữ lại cho {@link #awaitEvent}. */
        Message request(MessageType type, Object payload) throws Exception {
            Message request = Message.request(type, payload);
            sendRaw(JsonCodec.encode(request));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (System.nanoTime() < deadline) {
                Message message = next();
                if (message.kind() == MessageKind.RESPONSE && request.requestId().equals(message.requestId())) {
                    return message;
                }
                backlog.add(message);
            }
            return fail("không nhận được RESPONSE cho " + type);
        }

        /** Chờ sự kiện thuộc loại {@code type}, kể cả sự kiện đã đến trước đó trong lúc chờ RESPONSE. */
        Message awaitEvent(MessageType type) throws Exception {
            for (int i = 0; i < backlog.size(); i++) {
                Message message = backlog.get(i);
                if (message.kind() == MessageKind.EVENT && type.name().equals(message.type())) {
                    return backlog.remove(i);
                }
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (System.nanoTime() < deadline) {
                Message message = next();
                if (message.kind() == MessageKind.EVENT && type.name().equals(message.type())) {
                    return message;
                }
                backlog.add(message);
            }
            return fail("không nhận được sự kiện " + type);
        }

        /** Thông điệp kế tiếp, tối đa {@link #WAIT_SECONDS} giây. */
        Message next() throws InterruptedException {
            Message message = inbox.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(message, "hết giờ chờ thông điệp từ server");
            return message;
        }

        void closeQuietly() {
            try {
                if (socket != null && !socket.isOutputClosed()) {
                    socket.sendClose(WebSocket.NORMAL_CLOSURE, "xong").get(2, TimeUnit.SECONDS);
                }
            } catch (Exception ignored) {
                // test kết thúc, bỏ qua lỗi đóng
            }
        }
    }
}
