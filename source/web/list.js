// Danh sách phiên đấu giá (REST /api/auctions): lọc theo trạng thái, phân trang, làm mới.
// renderAuctionTable cũng được màn hình "Phiên của tôi" dùng lại.

import { api } from "./api.js";
import { el, formatMoney, formatTime, statusBadge } from "./util.js";

const PAGE_SIZE = 10;

const FILTERS = [
    { value: "", label: "Tất cả" },
    { value: "ACTIVE", label: "Đang diễn ra" },
    { value: "SCHEDULED", label: "Sắp diễn ra" },
    { value: "FINISHED", label: "Đã kết thúc" },
];

/**
 * Bảng các phiên: tên, giá, trạng thái, thời gian. Bấm vào dòng để vào phòng.
 * {@code extraColumn}: tùy chọn { header, cell(auction) } để thêm một cột (ví dụ nút "Sửa" ở trang quản trị).
 */
export function renderAuctionTable(items, { emptyText = "Chưa có phiên nào.", extraColumn = null } = {}) {
    if (items.length === 0) {
        return el("p", { class: "muted", text: emptyText });
    }
    const open = (id) => {
        location.hash = `#/auction/${id}`;
    };
    const rows = items.map((a) => el("tr", {
        class: "clickable",
        tabindex: 0,
        onclick: () => open(a.auctionId),
        onkeydown: (event) => {
            if (event.key === "Enter") {
                open(a.auctionId);
            }
        },
    },
        el("td", {}, el("a", { href: `#/auction/${a.auctionId}`, onclick: (e) => e.stopPropagation(), text: a.title })),
        el("td", { class: "num" }, formatMoney(a.currentPrice),
            a.hasBids ? null : el("small", { class: "muted", text: " (khởi điểm)" })),
        el("td", {}, statusBadge(a.status)),
        el("td", { text: formatTime(a.startsAt) }),
        el("td", { text: formatTime(a.endsAt) }),
        extraColumn ? el("td", { onclick: (e) => e.stopPropagation() }, extraColumn.cell(a)) : null,
    ));
    return el("div", { class: "table-wrap" }, el("table", {},
        el("thead", {}, el("tr", {},
            el("th", { text: "Sản phẩm" }), el("th", { class: "num", text: "Giá" }), el("th", { text: "Trạng thái" }),
            el("th", { text: "Bắt đầu" }), el("th", { text: "Kết thúc" }),
            extraColumn ? el("th", { text: extraColumn.header }) : null)),
        el("tbody", {}, rows)));
}

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
        const prev = el("button", { type: "button", text: "← Trước", onclick: () => go(page - 1) });
        const next = el("button", { type: "button", text: "Sau →", onclick: () => go(page + 1) });
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
                result.replaceChildren(renderAuctionTable(data.items, { emptyText: "Không có phiên nào ở trạng thái này." }));
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
