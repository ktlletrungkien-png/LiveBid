package com.livebid.client;

import com.livebid.common.Protocol;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Cấu hình của desktop client, đọc từ {@code config/client.properties}.
 *
 * <p>Thư mục chạy thường là {@code source/} (chạy {@code mvn -pl client exec:java}) hoặc
 * {@code source/client/} (chạy trong IDE), nên file được tìm lần lượt ở {@code config/} rồi
 * {@code ../config/} tính từ thư mục chạy. Không có file thì dùng mặc định giống
 * {@code config/client.properties.example}.
 *
 * <p>{@code server.host} để trống nghĩa là tự tìm server bằng UDP broadcast; nhập IP thủ công khi mạng
 * chặn broadcast.
 *
 * @param serverHost            IP/hostname server, chuỗi rỗng = tự tìm bằng UDP
 * @param tcpPort               cổng TCP của server
 * @param discoveryPort         cổng UDP mà server trả lời gói tìm kiếm
 * @param discoveryTimeoutMillis thời gian chờ mỗi lần thử tìm server
 * @param discoveryRetries      số lần gửi gói tìm kiếm
 * @param multicastGroup        nhóm multicast nhận thông báo "phiên sắp bắt đầu"
 * @param multicastPort         cổng multicast
 */
public record ClientConfig(
        String serverHost,
        int tcpPort,
        int discoveryPort,
        int discoveryTimeoutMillis,
        int discoveryRetries,
        String multicastGroup,
        int multicastPort) {

    /** Đường dẫn khi chạy từ {@code source/}. */
    public static final Path CONFIG_FILE = Paths.get("config", "client.properties");

    /** Đường dẫn khi chạy từ {@code source/client/} (IDE chạy theo module). */
    public static final Path CONFIG_FILE_FROM_MODULE = Paths.get("..", "config", "client.properties");

    /** Mặc định trùng với {@code config/client.properties.example}. */
    public static final int DEFAULT_DISCOVERY_TIMEOUT_MILLIS = 1_500;

    public static final int DEFAULT_DISCOVERY_RETRIES = 3;

    public ClientConfig {
        serverHost = serverHost == null ? "" : serverHost.trim();
        requirePort(tcpPort, "server.tcpPort");
        requirePort(discoveryPort, "udp.discoveryPort");
        if (discoveryTimeoutMillis <= 0) {
            throw new IllegalArgumentException("udp.discoveryTimeoutMillis phải > 0: " + discoveryTimeoutMillis);
        }
        if (discoveryRetries <= 0) {
            throw new IllegalArgumentException("udp.discoveryRetries phải > 0: " + discoveryRetries);
        }
        multicastGroup = multicastGroup == null || multicastGroup.isBlank()
                ? Protocol.DEFAULT_MULTICAST_GROUP
                : multicastGroup.trim();
        requirePort(multicastPort, "multicast.port");
    }

    /** Cấu hình mặc định, dùng khi không có file {@code client.properties}. */
    public static ClientConfig defaults() {
        return new ClientConfig("", Protocol.DEFAULT_TCP_PORT, Protocol.DEFAULT_UDP_DISCOVERY_PORT,
                DEFAULT_DISCOVERY_TIMEOUT_MILLIS, DEFAULT_DISCOVERY_RETRIES,
                Protocol.DEFAULT_MULTICAST_GROUP, Protocol.DEFAULT_MULTICAST_PORT);
    }

    /**
     * Đọc {@code client.properties} nếu có, không có thì trả {@link #defaults()}.
     *
     * @throws IllegalStateException khi không đọc được file
     * @throws IllegalArgumentException khi một giá trị trong file không hợp lệ
     */
    public static ClientConfig load() {
        Path file = findConfigFile();
        if (file == null) {
            return defaults();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + file.toAbsolutePath() + ": " + e.getMessage(), e);
        }
        return from(properties);
    }

    /** Đổi {@link Properties} thành cấu hình; thiếu khóa thì dùng mặc định. */
    public static ClientConfig from(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        return new ClientConfig(
                properties.getProperty("server.host", ""),
                intValue(properties, "server.tcpPort", Protocol.DEFAULT_TCP_PORT),
                intValue(properties, "udp.discoveryPort", Protocol.DEFAULT_UDP_DISCOVERY_PORT),
                intValue(properties, "udp.discoveryTimeoutMillis", DEFAULT_DISCOVERY_TIMEOUT_MILLIS),
                intValue(properties, "udp.discoveryRetries", DEFAULT_DISCOVERY_RETRIES),
                properties.getProperty("multicast.group", Protocol.DEFAULT_MULTICAST_GROUP),
                intValue(properties, "multicast.port", Protocol.DEFAULT_MULTICAST_PORT));
    }

    /** {@code true} khi người dùng để trống {@code server.host} và cần tự tìm server. */
    public boolean autoDiscover() {
        return serverHost.isBlank();
    }

    private static Path findConfigFile() {
        for (Path candidate : List.of(CONFIG_FILE, CONFIG_FILE_FROM_MODULE)) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static int intValue(Properties properties, String key, int fallback) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Giá trị của " + key + " trong client.properties không phải số nguyên: " + raw.trim(), e);
        }
    }

    private static void requirePort(int port, String key) {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException(key + " phải trong khoảng 1–65535: " + port);
        }
    }
}
