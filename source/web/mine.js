// "Phiên của tôi" (REST /api/me/auctions): các phiên đã tham gia và các phiên đã thắng.

import { api } from "./api.js";
import { renderAuctionTable } from "./list.js";
import { el } from "./util.js";

const FILTERS = [
    { value: "participated", label: "Đã tham gia", empty: "Bạn chưa đặt giá ở phiên nào." },
    { value: "won", label: "Đã thắng", empty: "Bạn chưa thắng phiên nào." },
];

export default {
    route: "mine",
    title: "Phiên của tôi",
    nav: true,
    order: 20,
    render(container) {
        let disposed = false;
        let filter = FILTERS[0];

        const filterBar = el("div", { class: "tabs", role: "tablist" });
        const message = el("p", { class: "form-message", role: "alert" });
        const result = el("div", { "aria-live": "polite" });

        function drawFilters() {
            filterBar.replaceChildren(...FILTERS.map((f) => el("button", {
                type: "button", role: "tab", class: f === filter ? "active" : null,
                "aria-selected": String(f === filter), text: f.label,
                onclick: () => {
                    filter = f;
                    load();
                },
            })));
        }

        async function load() {
            drawFilters();
            message.textContent = "";
            result.replaceChildren(el("p", { class: "muted", text: "Đang tải…" }));
            try {
                const items = await api.myAuctions(filter.value);
                if (!disposed) {
                    result.replaceChildren(renderAuctionTable(items, { emptyText: filter.empty }));
                }
            } catch (error) {
                if (!disposed) {
                    result.replaceChildren();
                    message.textContent = error.message;
                }
            }
        }

        container.append(
            el("div", { class: "toolbar" },
                el("h2", { text: "Phiên của tôi" }),
                el("button", { type: "button", text: "Làm mới", onclick: () => load() })),
            filterBar,
            message,
            result,
        );
        load();
        return () => {
            disposed = true;
        };
    },
};
