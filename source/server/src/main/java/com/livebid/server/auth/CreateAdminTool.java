package com.livebid.server.auth;

import com.livebid.common.dto.Role;
import com.livebid.server.ServerConfig;
import com.livebid.server.db.SqliteDatabase;
import com.livebid.server.db.SqliteUserRepository;
import com.livebid.server.model.NewUser;
import com.livebid.server.repo.DataAccessException;
import com.livebid.server.repo.UserRepository;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;

/**
 * Công cụ dòng lệnh tạo tài khoản {@link Role#ADMIN}. Chạy độc lập, không qua server đang chạy.
 *
 * <p>Cách chạy (từ thư mục {@code source/}): {@code mvn -pl server exec:java@create-admin}. Có thể truyền
 * đường dẫn CSDL làm tham số đầu tiên; nếu không thì lấy {@code database.path} trong cấu hình.
 *
 * <p>Không có mật khẩu mặc định trong mã nguồn. Mật khẩu được nhập hai lần và không hiện ra màn hình khi
 * có console. Khi chạy qua Maven thường không có console, khi đó mật khẩu hiện trên màn hình.
 *
 * <p>Thoát mã 0 nếu thành công, mã 1 nếu lỗi (trùng tên, dữ liệu không hợp lệ, không mở được CSDL).
 */
public final class CreateAdminTool {

    private CreateAdminTool() {
    }

    public static void main(String[] args) throws IOException {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args) throws IOException {
        ServerConfig config = ServerConfig.load(new String[0]);
        Path dbPath = args.length > 0
                ? Path.of(args[0])
                : Path.of(config.getString("database.path", "./data/auction.db"));
        int iterations = config.getInt("auth.pbkdf2Iterations", PasswordHasher.DEFAULT_ITERATIONS);

        Console console = System.console();
        BufferedReader in = console == null
                ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
                : null;
        if (console == null) {
            System.out.println("Cảnh báo: không có console, mật khẩu sẽ hiện trên màn hình khi bạn gõ.");
        }

        String username = readLine(console, in, "Tên đăng nhập: ");
        String displayName = readLine(console, in, "Tên hiển thị: ");
        char[] password = readSecret(console, in, "Mật khẩu: ");
        char[] confirm = readSecret(console, in, "Nhập lại mật khẩu: ");
        try {
            if (username == null || displayName == null || password == null || confirm == null) {
                System.err.println("Đã hủy: không đọc đủ thông tin.");
                return 1;
            }
            if (!Arrays.equals(password, confirm)) {
                System.err.println("Đã hủy: hai lần nhập mật khẩu không khớp.");
                return 1;
            }
            String problem = AuthServiceImpl.validationError(username, new String(password), displayName);
            if (problem != null) {
                System.err.println("Đã hủy: " + problem);
                return 1;
            }
            return createAdmin(dbPath, iterations, username.trim(), displayName.trim(), password);
        } finally {
            Arrays.fill(password == null ? new char[0] : password, '\0');
            Arrays.fill(confirm == null ? new char[0] : confirm, '\0');
        }
    }

    private static int createAdmin(Path dbPath, int iterations, String username, String displayName,
                                   char[] password) {
        String lower = username.toLowerCase(Locale.ROOT);
        try (SqliteDatabase db = SqliteDatabase.open(dbPath)) {
            UserRepository users = new SqliteUserRepository(db);
            if (users.findByUsername(lower).isPresent()) {
                System.err.println("Đã hủy: tên đăng nhập \"" + lower + "\" đã tồn tại.");
                return 1;
            }
            PasswordHasher.HashedPassword hashed = new PasswordHasher(iterations).hash(password);
            users.insert(new NewUser(lower, displayName, hashed.hash(), hashed.salt(), hashed.iterations(),
                    Role.ADMIN, Clock.systemUTC().millis()));
            System.out.println("Đã tạo tài khoản ADMIN \"" + lower + "\" trong " + dbPath.toAbsolutePath());
            return 0;
        } catch (DataAccessException e) {
            System.err.println("Đã hủy: lỗi cơ sở dữ liệu: " + e.getMessage());
            return 1;
        }
    }

    private static String readLine(Console console, BufferedReader in, String prompt) throws IOException {
        if (console != null) {
            return console.readLine(prompt);
        }
        System.out.print(prompt);
        System.out.flush();
        return in.readLine();
    }

    private static char[] readSecret(Console console, BufferedReader in, String prompt) throws IOException {
        if (console != null) {
            return console.readPassword(prompt);
        }
        String line = readLine(null, in, prompt);
        return line == null ? null : line.toCharArray();
    }
}
