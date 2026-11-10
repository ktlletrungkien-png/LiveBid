package com.livebid.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.Protocol;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageKind;
import com.livebid.common.protocol.MessageType;
import com.livebid.server.tcp.FrameTooLargeException;
import com.livebid.server.tcp.LineFrameDecoder;
import com.livebid.server.tcp.MessageRouter;
import com.livebid.server.tcp.TcpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * T09–T10: đóng khung TCP. Thông điệp bị chia nhiều lần đọc, nhiều thông điệp trong một lần đọc,
 * JSON lỗi, thông điệp quá 64 KiB.
 */
class TcpFramingTest {

    // ================================================================== bộ tách khung (không cần socket)

    @Nested
    class Decoder {

        private final LineFrameDecoder decoder = new LineFrameDecoder(Protocol.MAX_MESSAGE_BYTES);
        private final List<String> frames = new ArrayList<>();

        private void feed(byte[] bytes) throws FrameTooLargeException {
            decoder.feed(bytes, 0, bytes.length, frames::add);
        }

        @Test
        void messageSplitAcrossReads() throws Exception {
            feed(utf8("{\"a\":"));
            assertTrue(frames.isEmpty(), "chưa gặp '\\n' thì chưa có thông điệp");
            feed(utf8("1}"));
            feed(utf8("\n"));
            assertEquals(List.of("{\"a\":1}"), frames);
        }

        @Test
        void severalMessagesInOneRead() throws Exception {
            feed(utf8("{\"a\":1}\n{\"a\":2}\n{\"a\":"));
            assertEquals(List.of("{\"a\":1}", "{\"a\":2}"), frames);
            assertEquals(5, decoder.pendingBytes(), "phần dở của thông điệp thứ ba được giữ lại");
            feed(utf8("3}\n"));
            assertEquals(List.of("{\"a\":1}", "{\"a\":2}", "{\"a\":3}"), frames);
        }

        @Test
        void vietnameseCharacterSplitInTheMiddle() throws Exception {
            byte[] line = utf8("{\"title\":\"Bàn phím cơ\"}\n");
            int middleOfA = indexOf(line, (byte) 0xC3) + 1; // "à" = C3 A0, cắt giữa hai byte
            feed(Arrays.copyOfRange(line, 0, middleOfA));
            feed(Arrays.copyOfRange(line, middleOfA, line.length));
            assertEquals(List.of("{\"title\":\"Bàn phím cơ\"}"), frames);
        }

        @Test
        void crlfAndEmptyLinesAreTolerated() throws Exception {
            feed(utf8("\n\r\n{\"a\":1}\r\n\n"));
            assertEquals(List.of("{\"a\":1}"), frames);
        }

        @Test
        void exactlyAtLimitIsAccepted() throws Exception {
            LineFrameDecoder small = new LineFrameDecoder(8);
            small.feed(utf8("12345678\n"), 0, 9, frames::add);
            assertEquals(List.of("12345678"), frames);
        }

        @Test
        void overLimitIsRejectedWithoutBufferingIt() throws Exception {
            LineFrameDecoder small = new LineFrameDecoder(8);
            byte[] bytes = utf8("ok\n123456789");
            assertThrows(FrameTooLargeException.class, () -> small.feed(bytes, 0, bytes.length, frames::add));
            assertEquals(List.of("ok"), frames, "thông điệp hợp lệ đứng trước vẫn được xử lý");
            assertTrue(small.pendingBytes() <= 8, "bộ đệm không vượt giới hạn");
        }
    }

    // ================================================================== qua socket thật

    @Nested
    class OverSocket {

        private static final long FIXED_TIME = 1_793_080_800_000L;

        private TcpServer server;

        @BeforeEach
        void startServer() throws IOException {
            Clock clock = Clock.fixed(Instant.ofEpochMilli(FIXED_TIME), ZoneOffset.UTC);
            server = new TcpServer(new TcpServer.Settings("127.0.0.1", 0, Protocol.MAX_MESSAGE_BYTES, 0, 10),
                    new MessageRouter(clock));
            server.start();
        }

        @AfterEach
        void stopServer() {
            server.close();
        }

        @Test
        void pingReturnsServerTime() throws Exception {
            try (TestClient client = connect()) {
                client.sendLine(ping("p1"));
                Message res = client.read();
                assertEquals(MessageKind.RESPONSE, res.kind());
                assertEquals("p1", res.requestId());
                assertTrue(res.isSuccess());
                assertEquals(FIXED_TIME, res.payload().getAsJsonObject().get("serverTime").getAsLong());
            }
        }

