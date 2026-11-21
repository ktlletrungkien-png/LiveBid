package com.livebid.server.repo;

import com.livebid.server.model.NewUser;
import com.livebid.server.model.User;
import java.util.Optional;

/** Bảng {@code users}. <b>Hoè cài đặt bản SQLite</b> trong {@code server.db}. */
public interface UserRepository {

    Optional<User> findById(long id);

    /** {@code username} so khớp chữ thường. */
    Optional<User> findByUsername(String username);

    /** Thêm và trả về bản có id. Trùng username → {@link DataAccessException}. */
    User insert(NewUser user);

    /**
     * Ghi tên hiển thị và mật khẩu (hash, salt, số vòng lặp) theo {@code user.id()}. Không đổi username, vai trò,
     * thời điểm tạo. Không có id → {@link DataAccessException}.
     */
    void update(User user);
}
