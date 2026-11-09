package com.livebid.server.repo.memory;

import com.livebid.server.model.NewUser;
import com.livebid.server.model.User;
import com.livebid.server.repo.DataAccessException;
import com.livebid.server.repo.UserRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bản lưu trong bộ nhớ, dùng cho test và khi chưa có SQLite. */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> byId = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Optional<User> findById(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        return byId.values().stream().filter(u -> u.username().equals(key)).findFirst();
    }

    @Override
    public synchronized User insert(NewUser n) {
        String key = n.username().toLowerCase(Locale.ROOT);
        if (findByUsername(key).isPresent()) {
            throw new DataAccessException("username đã tồn tại: " + key, null);
        }
        User user = new User(nextId.getAndIncrement(), key, n.displayName(), n.passwordHash(),
                n.passwordSalt(), n.passwordIterations(), n.role(), n.createdAt());
        byId.put(user.id(), user);
        return user;
    }
}
