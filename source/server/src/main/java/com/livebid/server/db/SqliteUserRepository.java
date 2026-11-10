package com.livebid.server.db;

import com.livebid.common.dto.Role;
import com.livebid.server.model.NewUser;
import com.livebid.server.model.User;
import com.livebid.server.repo.DataAccessException;
import com.livebid.server.repo.UserRepository;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Bản SQLite của {@link UserRepository}, bảng {@code users}. Chỉ gọi từ luồng executor. */
public final class SqliteUserRepository implements UserRepository {

    private static final String COLUMNS =
            "id, username, display_name, password_hash, password_salt, password_iterations, role, created_at";

    private final SqliteDatabase db;

    public SqliteUserRepository(SqliteDatabase db) {
        this.db = Objects.requireNonNull(db);
    }

    @Override
    public Optional<User> findById(long id) {
        return querySingle("SELECT " + COLUMNS + " FROM users WHERE id = ?", id);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return querySingle("SELECT " + COLUMNS + " FROM users WHERE username = ?",
                username.toLowerCase(Locale.ROOT));
    }

    @Override
    public User insert(NewUser user) {
        String sql = "INSERT INTO users (username, display_name, password_hash, password_salt, "
                + "password_iterations, role, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        String username = user.username().toLowerCase(Locale.ROOT);
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, user.displayName());
            ps.setString(3, user.passwordHash());
            ps.setString(4, user.passwordSalt());
            ps.setInt(5, user.passwordIterations());
            ps.setString(6, user.role().name());
            ps.setLong(7, user.createdAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new User(keys.getLong(1), username, user.displayName(), user.passwordHash(),
                        user.passwordSalt(), user.passwordIterations(), user.role(), user.createdAt());
            }
        } catch (SQLException e) {
            // Trùng username (UNIQUE) cũng vào đây; service đổi thành USERNAME_TAKEN.
            throw new DataAccessException("Không thêm được người dùng " + username, e);
        }
    }

    private Optional<User> querySingle(String sql, Object param) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lỗi đọc bảng users", e);
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                rs.getString("password_salt"),
                rs.getInt("password_iterations"),
                Role.valueOf(rs.getString("role")),
                rs.getLong("created_at"));
    }
}
