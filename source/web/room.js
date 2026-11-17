// Phòng đấu giá (WebSocket /ws).
//
// Trình tự: mở /ws → AUTH (token từ REST) → JOIN_AUCTION nhận snapshot → PLACE_BID, nhận sự kiện AUCTION_*.
// Mất kết nối thì LiveSocket tự thử lại; mỗi lần kết nối lại màn hình này vào lại phòng để lấy snapshot mới.

import { session } from "./api.js";
import { LiveSocket } from "./socket.js";
import {
    clear, el, formatDuration, formatMoney, formatNumber, formatTime, parseMoneyInput, statusBadge, toast,
} from "./util.js";

const BID_TIMEOUT_MS = 10_000;
const HISTORY_LIMIT = 20;
const TICK_MS = 250;

// ---------------------------------------------------------------- logic thuần (không đụng DOM)

/**
 * Có nên áp dụng bản {@code incoming} lên bản {@code current} không. Bỏ qua bản có version cũ hơn hoặc
 * bằng; riêng snapshot sau khi vào phòng ({@code force}) chấp nhận version bằng để làm mới giờ server.
 */
export function shouldApply(current, incoming, force = false) {
    if (!current) {
        return true;
    }
    return force ? incoming.version >= current.version : incoming.version > current.version;
}

/** Mức giá thấp nhất được chấp nhận cho bid tiếp theo. */
export function minimumNextBid(auction) {
    return auction.hasBids ? auction.currentPrice + auction.minIncrement : auction.startingPrice;
}

/** Thời gian còn lại tới endsAt, tính theo giờ server (offset = serverTime - giờ máy lúc nhận). */
export function remainingMillis(auction, offset, nowMillis) {
    return auction.endsAt - (nowMillis + offset);
}

/** Kết quả đặt giá chưa rõ (không nhận được trả lời) khác với bị từ chối. */
export function isUnknownOutcome(error) {
    return error?.code === "TIMEOUT" || error?.code === "CONNECTION_LOST";
}

// ---------------------------------------------------------------- màn hình

