package com.livebid.client.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.reflect.TypeToken;
import com.livebid.common.dto.BidView;
import com.livebid.common.dto.LoginResult;
import com.livebid.common.payload.BidHistoryPayload;
import com.livebid.common.payload.LoginPayload;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra {@link ServerConnection} với một server giả {@link ServerSocket} trên loopback, cổng do hệ điều
 * hành chọn. Không cần server thật, không cần mạng ngoài, không dùng cổng cố định.
 */
class ServerConnectionTest {

    private static final long WAIT_SECONDS = 5;

    private ServerConnection connection;
    private FakeServer server;

    @AfterEach
    void cleanup() {
        if (connection != null) {
            connection.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void pingNhanDungResponseTheoRequestId() throws Exception {
        server = new FakeServer();
        connection = new ServerConnection();
        connection.connect("127.0.0.1", server.port());

        CompletableFuture<Message> future = connection.request(MessageType.PING, null);
        Message request = server.readRequest();
        assertEquals(MessageType.PING, request.typeEnum(), "Client phải gửi đúng loại lệnh");
        assertNotNull(request.requestId(), "REQUEST phải có requestId");

        server.send(Message.success(request, Map.of("serverTime", 1_700_000_000_000L)));
        Message response = future.get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(request.requestId(), response.requestId(), "RESPONSE phải mang đúng requestId");
        assertTrue(response.isSuccess());
        assertEquals(1_700_000_000_000L,
                response.payload().getAsJsonObject().get("serverTime").getAsLong());
    }

    @Test
    void haiResponseNguocThuTuVanDungFuture() throws Exception {
        server = new FakeServer();
        connection = new ServerConnection();
        connection.connect("127.0.0.1", server.port());

        CompletableFuture<Message> first = connection.request(MessageType.PING, null);
        Message firstRequest = server.readRequest();
        CompletableFuture<Message> second = connection.request(MessageType.PING, null);
        Message secondRequest = server.readRequest();
        assertNotEquals(firstRequest.requestId(), secondRequest.requestId(), "Mỗi yêu cầu có requestId riêng");

        // Trả lời yêu cầu thứ hai trước, rồi mới tới yêu cầu thứ nhất.
        server.send(Message.success(secondRequest, Map.of("which", "second")));
        server.send(Message.success(firstRequest, Map.of("which", "first")));

        assertEquals("first", which(first.get(WAIT_SECONDS, TimeUnit.SECONDS)));
        assertEquals("second", which(second.get(WAIT_SECONDS, TimeUnit.SECONDS)));
    }

    @Test
    void tachKhungKhiThongDiepBiChiaDoiVaDinhNhau() throws Exception {
        server = new FakeServer();
        connection = new ServerConnection();
        connection.connect("127.0.0.1", server.port());

        CompletableFuture<Message> split = connection.request(MessageType.PING, null);
        Message splitRequest = server.readRequest();
        CompletableFuture<Message> gluedOne = connection.request(MessageType.PING, null);
        Message gluedOneRequest = server.readRequest();
        CompletableFuture<Message> gluedTwo = connection.request(MessageType.PING, null);
        Message gluedTwoRequest = server.readRequest();

        String splitFrame = JsonCodec.encode(Message.success(splitRequest, Map.of("which", "split")));
        int half = splitFrame.length() / 2;
        server.write(splitFrame.substring(0, half));
        Thread.sleep(30);
        server.write(splitFrame.substring(half) + "\n");

        // Hai thông điệp hoàn chỉnh đi trong cùng một lần ghi.
        String glued = JsonCodec.encode(Message.success(gluedOneRequest, Map.of("which", "glued-one")))
                + "\n"
                + JsonCodec.encode(Message.success(gluedTwoRequest, Map.of("which", "glued-two")))
                + "\n";
        server.write(glued);

        assertEquals("split", which(split.get(WAIT_SECONDS, TimeUnit.SECONDS)));
        assertEquals("glued-one", which(gluedOne.get(WAIT_SECONDS, TimeUnit.SECONDS)));
        assertEquals("glued-two", which(gluedTwo.get(WAIT_SECONDS, TimeUnit.SECONDS)));
    }

    @Test
    void eventDuocDuaChoListener() throws Exception {
        server = new FakeServer();
        connection = new ServerConnection();
        connection.connect("127.0.0.1", server.port());

        CompletableFuture<Message> received = new CompletableFuture<>();
        connection.addEventListener(received::complete);

        server.send(Message.event(MessageType.AUCTION_UPDATED, Map.of("auctionId", 7L)));
        Message event = received.get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertEquals(MessageType.AUCTION_UPDATED, event.typeEnum());
        assertEquals(7L, event.payload().getAsJsonObject().get("auctionId").getAsLong());
    }

    @Test
    void serverDongSocketThiFutureLoiVaTrangThaiDisconnected() throws Exception {
        server = new FakeServer();
        connection = new ServerConnection();
        connection.connect("127.0.0.1", server.port());

        CountDownLatch disconnected = new CountDownLatch(1);
        connection.addStateListener(state -> {
            if (state == ConnectionState.DISCONNECTED) {
                disconnected.countDown();
            }
        });

        CompletableFuture<Message> future = connection.request(MessageType.PING, null);
        server.readRequest();
        server.closeClient();

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, failure.getCause(), "Yêu cầu đang chờ phải lỗi IOException");
        assertTrue(disconnected.await(WAIT_SECONDS, TimeUnit.SECONDS),
                "Phải báo trạng thái DISCONNECTED");
        assertEquals(ConnectionState.DISCONNECTED, connection.state());
    }

    @Test
    void callVoiResponseThatBaiNemRemoteErrorException() throws Exception {
        server = new FakeServer();
        connection = new ServerConnection();
        connection.connect("127.0.0.1", server.port());

        CompletableFuture<LoginResult> future = connection.call(MessageType.LOGIN,
                new LoginPayload("quan", "sai-mat-khau"), LoginResult.class);
        Message request = server.readRequest();
        server.send(Message.failure(request, ErrorCode.INVALID_CREDENTIALS,
                "Sai tên đăng nhập hoặc mật khẩu", null));

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(WAIT_SECONDS, TimeUnit.SECONDS));
        RemoteErrorException error = assertInstanceOf(RemoteErrorException.class, failure.getCause());
        assertEquals(ErrorCode.INVALID_CREDENTIALS, error.error().code());
        assertEquals("Sai tên đăng nhập hoặc mật khẩu", error.getMessage());
    }

