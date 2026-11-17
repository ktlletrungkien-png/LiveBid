// Thống kê (REST /api/stats): số liệu do dịch vụ Settlement cộng dồn.
// Mã 503 nghĩa là Settlement chưa chạy; đó là trạng thái bình thường, không phải lỗi.

import { api } from "./api.js";
import { el, formatMoney } from "./util.js";

export default {
    route: "stats",
    title: "Thống kê",
    nav: true,
    order: 40,
    render(container) {
        let disposed = false;
        const result = el("div", { "aria-live": "polite" });

        function tile(label, value) {
            return el("div", { class: "stat" },
                el("span", { class: "stat-label", text: label }),
                el("span", { class: "stat-value", text: value }));
        }

        function draw(summary) {
            const winners = summary.topWinners ?? [];
            result.replaceChildren(
                el("div", { class: "stats" },
                    tile("Tổng số phiên", String(summary.totalAuctions)),
                    tile("Phiên có người thắng", String(summary.auctionsWithWinner)),
                    tile("Tổng giá trị", formatMoney(summary.totalValue))),
                el("h3", { text: "Người thắng nhiều nhất" }),
                winners.length === 0
                    ? el("p", { class: "muted", text: "Chưa có dữ liệu." })
                    : el("div", { class: "table-wrap" }, el("table", {},
                        el("thead", {}, el("tr", {},
                            el("th", { text: "Hạng" }), el("th", { text: "Người thắng" }),
                            el("th", { class: "num", text: "Số phiên thắng" }), el("th", { class: "num", text: "Tổng chi" }))),
                        el("tbody", {}, winners.map((w, index) => el("tr", {},
                            el("td", { text: String(index + 1) }),
                            el("td", { text: w.displayName }),
                            el("td", { class: "num", text: String(w.wins) }),
                            el("td", { class: "num", text: formatMoney(w.totalSpent) })))))),
            );
        }

        async function load() {
            result.replaceChildren(el("p", { class: "muted", text: "Đang tải…" }));
            try {
                const summary = await api.stats();
                if (!disposed) {
                    draw(summary);
                }
            } catch (error) {
                if (disposed) {
                    return;
                }
                result.replaceChildren(error.status === 503
                    ? el("p", { class: "muted", text: "Dịch vụ thống kê chưa chạy." })
                    : el("p", { class: "form-message", text: error.message }));
            }
        }

        container.append(
            el("div", { class: "toolbar" },
                el("h2", { text: "Thống kê" }),
                el("button", { type: "button", text: "Làm mới", onclick: () => load() })),
            result,
        );
        load();
        return () => {
            disposed = true;
        };
    },
};
