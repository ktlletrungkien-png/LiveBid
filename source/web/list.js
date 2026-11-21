// Danh sách phiên đấu giá (REST /api/auctions): lọc theo trạng thái, phân trang, làm mới.
// renderAuctionTable cũng được "Phiên của tôi" và trang quản trị dùng lại.

import { api } from "./api.js";
import { el, formatDuration, formatMoney, formatTime, statusBadge } from "./util.js";

const PAGE_SIZE = 10;
/** Cùng cửa sổ chống bắn tỉa của server: còn ít hơn thế thì đồng hồ chuyển đỏ. */
const URGENT_MILLIS = 10_000;

const FILTERS = [
    { value: "", label: "Tất cả" },
    { value: "ACTIVE", label: "Đang diễn ra" },
    { value: "SCHEDULED", label: "Sắp diễn ra" },
    { value: "FINISHED", label: "Đã kết thúc" },
];

// ---------------------------------------------------------------- đồng hồ trên từng dòng

let tickerStarted = false;

/**
 * Một bộ đếm giờ duy nhất cho mọi bảng đang hiện: mỗi giây cập nhật các ô có {@code data-clock}.
 * Thời gian còn lại tính theo giờ server (offset = serverTime - Date.now() lúc nhận danh sách), không theo giờ máy.
 */
function ensureTicker() {
    if (tickerStarted) {
        return;
    }
    tickerStarted = true;
    setInterval(refreshClocks, 1000);
}

function refreshClocks() {
    const now = Date.now();
    document.querySelectorAll("[data-clock]").forEach((node) => updateClock(node, now));
}

function updateClock(node, now) {
    const serverNow = now + Number(node.dataset.offset);
    if (node.dataset.status === "ACTIVE") {
        const left = Number(node.dataset.endsAt) - serverNow;
        node.textContent = left > 0 ? `Còn ${formatDuration(left)}` : "Đang chốt phiên…";
        node.classList.toggle("urgent", left > 0 && left <= URGENT_MILLIS);
    } else {
        const until = Number(node.dataset.startsAt) - serverNow;
        node.textContent = until > 0 ? `Bắt đầu sau ${formatDuration(until)}` : "Sắp bắt đầu";
    }
}

function clockCell(auction, offset) {
    if (auction.status === "FINISHED") {
        return el("span", { class: "t-left", text: `Kết thúc lúc ${formatTime(auction.endsAt)}` });
    }
    const node = el("span", {
        class: "t-left",
        "data-clock": "",
        "data-status": auction.status,
        "data-starts-at": auction.startsAt,
        "data-ends-at": auction.endsAt,
        "data-offset": offset,
    });
    updateClock(node, Date.now()); // điền ngay, không chờ nhịp đếm giờ đầu tiên
    return node;
}

/**
 * Danh mục lô: số lô, tên và thời gian, giá, trạng thái kèm đồng hồ. Bấm vào dòng để vào phòng.
 * {@code extraColumn}: tùy chọn { header, cell(auction) } để thêm một cột (ví dụ nút "Sửa" ở trang quản trị).
 */
export function renderAuctionTable(items, { emptyText = "Chưa có phiên nào.", extraColumn = null } = {}) {
    if (items.length === 0) {
        return el("p", { class: "muted", text: emptyText });
    }
    ensureTicker();
    const open = (id) => {
        location.hash = `#/auction/${id}`;
    };
    const rows = items.map((a) => {
        const offset = a.serverTime - Date.now();
        const clock = clockCell(a, offset);
        return el("tr", {
            class: "clickable",
            tabindex: 0,
            onclick: () => open(a.auctionId),
            onkeydown: (event) => {
                if (event.key === "Enter") {
                    open(a.auctionId);
                }
            },
        },
            el("td", { class: "c-lot" }, el("span", { class: "lot-no", text: String(a.auctionId) })),
            el("td", { class: "c-title" },
                el("a", { class: "lot-title", href: `#/auction/${a.auctionId}`, onclick: (e) => e.stopPropagation(), text: a.title }),
                el("span", { class: "lot-meta", text: `${formatTime(a.startsAt)} đến ${formatTime(a.endsAt)}` })),
            el("td", { class: "price c-price" }, formatMoney(a.currentPrice),
                a.hasBids ? null : el("small", { text: "giá khởi điểm" })),
            el("td", { class: "c-status" }, statusBadge(a.status), clock),
            extraColumn ? el("td", { class: "c-extra", onclick: (e) => e.stopPropagation() }, extraColumn.cell(a)) : null,
        );
    });
    const table = el("div", { class: "table-wrap catalog" }, el("table", {},
        el("thead", {}, el("tr", {},
            el("th", { text: "Lô" }), el("th", { text: "Sản phẩm" }), el("th", { class: "num", text: "Giá" }),
            el("th", { text: "Trạng thái" }),
            extraColumn ? el("th", { text: extraColumn.header }) : null)),
        el("tbody", {}, rows)));
    return table;
}

// ---------------------------------------------------------------- màn hình

export default {
    route: "auctions",
    title: "Phiên đấu giá",
    nav: true,
    order: 10,
    render(container) {
        let disposed = false;
        let status = "";
        let page = 1;
        let hasMore = false;

        const message = el("p", { class: "form-message", role: "alert" });
        const result = el("div", { "aria-live": "polite" });
        const pageLabel = el("span", { class: "muted" });
        const prev = el("button", { type: "button", text: "Trang trước", onclick: () => go(page - 1) });
        const next = el("button", { type: "button", text: "Trang sau", onclick: () => go(page + 1) });
        const filterBar = el("div", { class: "tabs", role: "tablist" });

        function drawFilters() {
            filterBar.replaceChildren(...FILTERS.map((f) => el("button", {
                type: "button", role: "tab", class: f.value === status ? "active" : null,
                "aria-selected": String(f.value === status), text: f.label,
                onclick: () => {
                    status = f.value;
                    go(1);
                },
            })));
        }

        function go(target) {
            page = Math.max(1, target);
            load();
        }

        async function load() {
            drawFilters();
            message.textContent = "";
            result.replaceChildren(el("p", { class: "muted", text: "Đang tải…" }));
            try {
                const data = await api.listAuctions({ status, page, pageSize: PAGE_SIZE });
                if (disposed) {
                    return;
                }
                hasMore = data.hasMore;
                result.replaceChildren(renderAuctionTable(data.items, {
                    emptyText: status ? "Chưa có phiên nào ở trạng thái này. Chọn “Tất cả” để xem mọi phiên." : "Chưa có phiên nào. Phiên mới sẽ hiện ở đây.",
                }));
                pageLabel.textContent = `Trang ${data.page}`;
                prev.disabled = page <= 1;
                next.disabled = !hasMore;
            } catch (error) {
                if (!disposed) {
                    result.replaceChildren();
                    message.textContent = error.message;
                }
            }
        }

        container.append(
            el("div", { class: "toolbar" },
                el("h2", { text: "Phiên đấu giá" }),
                el("button", { type: "button", text: "Làm mới", onclick: () => load() })),
            filterBar,
            message,
            result,
            el("div", { class: "pager" }, prev, pageLabel, next),
        );
        load();
        return () => {
            disposed = true;
        };
    },
};