        @Test
        void messageSplitAcrossWrites() throws Exception {
            try (TestClient client = connect()) {
                byte[] line = utf8(ping("split") + "\n");
                int half = line.length / 2;
                client.sendRaw(Arrays.copyOfRange(line, 0, half));
                Thread.sleep(150); // để server chắc chắn nhận nửa đầu ở một lần read() riêng
                client.sendRaw(Arrays.copyOfRange(line, half, line.length));
                assertEquals("split", client.read().requestId());
            }
        }

        @Test
        void severalMessagesInOneWrite() throws Exception {
            try (TestClient client = connect()) {
                client.sendRaw(utf8(ping("a") + "\n" + ping("b") + "\n" + ping("c") + "\n"));
                assertEquals("a", client.read().requestId());
                assertEquals("b", client.read().requestId());
                assertEquals("c", client.read().requestId());
            }
        }

        @Test
        void invalidJsonGetsErrorAndConnectionStaysOpen() throws Exception {
            try (TestClient client = connect()) {
                client.sendLine("{not json");
                Message res = client.read();
                assertFalse(res.isSuccess());
                assertEquals(ErrorCode.INVALID_MESSAGE, res.error().code());

                client.sendLine(ping("after"));
                assertEquals("after", client.read().requestId(), "kết nối vẫn dùng được sau JSON lỗi");
            }
        }

        @Test
        void unknownCommandAndEventFromClientAreRejected() throws Exception {
            try (TestClient client = connect()) {
                client.sendLine("{\"protocolVersion\":1,\"kind\":\"REQUEST\",\"type\":\"HACK\",\"requestId\":\"h\"}");
                Message unknown = client.read();
                assertEquals("h", unknown.requestId());
                assertEquals(ErrorCode.INVALID_MESSAGE, unknown.error().code());

                client.sendLine("{\"protocolVersion\":1,\"kind\":\"EVENT\",\"type\":\"AUCTION_UPDATED\"}");
                assertEquals(ErrorCode.INVALID_MESSAGE, client.read().error().code());
            }
        }

        @Test
        void oversizedMessageClosesOnlyThatConnection() throws Exception {
            try (TestClient bystander = connect(); TestClient attacker = connect()) {
                byte[] huge = new byte[Protocol.MAX_MESSAGE_BYTES + 1024];
                Arrays.fill(huge, (byte) 'a');
                attacker.sendRawIgnoringErrors(huge);

                // Server có thể kịp gửi INVALID_MESSAGE trước khi đóng, hoặc client chỉ thấy kết nối bị cắt.
                // Điều bắt buộc: kết nối này kết thúc.
                List<Message> beforeClose = attacker.readUntilClosed();
                beforeClose.forEach(m -> assertEquals(ErrorCode.INVALID_MESSAGE, m.error().code()));

                bystander.sendLine(ping("still-alive"));
                assertEquals("still-alive", bystander.read().requestId(), "kết nối khác không bị ảnh hưởng");
            }
            try (TestClient fresh = connect()) {
                fresh.sendLine(ping("new"));
                assertEquals("new", fresh.read().requestId(), "server vẫn nhận kết nối mới");
            }
        }

        private TestClient connect() throws IOException {
            return new TestClient(server.localPort());
        }
    }

    // ================================================================== tiện ích

    private static String ping(String requestId) {
        return JsonCodec.encode(Message.request(MessageType.PING, requestId, null));
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static int indexOf(byte[] bytes, byte value) {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == value) {
                return i;
            }
        }
        throw new IllegalArgumentException("không thấy byte");
    }

    /** Client TCP không giao diện, chỉ dùng trong test. */
    static final class TestClient implements AutoCloseable {

        private final Socket socket;
        private final OutputStream out;
        private final BufferedReader in;

        TestClient(int port) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5_000);
            out = socket.getOutputStream();
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }

        void sendLine(String json) throws IOException {
            sendRaw(utf8(json + "\n"));
        }

        void sendRaw(byte[] bytes) throws IOException {
            out.write(bytes);
            out.flush();
        }

        void sendRawIgnoringErrors(byte[] bytes) {
            try {
                sendRaw(bytes);
            } catch (IOException ignored) {
                // server có thể đóng kết nối trước khi ghi xong
            }
        }

        Message read() throws Exception {
            String line = in.readLine();
            if (line == null) {
                throw new IOException("server đã đóng kết nối");
            }
            return JsonCodec.decode(line);
        }

        /** Đọc đến khi server đóng kết nối; thất bại nếu hết 5 giây mà kết nối vẫn mở. */
        List<Message> readUntilClosed() throws Exception {
            List<Message> messages = new ArrayList<>();
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    messages.add(JsonCodec.decode(line));
                }
            } catch (SocketException reset) {
                // "Connection reset": cũng nghĩa là server đã đóng
            }
            return messages;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