export default {
    route: "auction",
    title: "Phòng đấu giá",
    nav: false,
    render(container, params) {
        const auctionId = Number(params[0]);
        const me = session.user;
        if (!Number.isSafeInteger(auctionId) || auctionId <= 0) {
            container.append(el("p", { class: "form-message", text: "Mã phiên không hợp lệ." }),
                el("a", { href: "#/auctions", text: "← Về danh sách" }));
            return null;
        }

        const state = {
            auction: null,
            offset: 0,
            bids: [],
            online: false,
            joined: false,
            pending: false,
            dirty: false,
            connection: "connecting",
        };
        let disposed = false;

        // ---- dựng giao diện
        const title = el("h2", { text: "Đang tải…" });
        const statusSlot = el("span");
        const connection = el("span", { class: "badge badge-connecting", text: "Đang kết nối…" });
        const description = el("p", { class: "muted" });
        const priceLabel = el("span", { class: "stat-label", text: "Giá" });
        const price = el("span", { class: "stat-value" });
        const leader = el("span", { class: "stat-value" });
        const countdown = el("span", { class: "stat-value countdown" });
        const startingPrice = el("span", { class: "stat-value small" });
        const increment = el("span", { class: "stat-value small" });
        const timeRange = el("span", { class: "stat-value small" });
        const extensions = el("span", { class: "stat-value small" });

        const amountInput = el("input", {
            type: "text", inputmode: "numeric", autocomplete: "off", "aria-label": "Số tiền đặt giá",
            oninput: () => {
                state.dirty = true;
            },
        });
        const bidButton = el("button", { type: "submit", class: "primary", text: "Đặt giá" });
        const bidHint = el("p", { class: "muted" });
        const bidMessage = el("p", { class: "form-message", role: "status" });
        const bidForm = el("form", { class: "bid-form", novalidate: true }, amountInput, bidButton);
        const bidBox = el("div", { class: "card" }, el("h3", { text: "Đặt giá" }), bidForm, bidHint, bidMessage);
        const notices = el("ul", { class: "notices", "aria-live": "polite" });
        const historyBody = el("tbody");

        container.append(
            el("a", { href: "#/auctions", class: "back", text: "← Danh sách phiên" }),
            el("div", { class: "room-head" }, title, statusSlot, connection),
            description,
            el("div", { class: "stats" },
                stat(priceLabel, price),
                stat("Người dẫn đầu", leader),
                stat("Còn lại", countdown),
                stat("Giá khởi điểm", startingPrice),
                stat("Bước giá tối thiểu", increment),
                stat("Thời gian", timeRange),
                stat("Số lần gia hạn", extensions)),
            me?.role === "ADMIN"
                ? el("p", { class: "muted", text: "Tài khoản quản trị chỉ xem phiên, không đặt giá." })
                : bidBox,
            el("h3", { text: "Thông báo" }),
            notices,
            el("h3", { text: "Lịch sử đặt giá" }),
            el("div", { class: "table-wrap" }, el("table", {},
                el("thead", {}, el("tr", {},
                    el("th", { text: "Thời gian" }), el("th", { text: "Người đặt" }), el("th", { class: "num", text: "Số tiền" }))),
                historyBody)),
        );

        function stat(label, valueNode) {
            return el("div", { class: "stat" },
                typeof label === "string" ? el("span", { class: "stat-label", text: label }) : label, valueNode);
        }

        // ---- cập nhật giao diện từ trạng thái
        function renderAuction() {
            const a = state.auction;
            if (!a) {
                return;
            }
            title.textContent = a.title;
            statusSlot.replaceChildren(statusBadge(a.status));
            description.textContent = a.description || "";
            // "Giá khởi điểm" khi chưa ai đặt; "Giá đã được đặt" khi đã có người đặt.
            priceLabel.textContent = a.hasBids ? "Giá đã được đặt" : "Giá khởi điểm";
            price.textContent = formatMoney(a.currentPrice);
            leader.textContent = a.leaderDisplayName ?? (a.hasBids ? "—" : "Chưa có");
            startingPrice.textContent = formatMoney(a.startingPrice);
            increment.textContent = formatMoney(a.minIncrement);
            timeRange.textContent = `${formatTime(a.startsAt)} → ${formatTime(a.endsAt)}`;
            extensions.textContent = String(a.extensionCount);
            renderBidControls();
            tick();
        }

        function renderBids() {
            historyBody.replaceChildren(...(state.bids.length === 0
                ? [el("tr", {}, el("td", { colspan: 3, class: "muted", text: "Chưa có lượt đặt giá nào." }))]
                : state.bids.map((bid) => el("tr", { class: bid.bidderDisplayName === me?.displayName ? "mine" : null },
                    el("td", { text: formatTime(bid.acceptedAt, { seconds: true }) }),
                    el("td", { text: bid.bidderDisplayName }),
                    el("td", { class: "num", text: formatMoney(bid.amount) })))));
        }

        function renderConnection() {
            const labels = {
                connecting: ["Đang kết nối…", "connecting"],
                online: ["Đã kết nối", "online"],
                offline: ["Mất kết nối — đang thử lại…", "offline"],
                "auth-failed": ["Không xác thực được", "offline"],
                closed: ["Đã đóng", "offline"],
            };
            const [text, kind] = labels[state.connection] ?? labels.connecting;
            connection.textContent = text;
            connection.className = `badge badge-${kind}`;
        }

        function bidDisabledReason() {
            const a = state.auction;
            if (!a) {
                return "Đang tải phiên…";
            }
            if (!state.online || !state.joined) {
                return "Chưa kết nối tới phòng, không thể đặt giá lúc này.";
            }
            if (a.status === "SCHEDULED") {
                return "Phiên chưa bắt đầu.";
            }
            if (a.status === "FINISHED") {
                return "Phiên đã kết thúc.";
            }
            if (state.pending) {
                return "Đang gửi yêu cầu đặt giá…";
            }
            return null;
        }

        function renderBidControls() {
            const reason = bidDisabledReason();
            const disabled = reason !== null;
            amountInput.disabled = disabled;
            bidButton.disabled = disabled;
            const a = state.auction;
            bidHint.textContent = reason
                ?? (a ? `Giá tối thiểu: ${formatMoney(minimumNextBid(a))}` : "");
        }

        function prefillBid(force = false) {
            const a = state.auction;
            if (!a) {
                return;
            }
            const minimum = minimumNextBid(a);
            const typed = parseMoneyInput(amountInput.value);
            if (force || !state.dirty || !(typed >= minimum)) {
                amountInput.value = formatNumber(minimum);
                state.dirty = false;
            }
        }

        function tick() {
            const a = state.auction;
            if (!a) {
                return;
            }
            const now = Date.now() + state.offset;
            if (a.status === "SCHEDULED") {
                countdown.textContent = "Bắt đầu sau " + formatDuration(a.startsAt - now);
            } else if (a.status === "ACTIVE") {
                const left = remainingMillis(a, state.offset, Date.now());
                countdown.textContent = left > 0 ? formatDuration(left) : "Đang chốt phiên…";
            } else {
                countdown.textContent = "Đã kết thúc";
            }
        }

        function notice(text, kind = "info") {
            notices.prepend(el("li", { class: `notice-${kind}` },
                el("time", { text: new Date().toLocaleTimeString("vi-VN") + " " }), text));
            while (notices.children.length > 20) {
                notices.lastChild.remove();
            }
        }

        // ---- áp dụng dữ liệu từ server
        function applyAuction(view, force = false) {
            if (!shouldApply(state.auction, view, force)) {
                return false; // bản cũ hơn hoặc trùng: bỏ qua
            }
            const previousLeader = state.auction?.leaderDisplayName ?? null;
            state.auction = view;
            state.offset = view.serverTime - Date.now();
            if (me && previousLeader === me.displayName && view.leaderDisplayName
                && view.leaderDisplayName !== me.displayName) {
                notice("Bạn đã bị vượt giá", "warn");
                toast("Bạn đã bị vượt giá", "warn");
            }
            renderAuction();
            prefillBid();
            return true;
        }

        function onEvent(type, view) {
            if (disposed || !view || view.auctionId !== auctionId) {
                return;
            }
            switch (type) {
                case "AUCTION_STARTED":
                    applyAuction(view);
                    notice("Phiên đã bắt đầu");
                    break;
                case "AUCTION_UPDATED":
                    applyAuction(view);
                    refreshHistory();
                    break;
                case "AUCTION_EXTENDED":
                    // Báo gia hạn dựa trên extensionCount để không mất thông báo dù bản cập nhật đã áp dụng trước đó.
                    if (view.extensionCount > (state.auction?.extensionCount ?? 0)) {
                        notice("Phiên được gia hạn", "warn");
                    }
                    applyAuction(view);
                    break;
                case "AUCTION_FINISHED": {
                    const wasFinished = state.auction?.status === "FINISHED";
                    applyAuction(view);
                    if (!wasFinished) {
                        notice(view.winnerDisplayName
                            ? `Phiên kết thúc. Người thắng: ${view.winnerDisplayName} với giá ${formatMoney(view.currentPrice)}`
                            : "Phiên kết thúc. Chưa có người thắng", "info");
                    }
                    break;
                }
                default:
                    break;
            }
        }

        function onState(next) {
            if (disposed) {
                return;
            }
            state.connection = next;
            state.online = next === "online";
            if (next !== "online") {
                state.joined = false; // server đã gỡ khỏi phòng khi mất kết nối; phải JOIN lại
            }
            renderConnection();
            renderBidControls();
            if (next === "auth-failed") {
                session.clear("expired");
            }
        }

        async function join() {
            try {
                const snapshot = await socket.request("JOIN_AUCTION", { auctionId });
                if (disposed) {
                    return;
                }
                state.joined = true;
                applyAuction(snapshot.auction, true);
                state.bids = snapshot.recentBids ?? [];
                renderBids();
                renderAuction();
                prefillBid();
            } catch (error) {
                if (disposed) {
                    return;
                }
                if (error.code === "AUCTION_NOT_FOUND") {
                    title.textContent = "Không tìm thấy phiên";
                }
                notice(error.message, "error");
                renderBidControls();
            }
        }

        async function refreshHistory() {
            try {
                const list = await socket.request("GET_BID_HISTORY", { auctionId, limit: HISTORY_LIMIT });
                if (!disposed) {
                    state.bids = list ?? [];
                    renderBids();
                }
            } catch {
                // lịch sử sẽ được tải lại ở sự kiện kế tiếp
            }
        }

        function setBidMessage(text, kind) {
            bidMessage.textContent = text;
            bidMessage.className = `form-message ${kind ?? ""}`;
        }

        bidForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (bidDisabledReason() !== null) {
                return;
            }
            const amount = parseMoneyInput(amountInput.value);
            if (!Number.isFinite(amount)) {
                setBidMessage("Số tiền không hợp lệ. Nhập số nguyên, ví dụ 280000 hoặc 280.000.", "error");
                return;
            }
            state.pending = true;
            setBidMessage("Đang gửi…", "");
            renderBidControls();
            try {
                const receipt = await socket.request("PLACE_BID", { auctionId, amount }, BID_TIMEOUT_MS);
                state.dirty = false;
                setBidMessage(`Đặt giá thành công: ${formatMoney(receipt.acceptedAmount)}`, "ok");
            } catch (error) {
                if (isUnknownOutcome(error)) {
                    // Không tự kết luận thất bại: có thể server đã nhận. Người dùng kiểm tra lịch sử trước khi thử lại.
                    setBidMessage("Chưa xác định kết quả. Hãy kiểm tra lịch sử đặt giá trước khi thử lại.", "warn");
                } else {
                    setBidMessage(error.message, "error");
                    if (error.code === "BID_TOO_LOW" && error.details?.minimumAllowed) {
                        amountInput.value = formatNumber(error.details.minimumAllowed);
                        state.dirty = false;
                    }
                }
            } finally {
                state.pending = false;
                if (!disposed) {
                    renderBidControls();
                }
            }
        });

        // ---- khởi động
        renderBids();
        renderConnection();
        renderBidControls();
        const socket = new LiveSocket({
            token: session.token,
            onEvent,
            onState,
            onReady: join,
        });
        socket.start();
        const timer = setInterval(tick, TICK_MS);

        return () => {
            disposed = true;
            clearInterval(timer);
            socket.stop();
            clear(notices);
        };
    },
};
