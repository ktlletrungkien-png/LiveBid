package com.livebid.server;

import com.livebid.common.Protocol;
import com.livebid.server.auth.PasswordHasher;
import com.livebid.server.core.AuctionSettings;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Đọc {@code server.properties}. Thứ tự tìm file:
 *
 * <ol>
 *   <li>đường dẫn truyền làm tham số dòng lệnh đầu tiên;</li>
 *   <li>system property {@code -Dlivebid.config=...};</li>
 *   <li>{@code config/server.properties}, rồi {@code ../config/server.properties} (tính từ thư mục đang chạy,
 *       để chạy được cả từ {@code source/} lẫn {@code source/server/}).</li>
 * </ol>
 *
 * <p>Không tìm thấy file thì dùng giá trị mặc định. Mỗi mảng tự đọc khóa của mình bằng
 * {@link #getString}/{@link #getInt}; các getter có sẵn bên dưới là khóa của tầng TCP.
 */
public final class ServerConfig {

    public static final String CONFIG_PROPERTY = "livebid.config";

    private static final List<Path> DEFAULT_LOCATIONS = List.of(
            Path.of("config", "server.properties"),
            Path.of("..", "config", "server.properties"));

    private final Properties properties;
    private final Path source;

    private ServerConfig(Properties properties, Path source) {
        this.properties = properties;
        this.source = source;
    }

    /** Cấu hình rỗng (toàn giá trị mặc định); dùng cho test. */
    public static ServerConfig defaults() {
        return new ServerConfig(new Properties(), null);
    }

    public static ServerConfig load(String[] args) throws IOException {
        Optional<Path> path = locate(args);
        if (path.isEmpty()) {
            return defaults();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path.get(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new ServerConfig(properties, path.get().toAbsolutePath().normalize());
    }

    private static Optional<Path> locate(String[] args) throws IOException {
        String explicit = args != null && args.length > 0 ? args[0] : System.getProperty(CONFIG_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit);
            if (!Files.isRegularFile(path)) {
                throw new IOException("Không tìm thấy file cấu hình: " + path.toAbsolutePath());
            }
            return Optional.of(path);
        }
        return DEFAULT_LOCATIONS.stream().filter(Files::isRegularFile).findFirst();
    }

    /** File đã đọc; rỗng nếu đang dùng mặc định. */
    public Optional<Path> source() {
        return Optional.ofNullable(source);
    }

    public String getString(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cấu hình " + key + " phải là số nguyên, đang là: " + value, e);
        }
    }

    // ------------------------------------------------------------------ tầng TCP

    /** Địa chỉ bind; {@code 0.0.0.0} = mọi card mạng. Client không dùng địa chỉ này để kết nối. */
    public String host() {
        return getString("server.host", "0.0.0.0");
    }

    public int tcpPort() {
        return getInt("server.tcpPort", Protocol.DEFAULT_TCP_PORT);
    }

    public int maxMessageBytes() {
        return getInt("network.maxMessageBytes", Protocol.MAX_MESSAGE_BYTES);
    }

    /** Không nhận byte nào trong khoảng này thì đóng kết nối; 0 = không giới hạn. */
    public int idleTimeoutSeconds() {
        return getInt("network.idleTimeoutSeconds", 45);
    }

    public int maxConnections() {
        return getInt("network.maxConnections", 100);
    }

    // ------------------------------------------------------------------ khóa dùng chung khi khởi động

    public Path databasePath() {
        return Path.of(getString("database.path", "./data/auction.db"));
    }

    public int pbkdf2Iterations() {
        return getInt("auth.pbkdf2Iterations", PasswordHasher.DEFAULT_ITERATIONS);
    }

    public int httpPort() {
        return getInt("server.httpPort", Protocol.DEFAULT_HTTP_PORT);
    }

    public int udpDiscoveryPort() {
        return getInt("server.udpDiscoveryPort", Protocol.DEFAULT_UDP_DISCOVERY_PORT);
    }

    public String multicastGroup() {
        return getString("server.multicastGroup", Protocol.DEFAULT_MULTICAST_GROUP);
    }

    public int multicastPort() {
        return getInt("server.multicastPort", Protocol.DEFAULT_MULTICAST_PORT);
    }

    public int multicastTtl() {
        return getInt("server.multicastTtl", 1);
    }

    /** Khóa {@code auction.*}; thiếu khóa nào thì dùng mặc định của {@link AuctionSettings#defaults()}. */
    public AuctionSettings auctionSettings() {
        AuctionSettings d = AuctionSettings.defaults();
        return new AuctionSettings(
                getInt("auction.antiSnipingWindowSeconds", (int) (d.antiSnipingWindowMillis() / 1000)) * 1000L,
                getInt("auction.antiSnipingExtensionSeconds", (int) (d.antiSnipingExtensionMillis() / 1000)) * 1000L,
                getInt("auction.antiSnipingMaxExtensions", d.maxExtensions()),
                getInt("auction.announceLeadSeconds", (int) (d.announceLeadMillis() / 1000)) * 1000L,
                getInt("auction.snapshotBidCount", d.snapshotBidCount()));
    }
}
