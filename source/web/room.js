// Phòng đấu giá (WebSocket /ws).
//
// Trình tự: mở /ws → AUTH (token từ REST) → JOIN_AUCTION nhận snapshot → PLACE_BID, nhận sự kiện AUCTION_*.
// Mất kết nối thì LiveSocket tự thử lại; mỗi lần kết nối lại màn hình này vào lại phòng để lấy snapshot mới.
//
// Giao diện: ô giá đổi màu theo vị trí của người xem (xem positionOf): xanh lục khi đang dẫn đầu, đỏ khi bị vượt,
// và đồng hồ chuyển đỏ nhấp nháy ở 10 giây cuối.

import { session } from "./api.js";
import { LiveSocket } from "./socket.js";
import {
    clear, el, formatDuration, formatMoney, formatNumber, formatTime, parseMoneyInput, statusBadge, toast,
} from "./util.js";

const BID_TIMEOUT_MS = 10_000;
const HISTORY_LIMIT = 20;
const TICK_MS = 250;
/** Cùng cửa sổ chống bắn tỉa của server: còn ít hơn thế thì đồng hồ chuyển đỏ. */
const CLOSING_MILLIS = 10_000;

/** Nút chọn nhanh: giá tối thiểu cộng thêm n lần bước giá. */
const QUICK_BIDS = [
    { label: "Tối thiểu", steps: 0 },
    { label: "+1 bước", steps: 1 },
    { label: "+5 bước", steps: 5 },
    { label: "+10 bước", steps: 10 },
];

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

/**
 * Vị trí của người xem trong phòng, quyết định màu ô giá:
 * upcoming (chưa bắt đầu) · leading (đang dẫn đầu) · outbid (đã đặt nhưng bị vượt) · watching (chưa đặt) ·
 * won (đã thắng) · ended (kết thúc, không thắng).
 * Nhận diện theo tên hiển thị nên hai người trùng tên có thể bị nhầm (hạn chế đã biết).
 */
export function positionOf(auction, myName, participated) {
    if (!auction) {
        return "watching";
    }
    if (auction.status === "FINISHED") {
        return auction.winnerDisplayName && auction.winnerDisplayName === myName ? "won" : "ended";
    }
    if (auction.status === "SCHEDULED") {
        return "upcoming";
    }
    if (auction.hasBids && auction.leaderDisplayName === myName) {
        return "leading";
    }
    return participated ? "outbid" : "watching";
}

