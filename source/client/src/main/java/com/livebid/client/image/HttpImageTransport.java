package com.livebid.client.image;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.livebid.client.net.RemoteErrorException;
import com.livebid.client.net.ServerConnection;
import com.livebid.common.Protocol;
import com.livebid.common.dto.ServerInfo;
import com.livebid.common.dto.UploadTicket;
import com.livebid.common.protocol.MessageType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.ImageIO;

/**
 * Ảnh qua HTTP, dùng đúng các endpoint của trang web.
 *
 * <ul>
 *   <li><b>Lấy ảnh:</b> {@code GET /api/images/{id}}, không cần đăng nhập.</li>
 *   <li><b>Tải ảnh lên:</b> xin vé qua TCP ({@code REQUEST_UPLOAD_TICKET}), rồi {@code POST /api/images} kèm
 *       vé trong header {@value Protocol#UPLOAD_TICKET_HEADER} (client desktop không có token REST).</li>
 *   <li><b>Địa chỉ HTTP:</b> ghép IP đang kết nối TCP với cổng server báo qua {@code SERVER_INFO}. Nhớ tới
 *       khi {@link #reset()}.</li>
 * </ul>
 *
 * <p>Ảnh đi trên kết nối HTTP riêng nên file lớn không chặn lệnh đặt giá trên TCP. WebP đọc được nhờ plugin
 * ImageIO TwelveMonkeys. Các hàm chặn; {@link ClientImages} gọi chúng trên luồng nền.
 */
public final class HttpImageTransport {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final ServerConnection connection;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private String baseUrl;

    public HttpImageTransport(ServerConnection connection) {
        this.connection = connection;
    }

    /** Lấy ảnh theo mã, thu nhỏ để cạnh dài không quá {@code maxSide}. Chặn, không gọi trên EDT. */
    public BufferedImage load(String imageId, int maxSide) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/images/" + imageId))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new ImageException("Không tải được ảnh (HTTP " + response.statusCode() + ")");
        }
        return ImageFiles.shrink(decode(response.body()), maxSide);
    }

    /** Tải file ảnh lên, trả mã ảnh để gắn vào {@code AuctionDraft}. Chặn, không gọi trên EDT. */
    public String upload(Path file) {
        String fileName = file.getFileName().toString();
        byte[] data = readFile(file);
        ImageFiles.check(fileName, data.length);

        UploadTicket ticket = askServer(MessageType.REQUEST_UPLOAD_TICKET, UploadTicket.class);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/images"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", ImageFiles.contentType(fileName))
                .header(Protocol.UPLOAD_TICKET_HEADER, ticket.ticket())
                .POST(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() != 201) {
            // Lỗi của server có dạng {"error": {"code": "...", "message": "..."}}
            throw new ImageException(json.getAsJsonObject("error").get("message").getAsString());
        }
        return json.get("imageId").getAsString();
    }

    /** Quên địa chỉ HTTP đã dò (gọi khi mất kết nối, lần sau có thể tới server khác). */
    public synchronized void reset() {
        baseUrl = null;
    }

    // ---------------------------------------------------------------- địa chỉ HTTP

    /** Gốc HTTP của server, ví dụ {@code http://192.168.1.5:8080}; dò một lần rồi nhớ. */
    private synchronized String baseUrl() {
        if (baseUrl == null) {
            baseUrl = discoverBaseUrl();
        }
        return baseUrl;
    }

    private String discoverBaseUrl() {
        String host = connection.remoteHost();
        if (host == null) {
            throw new ImageException("Chưa kết nối tới server");
        }
        ServerInfo info;
        try {
            info = askServer(MessageType.SERVER_INFO, ServerInfo.class);
        } catch (ImageException e) {
            throw new ImageException("Server chưa hỗ trợ ảnh", e); // server bản cũ không biết SERVER_INFO
        }
        String hostPart = host.contains(":") ? "[" + host + "]" : host; // IPv6 cần ngoặc vuông
        return "http://" + hostPart + ":" + info.httpPort();
    }

    // ---------------------------------------------------------------- tiện ích (đổi lỗi thành câu dễ hiểu)

    /** Gửi một lệnh TCP không có payload và chờ kết quả. */
    private <T> T askServer(MessageType type, Class<T> resultType) {
        try {
            return connection.call(type, null, resultType)
                    .get(ServerConnection.REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RemoteErrorException remote) {
                throw new ImageException(remote.error().message(), remote); // lỗi nghiệp vụ, ví dụ chưa đăng nhập
            }
            throw new ImageException("Mất kết nối tới server", e.getCause());
        } catch (TimeoutException e) {
            throw new ImageException("Server không phản hồi", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageException("Đã hủy", e);
        }
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> body) {
        try {
            return http.send(request, body);
        } catch (IOException e) {
            throw new ImageException("Không kết nối được máy chủ ảnh", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageException("Đã hủy", e);
        }
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ImageException("Không đọc được định dạng ảnh này");
            }
            return image;
        } catch (IOException e) {
            throw new ImageException("Không đọc được ảnh", e);
        }
    }

    private static byte[] readFile(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ImageException("Không đọc được file: " + e.getMessage(), e);
        }
    }
}
