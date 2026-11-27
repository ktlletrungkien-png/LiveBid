package com.livebid.server.image;

import com.livebid.common.dto.UploadTicket;
import com.livebid.server.core.UserPrincipal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vé tải ảnh cho client desktop. Client đăng nhập qua TCP nên không có token REST; nó xin vé qua TCP rồi
 * dùng vé đó cho đúng một lần {@code POST /api/images}.
 *
 * <p>Vé ngẫu nhiên 192 bit, gắn với người xin, <b>dùng một lần</b> và hết hạn sau {@link #DEFAULT_TTL}.
 * Chỉ dùng được để tải ảnh, nên lộ vé cũng không làm được gì khác. Lưu trong bộ nhớ: server khởi động lại
 * thì vé cũ mất hiệu lực, client chỉ cần xin vé mới.
 */
public final class UploadTickets {

    public static final Duration DEFAULT_TTL = Duration.ofSeconds(60);

    /** Chặn một kết nối xin vé liên tục làm đầy bộ nhớ. */
    static final int MAX_LIVE_TICKETS = 10_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private record Entry(UserPrincipal user, long expiresAt) {
    }

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public UploadTickets(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock);
        this.ttl = Objects.requireNonNull(ttl);
    }

    public UploadTickets(Clock clock) {
        this(clock, DEFAULT_TTL);
    }

    /** Cấp vé mới cho {@code user}. */
    public UploadTicket issue(UserPrincipal user) {
        Objects.requireNonNull(user, "user");
        long now = clock.millis();
        if (tickets.size() >= MAX_LIVE_TICKETS) {
            tickets.values().removeIf(e -> e.expiresAt() <= now);
        }
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        long expiresAt = now + ttl.toMillis();
        tickets.put(ticket, new Entry(user, expiresAt));
        return new UploadTicket(ticket, expiresAt);
    }

    /** Dùng vé: trả người xin vé nếu vé còn hạn, rồi xóa vé (dùng lại lần hai sẽ rỗng). */
    public Optional<UserPrincipal> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        Entry entry = tickets.remove(ticket.trim());
        if (entry == null || entry.expiresAt() <= clock.millis()) {
            return Optional.empty();
        }
        return Optional.of(entry.user());
    }
}