/** Dòng chữ dưới giá, nói rõ vị trí hiện tại. */
export function positionText(auction, position) {
    switch (position) {
        case "leading":
            return "Bạn đang dẫn đầu";
        case "outbid":
            return "Bạn đã bị vượt giá. Đặt cao hơn để giành lại.";
        case "won":
            return "Bạn đã thắng phiên này";
        case "upcoming":
            return "Phiên chưa bắt đầu";
        case "ended":
            return auction.winnerDisplayName
                ? `${auction.winnerDisplayName} thắng phiên này`
                : "Phiên đã kết thúc, chưa có người thắng";
        default:
            return auction.hasBids ? `${auction.leaderDisplayName ?? "Một người"} đang dẫn đầu` : "Chưa có ai đặt giá";
    }
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
                el("a", { href: "#/auctions", class: "back", text: "Danh sách phiên" }));
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
            participated: false,
            connection: "connecting",
        };
        let disposed = false;

        // ---- dựng giao diện
        const title = el("h2", { class: "room-title", text: "Đang tải…" });
        const statusSlot = el("span");
        const connection = el("span", { class: "badge badge-connecting", text: "Đang kết nối…" });
        const description = el("p", { class: "room-desc muted" });

        const priceLabel = el("p", { class: "slab-label" });
        const price = el("p", { class: "slab-price", text: "—" });
        const slabStatus = el("p", { class: "slab-status" });
        const slab = el("div", { class: "slab", "data-state": "watching" }, priceLabel, price, slabStatus);

        const clockLabel = el("p", { class: "clock-label", text: "Còn lại" });
        const countdown = el("p", { class: "countdown" });
        const clockSub = el("p", { class: "clock-sub muted" });
        const clock = el("div", { class: "clock", "data-closing": "false" },
            el("div", {}, clockLabel, countdown), clockSub);

        const factStart = el("dd");
        const factStep = el("dd");
        const factRange = el("dd");
        const factExtensions = el("dd");

        const amountInput = el("input", {
            type: "text", inputmode: "numeric", autocomplete: "off", "aria-label": "Số tiền đặt giá",
            oninput: () => {
                state.dirty = true;
            },
        });
        const bidButton = el("button", { type: "submit", class: "primary", text: "Đặt giá" });
        const chips = QUICK_BIDS.map((quick) => el("button", {
            type: "button",
            text: quick.label,
            onclick: () => {
                const a = state.auction;
                if (a) {
                    setAmount(minimumNextBid(a) + quick.steps * a.minIncrement, quick.steps > 0);
                }
            },
        }));
        const bidHint = el("p", { class: "bid-hint muted" });
        const bidMessage = el("p", { class: "form-message bid-message", role: "status" });
        const bidForm = el("form", { class: "bid-form", novalidate: true }, amountInput, bidButton);
        const paddle = el("div", { class: "paddle" },
            el("h3", { text: "Đặt giá của bạn" }), bidForm, el("div", { class: "chips" }, chips), bidHint, bidMessage);
        const notices = el("ul", { class: "notices", "aria-live": "polite" });
        const historyBody = el("tbody");

        container.append(
            el("a", { href: "#/auctions", class: "back", text: "Danh sách phiên" }),
            el("div", { class: "room-top" }, el("span", { class: "lot", text: `Lô ${auctionId}` }), statusSlot, connection),
            title,
            description,
            el("div", { class: "floor" }, slab, clock),
            me?.role === "ADMIN"
                ? el("p", { class: "muted", text: "Tài khoản quản trị chỉ xem phiên, không đặt giá." })
                : paddle,
            el("dl", { class: "facts" },
                fact("Giá khởi điểm", factStart), fact("Bước giá tối thiểu", factStep),
                fact("Thời gian", factRange), fact("Số lần gia hạn", factExtensions)),
            el("h3", { text: "Thông báo" }),
            notices,
            el("h3", { text: "Lịch sử đặt giá" }),
            el("div", { class: "table-wrap tape" }, el("table", {},
                el("thead", {}, el("tr", {},
                    el("th", { text: "Thời gian" }), el("th", { text: "Người đặt" }), el("th", { class: "num", text: "Số tiền" }))),
                historyBody)),
        );

        function fact(label, valueNode) {
            return el("div", {}, el("dt", { text: label }), valueNode);
        }

        // ---- cập nhật giao diện từ trạng thái
        function position() {
            return positionOf(state.auction, me?.displayName, state.participated);
        }

        function renderAuction() {
            const a = state.auction;
            if (!a) {
                return;
            }
            const current = position();
            title.textContent = a.title;
            statusSlot.replaceChildren(statusBadge(a.status));
            description.textContent = a.description || "";
            // "Giá khởi điểm" khi chưa ai đặt; "Giá đã được đặt" khi đã có người đặt.
            priceLabel.textContent = a.hasBids ? "Giá đã được đặt" : "Giá khởi điểm";
            price.textContent = formatMoney(a.currentPrice);
            slab.dataset.state = current;
            slabStatus.textContent = positionText(a, current);
            factStart.textContent = formatMoney(a.startingPrice);
            factStep.textContent = formatMoney(a.minIncrement);
            factRange.textContent = `${formatTime(a.startsAt)} đến ${formatTime(a.endsAt)}`;
            factExtensions.textContent = String(a.extensionCount);
            clockLabel.textContent = a.status === "SCHEDULED" ? "Bắt đầu sau" : a.status === "ACTIVE" ? "Còn lại" : "Đã kết thúc";
            clockSub.textContent = a.status === "SCHEDULED" ? `Bắt đầu lúc ${formatTime(a.startsAt)}` : `Kết thúc lúc ${formatTime(a.endsAt)}`;
            renderBidControls();
            tick();
            if (!slab.classList.contains("animated")) {
                // Bật hiệu ứng chuyển màu sau lần vẽ đầu, để lần hiện đầu tiên không chạy từ màu trung tính.
                requestAnimationFrame(() => slab.classList.add("animated"));
            }
        }

        function renderBids() {
            historyBody.replaceChildren(...(state.bids.length === 0
                ? [el("tr", {}, el("td", { colspan: 3, class: "muted", text: "Chưa có lượt đặt giá nào. Bạn có thể là người đầu tiên." }))]
                : state.bids.map((bid) => el("tr", { class: bid.bidderDisplayName === me?.displayName ? "mine" : null },
                    el("td", { text: formatTime(bid.acceptedAt, { seconds: true }) }),
                    el("td", { text: bid.bidderDisplayName }),
                    el("td", { class: "price", text: formatMoney(bid.amount) })))));
        }

        function renderConnection() {
            const labels = {
                connecting: ["Đang kết nối…", "connecting"],
                online: ["Đã kết nối", "online"],
                offline: ["Mất kết nối, đang thử lại…", "offline"],
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
            chips.forEach((chip) => {
                chip.disabled = disabled;
            });
            const a = state.auction;
            bidHint.textContent = reason ?? (a ? `Giá tối thiểu: ${formatMoney(minimumNextBid(a))}` : "");
        }

        function setAmount(value, typed) {
            amountInput.value = formatNumber(value);
            state.dirty = typed;
            amountInput.focus();
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
            let closing = false;
            if (a.status === "SCHEDULED") {
                countdown.textContent = formatDuration(a.startsAt - (Date.now() + state.offset));
            } else if (a.status === "ACTIVE") {
                const left = remainingMillis(a, state.offset, Date.now());
                countdown.textContent = left > 0 ? formatDuration(left) : "Đang chốt";
                closing = left > 0 && left <= CLOSING_MILLIS;
            } else {
                countdown.textContent = "00:00";
            }
            clock.dataset.closing = String(closing);
        }

        function notice(text, kind = "info") {
            notices.prepend(el("li", { class: `notice-${kind}` },
                el("time", { text: new Date().toLocaleTimeString("vi-VN") }), text));
            while (notices.children.length > 20) {
                notices.lastChild.remove();
            }
        }

        /** Nháy nhẹ ô giá khi giá tăng, để mắt bắt được thay đổi. */
        function flashPrice() {
            price.classList.remove("flash");
            void price.offsetWidth; // khởi động lại hiệu ứng
            price.classList.add("flash");
        }

        /** Có lượt đặt giá của mình trong danh sách thì coi như đã tham gia đặt giá. */
        function noteParticipation() {
            if (me && state.bids.some((bid) => bid.bidderDisplayName === me.displayName)) {
                state.participated = true;
            }
        }

        // ---- áp dụng dữ liệu từ server
        function applyAuction(view, force = false) {
            if (!shouldApply(state.auction, view, force)) {
                return false; // bản cũ hơn hoặc trùng: bỏ qua
            }
            const previous = state.auction;
            const previousLeader = previous?.leaderDisplayName ?? null;
            const raised = previous !== null && view.currentPrice > previous.currentPrice;
            state.auction = view;
            state.offset = view.serverTime - Date.now();
            if (me && view.hasBids && view.leaderDisplayName === me.displayName) {
                state.participated = true;
            }
            if (me && previousLeader === me.displayName && view.leaderDisplayName
                && view.leaderDisplayName !== me.displayName) {
                notice("Bạn đã bị vượt giá", "warn");
                toast("Bạn đã bị vượt giá", "warn");
            }
            renderAuction();
            prefillBid();
            if (raised) {
                flashPrice();
            }
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
                state.bids = snapshot.recentBids ?? [];
                noteParticipation();
                applyAuction(snapshot.auction, true);
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
                    noteParticipation();
                    renderBids();
                    renderAuction();
                }
            } catch {
                // lịch sử sẽ được tải lại ở sự kiện kế tiếp
            }
        }

        function setBidMessage(text, kind) {
            bidMessage.textContent = text;
            bidMessage.className = `form-message bid-message ${kind ?? ""}`;
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
                state.participated = true;
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
