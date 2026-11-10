package com.livebid.server.db;

import com.livebid.server.repo.DataAccessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kết nối SQLite duy nhất của server và bộ chạy migration.
 *
 * <p><b>Chỉ dùng từ luồng {@code BusinessExecutor}</b>. Ngoại lệ: lúc khởi động server, công cụ dòng lệnh
 * và test đơn luồng. SQLite cho phép một {@link Connection} dùng chung khi chỉ có một luồng truy cập, nên
 * không dùng connection pool.
 *
 * <p>Migration: bảng {@code schema_version} lưu phiên bản hiện tại. Mỗi file {@code db/Vn__*.sql} trong
 * classpath có số {@code n} lớn hơn phiên bản hiện tại sẽ được chạy, mỗi file trong một transaction.
 */
public final class SqliteDatabase implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SqliteDatabase.class);

    /** Danh sách migration theo thứ tự tăng dần. Thêm bản mới vào cuối, không sửa bản cũ đã chạy. */
    private static final List<String> MIGRATIONS = List.of("db/V1__init.sql");

    private static final Pattern VERSION_PATTERN = Pattern.compile("V(\\d+)__");

    private final Path file;
    private final Connection connection;

    private SqliteDatabase(Path file, Connection connection) {
        this.file = file;
        this.connection = connection;
    }

    /**
     * Mở (hoặc tạo) file CSDL, bật khóa ngoại và WAL, rồi chạy migration còn thiếu.
     *
     * @throws DataAccessException nếu không mở được hoặc migration lỗi
     */
    public static SqliteDatabase open(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        try {
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
            SqliteDatabase db = new SqliteDatabase(absolute, connection);
            try {
                db.configure();
                db.migrate();
            } catch (RuntimeException e) {
                db.close();
                throw e;
            }
            log.info("Đã mở CSDL {}", absolute);
            return db;
        } catch (IOException e) {
            throw new DataAccessException("Không tạo được thư mục cho CSDL: " + absolute, e);
        } catch (SQLException e) {
            throw new DataAccessException("Không mở được CSDL: " + absolute, e);
        }
    }

    /** Kết nối dùng chung. Chỉ dùng từ luồng executor nghiệp vụ. */
    public Connection connection() {
        return connection;
    }

    /** Đường dẫn tuyệt đối của file CSDL. */
    public Path file() {
        return file;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("Lỗi khi đóng CSDL {}", file, e);
        }
    }

    private void configure() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void migrate() {
        try {
            ensureVersionTable();
            int current = currentVersion();
            for (String resource : MIGRATIONS) {
                int version = versionOf(resource);
                if (version > current) {
                    applyMigration(resource, version);
                    current = version;
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi khi chạy migration", e);
        }
    }

    private void ensureVersionTable() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
    }

    private int currentVersion() throws SQLException {
        try (Statement st = connection.createStatement();
             var rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void applyMigration(String resource, int version) throws SQLException {
        log.info("Chạy migration {} (phiên bản {})", resource, version);
        List<String> statements = readStatements(resource);
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement st = connection.createStatement()) {
                for (String sql : statements) {
                    st.execute(sql);
                }
                st.execute("DELETE FROM schema_version");
                st.execute("INSERT INTO schema_version (version) VALUES (" + version + ")");
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /** Đọc file SQL từ classpath, bỏ dòng chú thích {@code --} và tách theo dấu {@code ;}. */
    static List<String> readStatements(String resource) {
        String text;
        try (InputStream in = SqliteDatabase.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new DataAccessException("Không tìm thấy migration trong classpath: " + resource, null);
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Không đọc được migration: " + resource, e);
        }
        StringBuilder noComments = new StringBuilder();
        for (String line : text.split("\\R")) {
            int comment = line.indexOf("--");
            noComments.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String part : noComments.toString().split(";")) {
            String sql = part.strip();
            if (!sql.isEmpty()) {
                statements.add(sql);
            }
        }
        return statements;
    }

    private static int versionOf(String resource) {
        String fileName = resource.substring(resource.lastIndexOf('/') + 1);
        Matcher m = VERSION_PATTERN.matcher(fileName);
        if (!m.find()) {
            throw new DataAccessException("Tên migration không có số phiên bản: " + resource, null);
        }
        return Integer.parseInt(m.group(1));
    }
}
