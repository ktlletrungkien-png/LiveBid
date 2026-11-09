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
}
