package com.livebid.tests.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.Role;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.server.auth.AuthServiceImpl;
import com.livebid.server.auth.PasswordHasher;
import com.livebid.server.core.BusinessExecutor;
import com.livebid.server.core.ServiceException;
import com.livebid.server.core.UserPrincipal;
import com.livebid.server.model.User;
import com.livebid.server.repo.memory.InMemoryUserRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra {@link AuthServiceImpl} với bản lưu trong bộ nhớ. Mỗi future được chờ tối đa 5 giây.
 */
class AuthServiceImplTest {

    private BusinessExecutor executor;
    private InMemoryUserRepository users;
    private AuthServiceImpl auth;

    @BeforeEach
    void setUp() {
        executor = new BusinessExecutor();
        users = new InMemoryUserRepository();
        auth = new AuthServiceImpl(executor, users, new PasswordHasher(1000), Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        auth.close();
        executor.close();
    }

    @Test
    void registerCreatesUserWithLowercaseNameRoleUserAndHashedPassword() throws Exception {
        UserPrincipal p = await(auth.register("Hoe_01", "matkhau123", "  Hoè  "));

        assertEquals("hoe_01", p.username());
        assertEquals("Hoè", p.displayName());
        assertEquals(Role.USER, p.role());

        User stored = users.findByUsername("hoe_01").orElseThrow();
        assertEquals(Role.USER, stored.role());
        assertFalse(stored.passwordHash().contains("matkhau123"), "không được lưu mật khẩu rõ");
        assertNotEquals(stored.passwordHash(), stored.passwordSalt());
    }

    @Test
    void registerWithSameNameDifferentCaseIsTaken() throws Exception {
        await(auth.register("abc_user", "matkhau123", "Một"));

        ServiceException e = failureOf(auth.register("ABC_user", "matkhau456", "Hai"));
        assertEquals(ErrorCode.USERNAME_TAKEN, e.code());
    }

    @Test
    void invalidRegistrationDataIsValidationError() {
        assertEquals(ErrorCode.VALIDATION_ERROR,
                failureOf(auth.register("ab", "matkhau123", "Tên")).code(), "username quá ngắn");
        assertEquals(ErrorCode.VALIDATION_ERROR,
                failureOf(auth.register("hoe_01", "1234567", "Tên")).code(), "mật khẩu 7 ký tự");
        assertEquals(ErrorCode.VALIDATION_ERROR,
                failureOf(auth.register("hoe_01", "matkhau123", "   ")).code(), "tên hiển thị rỗng");
        assertEquals(ErrorCode.VALIDATION_ERROR,
                failureOf(auth.register("hoe-01", "matkhau123", "Tên")).code(), "username có dấu gạch ngang");
    }

    @Test
    void loginWithCorrectPasswordReturnsPrincipal() throws Exception {
        await(auth.register("hoe_01", "matkhau123", "Hoè"));

        UserPrincipal p = await(auth.login("HOE_01", "matkhau123"));

        assertEquals("hoe_01", p.username());
        assertEquals(Role.USER, p.role());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveSameError() throws Exception {
        await(auth.register("hoe_01", "matkhau123", "Hoè"));

        ServiceException wrongPassword = failureOf(auth.login("hoe_01", "saimatkhau"));
        ServiceException unknownUser = failureOf(auth.login("khong_co", "matkhau123"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, wrongPassword.code());
        assertEquals(ErrorCode.INVALID_CREDENTIALS, unknownUser.code());
        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
    }

    @Test
    void tokenIssuedResolvesAndRevokedTokenDoesNot() throws Exception {
        UserPrincipal p = await(auth.register("hoe_01", "matkhau123", "Hoè"));

        String token = auth.issueToken(p);
        assertEquals(p, auth.resolveToken(token).orElseThrow());

        auth.revokeToken(token);
        assertEquals(Optional.empty(), auth.resolveToken(token));
    }

    @Test
    void unknownOrEmptyTokenResolvesToEmpty() {
        assertEquals(Optional.empty(), auth.resolveToken("token-la"));
        assertEquals(Optional.empty(), auth.resolveToken(""));
        assertEquals(Optional.empty(), auth.resolveToken(null));
    }

    // ---- Tiện ích ----

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    /** Lấy {@link ServiceException} từ future thất bại (cả khi lỗi bị bọc trong {@link ExecutionException}). */
    private static ServiceException failureOf(CompletableFuture<?> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof ServiceException, "phải là ServiceException, nhận " + cause);
            return (ServiceException) cause;
        } catch (Exception e) {
            throw new AssertionError("future không hoàn thành đúng cách", e);
        }
        throw new AssertionError("future lẽ ra phải thất bại");
    }
}
