// Quản trị (chỉ ADMIN): tạo phiên (POST /api/auctions) và sửa phiên chưa bắt đầu (PUT /api/auctions/{id}).
//
// Giờ nhập bằng <input type="datetime-local"> (giờ địa phương) và đổi sang epoch ms trước khi gửi.
// Kiểm tra phía trình duyệt chỉ để báo lỗi sớm; server vẫn kiểm tra lại và lỗi của server được hiện nguyên văn.

import { api } from "./api.js";
import { renderAuctionTable } from "./list.js";
import { el, formatNumber, fromLocalInput, parseMoneyInput, toLocalInput, toast } from "./util.js";

const MINUTE = 60_000;

export default {
    route: "admin",
    title: "Quản trị",
    nav: true,
    order: 30,
    requires: "ADMIN",
    render(container) {
        let disposed = false;
        /** null = đang tạo mới; có giá trị = đang sửa phiên {id, version}. */
        let editing = null;

        const heading = el("h3");
        const message = el("p", { class: "form-message", role: "alert" });
        const title = el("input", { name: "title", type: "text", required: true });
        const description = el("textarea", { name: "description", rows: 3 });
        const startingPrice = el("input", { name: "startingPrice", type: "text", inputmode: "numeric", autocomplete: "off" });
        const minIncrement = el("input", { name: "minIncrement", type: "text", inputmode: "numeric", autocomplete: "off" });
        const startsAt = el("input", { name: "startsAt", type: "datetime-local", required: true });
        const endsAt = el("input", { name: "endsAt", type: "datetime-local", required: true });
        const submit = el("button", { type: "submit", class: "primary" });
        const cancel = el("button", { type: "button", text: "Hủy sửa", onclick: () => resetForm() });
        const form = el("form", { class: "card", novalidate: true },
            heading,
            el("div", { class: "form-grid" },
                field("Tên sản phẩm", title, "wide"),
                field("Mô tả", description, "wide"),
                field("Giá khởi điểm (đ)", startingPrice),
                field("Bước giá tối thiểu (đ)", minIncrement),
                field("Bắt đầu", startsAt),
                field("Kết thúc", endsAt)),
            message,
            el("div", { class: "actions" }, submit, cancel));
        const listBox = el("div", { "aria-live": "polite" });

        function field(label, input, extraClass) {
            return el("label", { class: extraClass }, el("span", { text: label }), input);
        }

        function resetForm() {
            editing = null;
            const now = Date.now();
            title.value = "";
            description.value = "";
            startingPrice.value = formatNumber(200_000);
            minIncrement.value = formatNumber(20_000);
            startsAt.value = toLocalInput(now + MINUTE);
            endsAt.value = toLocalInput(now + 6 * MINUTE);
            message.textContent = "";
            drawMode();
        }

        function drawMode() {
            heading.textContent = editing ? `Sửa phiên #${editing.id}` : "Tạo phiên mới";
            submit.textContent = editing ? "Lưu thay đổi" : "Tạo phiên";
            cancel.hidden = !editing;
        }

        function startEditing(auction) {
            editing = { id: auction.auctionId, version: auction.version };
            title.value = auction.title;
            description.value = auction.description ?? "";
            startingPrice.value = formatNumber(auction.startingPrice);
            minIncrement.value = formatNumber(auction.minIncrement);
            startsAt.value = toLocalInput(auction.startsAt);
            endsAt.value = toLocalInput(auction.endsAt);
            message.textContent = "";
            drawMode();
            form.scrollIntoView({ behavior: "smooth", block: "start" });
        }

        /** Đọc form thành AuctionDraft; trả {error} nếu dữ liệu sai. */
        function readDraft() {
            const name = title.value.trim();
            const starting = parseMoneyInput(startingPrice.value);
            const step = parseMoneyInput(minIncrement.value);
            const start = fromLocalInput(startsAt.value);
            const end = fromLocalInput(endsAt.value);
            if (!name) {
                return { error: "Tên sản phẩm không được để trống." };
            }
            if (!Number.isFinite(starting) || !Number.isFinite(step)) {
                return { error: "Giá khởi điểm và bước giá phải là số nguyên lớn hơn 0." };
            }
            if (!Number.isFinite(start) || !Number.isFinite(end)) {
                return { error: "Hãy chọn giờ bắt đầu và giờ kết thúc." };
            }
            if (end <= start) {
                return { error: "Giờ kết thúc phải sau giờ bắt đầu." };
            }
            return {
                draft: {
                    title: name,
                    description: description.value.trim(),
                    startingPrice: starting,
                    minIncrement: step,
                    startsAt: start,
                    endsAt: end,
                },
            };
        }

        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const { draft, error } = readDraft();
            if (error) {
                message.textContent = error;
                return;
            }
            submit.disabled = true;
            message.textContent = "";
            try {
                if (editing) {
                    await api.updateAuction(editing.id, editing.version, draft);
                    toast("Đã lưu thay đổi.", "ok");
                } else {
                    await api.createAuction(draft);
                    toast("Đã tạo phiên mới.", "ok");
                }
                resetForm();
                loadList();
            } catch (failure) {
                if (failure.code === "VERSION_CONFLICT") {
                    message.textContent = "Phiên đã bị thay đổi, tải lại.";
                    editing = null;
                    drawMode();
                    loadList();
                } else {
                    message.textContent = failure.message;
                }
            } finally {
                submit.disabled = false;
            }
        });

        async function loadList() {
            listBox.replaceChildren(el("p", { class: "muted", text: "Đang tải…" }));
            try {
                const page = await api.listAuctions({ status: "SCHEDULED", page: 1, pageSize: 50 });
                if (disposed) {
                    return;
                }
                listBox.replaceChildren(renderAuctionTable(page.items, {
                    emptyText: "Không có phiên nào đang chờ bắt đầu.",
                    extraColumn: {
                        header: "",
                        cell: (auction) => el("button", { type: "button", text: "Sửa", onclick: () => startEditing(auction) }),
                    },
                }));
            } catch (failure) {
                if (!disposed) {
                    listBox.replaceChildren(el("p", { class: "form-message", text: failure.message }));
                }
            }
        }

        container.append(
            el("h2", { text: "Quản trị" }),
            form,
            el("div", { class: "toolbar" },
                el("h3", { text: "Phiên chưa bắt đầu (có thể sửa)" }),
                el("button", { type: "button", text: "Làm mới", onclick: () => loadList() })),
            listBox,
        );
        resetForm();
        loadList();
        return () => {
            disposed = true;
        };
    },
};