    @Test
    void callVoiTypeDocDuocDanhSachCoGeneric() throws Exception {
        server = new FakeServer();
        connection = new ServerConnection();
        connection.connect("127.0.0.1", server.port());

        Type listOfBids = new TypeToken<List<BidView>>() { }.getType();
        CompletableFuture<List<BidView>> future = connection.call(MessageType.GET_BID_HISTORY,
                new BidHistoryPayload(7, null, 20), listOfBids);
        Message request = server.readRequest();
        assertEquals(MessageType.GET_BID_HISTORY, request.typeEnum());
        server.send(Message.success(request, List.of(
                new BidView(11, 7, "An", 220_000, 1_700_000_000_000L),
                new BidView(12, 7, "Bình", 240_000, 1_700_000_001_000L))));

        List<BidView> bids = future.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(2, bids.size(), "Phải đọc được List<BidView> nhờ TypeToken");
        assertEquals(11, bids.get(0).bidId());
        assertEquals("An", bids.get(0).bidderDisplayName());
        assertEquals(220_000, bids.get(0).amount());
        assertEquals("Bình", bids.get(1).bidderDisplayName());
    }

    private static String which(Message message) {
        return message.payload().getAsJsonObject().get("which").getAsString();
    }

    /** Server giả: nhận một kết nối, đọc từng dòng REQUEST, gửi lại nguyên văn thứ test yêu cầu. */
    private static final class FakeServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final CountDownLatch accepted = new CountDownLatch(1);

        private volatile Socket client;
        private volatile BufferedReader reader;
        private volatile OutputStream out;

        FakeServer() throws IOException {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(this::accept, "fake-server");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        private void accept() {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.UTF_8));
                out = socket.getOutputStream();
                client = socket;
            } catch (IOException e) {
                // Test sẽ báo lỗi ở readRequest() nếu không kết nối được.
            } finally {
                accepted.countDown();
            }
        }

        Message readRequest() throws Exception {
            assertTrue(accepted.await(WAIT_SECONDS, TimeUnit.SECONDS), "Client phải kết nối tới server giả");
            String line = reader.readLine();
            assertNotNull(line, "Client phải gửi một REQUEST");
            return JsonCodec.decode(line);
        }

        void send(Message message) throws IOException {
            write(JsonCodec.encode(message) + "\n");
        }

        void write(String text) throws IOException {
            assertNotNull(out, "Server giả chưa nhận kết nối");
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        void closeClient() {
            Socket current = client;
            if (current != null) {
                try {
                    current.close();
                } catch (IOException ignored) {
                    // Đang đóng để test mất kết nối.
                }
            }
        }

        @Override
        public void close() {
            closeClient();
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Không còn gì để dọn.
            }
        }
    }
}
