package com.livebid.server.auth;

import com.livebid.common.dto.Role;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.server.core.AuthService;
import com.livebid.server.core.BusinessExecutor;
import com.livebid.server.core.ServiceException;
import com.livebid.server.core.UserPrincipal;
import com.livebid.server.auth.PasswordHasher.HashedPassword;
import com.livebid.server.model.NewUser;
import com.livebid.server.model.User;
import com.livebid.server.repo.DataAccessException;
import com.livebid.server.repo.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cài đặt {@link AuthService}: đăng ký, đăng nhập và token cho web.
 *
 * <p><b>Quy tắc luồng:</b> mọi lệnh gọi {@link UserRepository} nằm trong {@code executor.submit(...)}.
 * Băm và kiểm tra mật khẩu (tốn CPU) chạy trên pool băm riêng {@code auth-hash-N}, không chạy trên
 * executor nghiệp vụ. Không có chỗ nào {@code join()} hay {@code get()} bên trong executor.
 *
 * <p>Token chỉ lưu trong bộ nhớ: server khởi động lại thì mọi token mất, người dùng phải đăng nhập lại.
 * Đây là hạn chế đã biết của bản đầu.
 */
public final class AuthServiceImpl implements AuthService, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,30}$");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 128;
    private static final int MAX_DISPLAY_NAME = 50;
    private static final int HASH_THREADS = 2;
    private static final int TOKEN_BYTES = 32;

    private final BusinessExecutor executor;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;
    private final ExecutorService hashPool;
    private final Map<String, UserPrincipal> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    /** Hash giả cố định: dùng khi không có user, để thời gian trả lời gần như nhau. */
    private final HashedPassword dummyHash;

    public AuthServiceImpl(BusinessExecutor executor, UserRepository users, PasswordHasher hasher, Clock clock) {
        this.executor = executor;
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
        AtomicInteger counter = new AtomicInteger();
        this.hashPool = Executors.newFixedThreadPool(HASH_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "auth-hash-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.dummyHash = hasher.hash("dummy-password".toCharArray());
    }

    @Override
    public CompletableFuture<UserPrincipal> register(String username, String password, String displayName) {
        String problem = validationError(username, password, displayName);
        if (problem != null) {
            return failed(new ServiceException(ErrorCode.VALIDATION_ERROR, problem));
        }
        String name = username.toLowerCase(Locale.ROOT);
        String display = displayName.trim();

        // 1. Kiểm tra trùng tên sớm (trên executor), để không băm mật khẩu cho tên đã có.
        return executor.submit(() -> {
                    if (users.findByUsername(name).isPresent()) {
                        throw usernameTaken();
                    }
                    return null;
                })
                // 2. Băm trên pool băm.
                .thenComposeAsync(ignored -> hashAsync(password), hashPool)
                // 3. Ghi DB trên executor; kiểm tra lại trong cùng tác vụ để không có hai người cùng tên.
                .thenComposeAsync(hashed -> executor.submit(() -> insertUser(name, display, hashed)), hashPool);
    }

    @Override
    public CompletableFuture<UserPrincipal> login(String username, String password) {
        String name = username == null ? "" : username.toLowerCase(Locale.ROOT);
        return executor.submit(() -> users.findByUsername(name))
                .thenComposeAsync(found -> verifyAsync(found, password), hashPool);
    }

    @Override
    public CompletableFuture<UserPrincipal> updateProfile(UserPrincipal user, String displayName,
                                                          String currentPassword, String newPassword) {
        boolean changePassword = newPassword != null && !newPassword.isEmpty();
        String problem = displayNameError(displayName);
        if (problem == null && changePassword) {
            problem = passwordError(newPassword);
        }
        if (problem != null) {
            return failed(new ServiceException(ErrorCode.VALIDATION_ERROR, problem));
        }
        String display = displayName.trim();

        // 1. Đọc tài khoản hiện tại (executor) → 2. kiểm tra mật khẩu cũ và băm mật khẩu mới (pool băm)
        // → 3. ghi CSDL (executor) → 4. cập nhật các token đang cấp cho người này.
        return executor.submit(() -> users.findById(user.userId())
                        .orElseThrow(() -> new ServiceException(ErrorCode.UNAUTHORIZED, "Tài khoản không còn tồn tại")))
                .thenComposeAsync(stored -> changePassword
                        ? rehashAsync(stored, currentPassword, newPassword)
                        : CompletableFuture.completedFuture((HashedPassword) null), hashPool)
                .thenComposeAsync(hashed -> executor.submit(() -> saveProfile(user.userId(), display, hashed)), hashPool)
                .thenApply(updated -> {
                    tokens.replaceAll((token, principal) -> principal.userId() == updated.userId() ? updated : principal);
                    return updated;
                });
    }

    @Override
    public String issueToken(UserPrincipal user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.put(token, user);
        return token;
    }

    @Override
    public Optional<UserPrincipal> resolveToken(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tokens.get(token));
    }

    @Override
    public void revokeToken(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    /** Dừng pool băm. Gọi khi tắt server, sau khi đã đóng executor nghiệp vụ hoặc song song. */
    @Override
    public void close() {
        hashPool.shutdown();
    }

    // ---- Các bước nội bộ ----

    /** Chạy trên luồng executor: thêm user, kiểm tra lại trùng tên trong cùng tác vụ. */
    private UserPrincipal insertUser(String name, String display, HashedPassword hashed) {
        if (users.findByUsername(name).isPresent()) {
            throw usernameTaken();
        }
        try {
            User user = users.insert(new NewUser(name, display, hashed.hash(), hashed.salt(),
                    hashed.iterations(), Role.USER, clock.millis()));
            return new UserPrincipal(user.id(), user.username(), user.displayName(), user.role());
        } catch (DataAccessException e) {
            log.error("Không ghi được người dùng {} vào CSDL", name, e);
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "Lỗi cơ sở dữ liệu, thử lại sau");
        }
    }

    /** Chạy trên luồng executor: ghi tên mới và (nếu có) mật khẩu mới; đọc lại để không ghi đè dữ liệu cũ. */
    private UserPrincipal saveProfile(long userId, String display, HashedPassword hashed) {
        User current = users.findById(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.UNAUTHORIZED, "Tài khoản không còn tồn tại"));
        User updated = hashed == null
                ? new User(current.id(), current.username(), display, current.passwordHash(), current.passwordSalt(),
                        current.passwordIterations(), current.role(), current.createdAt())
                : new User(current.id(), current.username(), display, hashed.hash(), hashed.salt(),
                        hashed.iterations(), current.role(), current.createdAt());
        try {
            users.update(updated);
        } catch (DataAccessException e) {
            log.error("Không cập nhật được thông tin người dùng {}", userId, e);
            throw new ServiceException(ErrorCode.INTERNAL_ERROR, "Lỗi cơ sở dữ liệu, thử lại sau");
        }
        return new UserPrincipal(updated.id(), updated.username(), updated.displayName(), updated.role());
    }

    /** Chạy trên pool băm: kiểm tra mật khẩu hiện tại rồi băm mật khẩu mới với salt mới. */
    private CompletableFuture<HashedPassword> rehashAsync(User stored, String currentPassword, String newPassword) {
        return CompletableFuture.supplyAsync(() -> {
            char[] current = currentPassword == null ? new char[0] : currentPassword.toCharArray();
            char[] fresh = newPassword.toCharArray();
            try {
                if (!hasher.verify(current, stored.passwordHash(), stored.passwordSalt(), stored.passwordIterations())) {
                    throw new ServiceException(ErrorCode.INVALID_CREDENTIALS, "Mật khẩu hiện tại không đúng");
                }
                return hasher.hash(fresh);
            } finally {
                Arrays.fill(current, '\0');
                Arrays.fill(fresh, '\0');
            }
        }, hashPool);
    }

    /** Chạy trên pool băm: băm mật khẩu với salt mới. */
    private CompletableFuture<HashedPassword> hashAsync(String password) {
        return CompletableFuture.supplyAsync(() -> {
            char[] chars = password.toCharArray();
            try {
                return hasher.hash(chars);
            } finally {
                Arrays.fill(chars, '\0');
            }
        }, hashPool);
    }

    /** Chạy trên pool băm: kiểm tra mật khẩu, rồi trả lỗi chung nếu sai user hoặc sai mật khẩu. */
    private CompletableFuture<UserPrincipal> verifyAsync(Optional<User> found, String password) {
        return CompletableFuture.supplyAsync(() -> {
            char[] chars = password == null ? new char[0] : password.toCharArray();
            try {
                boolean matched;
                if (found.isPresent()) {
                    User user = found.get();
                    matched = hasher.verify(chars, user.passwordHash(), user.passwordSalt(), user.passwordIterations());
                } else {
                    // Vẫn tốn thời gian như khi có user, để không lộ username nào tồn tại.
                    hasher.verify(chars, dummyHash.hash(), dummyHash.salt(), dummyHash.iterations());
                    matched = false;
                }
                if (!matched || found.isEmpty()) {
                    throw invalidCredentials();
                }
                User user = found.get();
                return new UserPrincipal(user.id(), user.username(), user.displayName(), user.role());
            } finally {
                Arrays.fill(chars, '\0');
            }
        }, hashPool);
    }

    // ---- Kiểm tra dữ liệu và tạo lỗi ----

    /**
     * Trả về thông báo lỗi tiếng Việt, hoặc {@code null} nếu dữ liệu hợp lệ.
     * Dùng chung cho đăng ký và công cụ tạo ADMIN.
     */
    static String validationError(String username, String password, String displayName) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            return "Tên đăng nhập phải từ 3 đến 30 ký tự, chỉ gồm chữ không dấu, chữ số và dấu gạch dưới";
        }
        String problem = passwordError(password);
        return problem != null ? problem : displayNameError(displayName);
    }

    private static String passwordError(String password) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            return "Mật khẩu phải có từ 8 đến 128 ký tự";
        }
        return null;
    }

    private static String displayNameError(String displayName) {
        String display = displayName == null ? "" : displayName.trim();
        if (display.isEmpty() || display.length() > MAX_DISPLAY_NAME || containsControlChar(display)) {
            return "Tên hiển thị phải từ 1 đến 50 ký tự và không chứa ký tự điều khiển";
        }
        return null;
    }

    private static boolean containsControlChar(String text) {
        return text.chars().anyMatch(Character::isISOControl);
    }

    private static ServiceException usernameTaken() {
        return new ServiceException(ErrorCode.USERNAME_TAKEN, "Tên đăng nhập đã được sử dụng");
    }

    private static ServiceException invalidCredentials() {
        return new ServiceException(ErrorCode.INVALID_CREDENTIALS, "Sai tên đăng nhập hoặc mật khẩu");
    }

    private static <T> CompletableFuture<T> failed(ServiceException e) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
    }
}
