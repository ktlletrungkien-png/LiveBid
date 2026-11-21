// LiveBid – giao diện web (HTML + CSS + JS thuần).
// Dữ liệu đi qua đối tượng `api` / `socket` trong api.js (USE_MOCK = true: dữ liệu mẫu).
// Mọi dữ liệu hiển thị chèn bằng textContent (qua hàm el), không dùng innerHTML.
"use strict";

/* ====================== Tiện ích ====================== */

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const MONEY = new Intl.NumberFormat("vi-VN");
const DATE_TIME = new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" });
const CLOCK_TIME = new Intl.DateTimeFormat("vi-VN", { timeStyle: "medium" });

const formatMoney = (x) => MONEY.format(x) + "đ";
const formatDateTime = (ms) => DATE_TIME.format(ms);

// Chênh lệch đồng hồ máy chủ - máy khách: offset = serverTime - Date.now()
let clockOffset = 0;
const nowMs = () => Date.now() + clockOffset;
function syncClock(serverTime) { if (serverTime) clockOffset = serverTime - Date.now(); }

function formatCountdown(ms) {
    const total = Math.max(0, Math.floor(ms / SECOND));
    const days = Math.floor(total / 86400);
    const hh = String(Math.floor((total % 86400) / 3600)).padStart(2, "0");
    const mm = String(Math.floor((total % 3600) / 60)).padStart(2, "0");
    const ss = String(total % 60).padStart(2, "0");
    return (days ? days + " ngày " : "") + `${hh}:${mm}:${ss}`;
}

function toLocalInput(ms) {
    const d = new Date(ms - new Date(ms).getTimezoneOffset() * MINUTE);
    return d.toISOString().slice(0, 16);
}

function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (value === null || value === undefined || value === false) continue;
        if (key === "text") node.textContent = value;
        else if (key === "value") node.value = value;
        else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
        else node.setAttribute(key, value === true ? "" : value);
    }
    for (const child of children.flat()) if (child !== null && child !== undefined && child !== false) node.append(child);
    return node;
}

function toast(message) {
    const node = el("div", { class: "toast", text: message });
    document.getElementById("toasts").append(node);
    setTimeout(() => node.remove(), 4000);
}

const errorText = (error) => (error && error.message) || "Có lỗi xảy ra, vui lòng thử lại.";

// Hộp thoại. content(close) trả về node; có confirmLabel thì tự thêm nút Hủy / Xác nhận,
// onConfirm() trả về chuỗi lỗi để giữ hộp thoại mở. Kết quả: true nếu đã xác nhận.
function modal({ title, content, confirmLabel, onConfirm }) {
    return new Promise((resolve) => {
        const dialog = el("dialog");
        let result = false;
        const close = (value) => { result = value; dialog.close(); };
        const body = content(close);
        const children = [el("h3", { text: title }), body];
        if (confirmLabel) {
            const error = el("p", { class: "form-error", role: "alert" });
            const ok = el("button", { class: "btn-primary", type: "button", text: confirmLabel });
            ok.addEventListener("click", async () => {
                ok.disabled = true;
                const message = onConfirm ? await onConfirm() : null;
                ok.disabled = false;
                if (message) error.textContent = message; else close(true);
            });
            children.push(error, el("div", { class: "dialog-actions" },
                el("button", { class: "btn-secondary", type: "button", text: "Hủy", onclick: () => close(false) }), ok));
        }
        dialog.append(...children);
        dialog.addEventListener("close", () => { dialog.remove(); resolve(result); });
        document.body.append(dialog);
        dialog.showModal();
    });
}

const confirmDialog = (title, message, confirmLabel) =>
    modal({ title, content: () => el("p", { class: "sub", text: message }), confirmLabel });

/* ====================== Phiên đăng nhập ====================== */

function loadUser() {
    try { return JSON.parse(sessionStorage.getItem(USER_KEY)); } catch { return null; }
}
let currentUser = loadUser(); // {id, displayName, role: "USER" | "ADMIN"}

function setUser(user) {
    currentUser = user;
    if (user) sessionStorage.setItem(USER_KEY, JSON.stringify(user)); else sessionStorage.removeItem(USER_KEY);
}

const isAdmin = () => currentUser && currentUser.role === "ADMIN";

/* ====================== Thành phần dùng chung ====================== */

const STATUS = {
    PENDING_APPROVAL: ["Chờ duyệt", "tag tag-warn"],
    REJECTED: ["Đã từ chối", "tag tag-reject"],
    SCHEDULED: ["Sắp diễn ra", "tag"],
    ACTIVE: ["Đang diễn ra", "tag tag-live"],
    FINISHED: ["Đã kết thúc", "tag tag-done"],
};
const statusTag = (status) => el("span", { class: STATUS[status][1], text: STATUS[status][0] });
const ownerName = (a) => (a.ownerRole === "ADMIN" ? "Ban tổ chức" : a.ownerDisplayName);

function field({ id, label, type = "text", autocomplete, value, maxlength, step, min }) {
    const input = type === "textarea"
        ? el("textarea", { id, name: id, rows: 4, maxlength, value })
        : el("input", { id, name: id, type, autocomplete, maxlength, step, min, value });
    const error = el("p", { class: "field-error", id: id + "-error", role: "alert" });
    input.setAttribute("aria-describedby", error.id);
    const wrap = el("div", { class: "field" }, el("label", { for: id, text: label }), input, error);
    return {
        wrap, input,
        setError(message) {
            error.textContent = message || "";
            if (message) wrap.setAttribute("data-invalid", ""); else wrap.removeAttribute("data-invalid");
            input.setAttribute("aria-invalid", message ? "true" : "false");
        },
    };
}

/* ====================== B. Đăng nhập / Đăng ký ====================== */

const USERNAME_RE = /^[A-Za-z0-9_]{3,30}$/;

function loginView() {
    let mode = "login";
    const node = el("div", { class: "auth-wrap" });

    function draw() {
        const isLogin = mode === "login";
        const username = field({ id: "username", label: "Tên đăng nhập", autocomplete: "username", maxlength: 30 });
        const displayName = isLogin ? null : field({ id: "displayName", label: "Tên hiển thị", autocomplete: "nickname", maxlength: 50 });
        const password = field({ id: "password", label: "Mật khẩu", type: "password", autocomplete: isLogin ? "current-password" : "new-password" });
        const formError = el("p", { class: "form-error", role: "alert" });
        const submit = el("button", { class: "btn-primary", type: "submit", text: isLogin ? "Đăng nhập" : "Tạo tài khoản" });
        const form = el("form", { class: "stack", novalidate: true },
            username.wrap, displayName && displayName.wrap, password.wrap, formError, submit);

        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const u = username.input.value.trim();
            const p = password.input.value;
            const d = displayName ? displayName.input.value.trim() : "";
            username.setError(USERNAME_RE.test(u) ? "" : "Tên đăng nhập gồm 3–30 ký tự: chữ không dấu, số hoặc dấu gạch dưới (_).");
            password.setError(p.length >= 8 ? "" : "Mật khẩu cần ít nhất 8 ký tự.");
            if (displayName) displayName.setError(d.length >= 1 && d.length <= 50 ? "" : "Tên hiển thị phải từ 1 đến 50 ký tự.");
            if (form.querySelector("[data-invalid]")) return;

            submit.disabled = true;
            formError.textContent = "";
            try {
                const result = await (isLogin ? api.login(u, p) : api.register(u, p, d));
                setUser(result.user);
                toast(isLogin ? "Đăng nhập thành công." : "Đăng ký thành công.");
                go("#/auctions");
            } catch (error) {
                formError.textContent = errorText(error);
                submit.disabled = false;
            }
        });

        const tab = (value, label) => el("button", {
            type: "button", text: label, "aria-pressed": String(mode === value),
            onclick: () => { mode = value; draw(); },
        });

        const demo = USE_MOCK ? el("div", { class: "demo-accounts" },
            el("div", { text: "Tài khoản thử (dữ liệu mẫu):" }),
            el("div", {},
                el("button", { class: "link-btn", type: "button", text: "Người dùng (USER)", onclick: () => fill("nguyenvana", "matkhau123") }),
                " · ",
                el("button", { class: "link-btn", type: "button", text: "Quản trị viên (ADMIN)", onclick: () => fill("admin", "admin12345") }))) : null;

        function fill(u, p) {
            if (mode !== "login") { mode = "login"; draw(); }
            node.querySelector("#username").value = u;
            node.querySelector("#password").value = p;
        }

        const hero = el("div", { class: "hero" },
            el("h2", { text: "Đấu giá trực tiếp, giá nhảy từng giây." }),
            el("p", { class: "sub", text: "Tham gia phiên đấu giá theo thời gian thực, đặt giá và theo dõi đối thủ ngay trên trình duyệt." }),
            el("ul", { class: "points" },
                el("li", { text: "Giá cập nhật tức thì cho mọi người trong phòng" }),
                el("li", { text: "Đồng hồ đếm ngược chính xác, tự gia hạn cuối phiên" }),
                el("li", { text: "Theo dõi phiên của bạn và lịch sử đặt giá" })));

        const card = el("section", { class: "card auth" },
            el("h2", { text: isLogin ? "Chào mừng quay lại" : "Tạo tài khoản mới" }),
            el("p", { class: "sub", text: isLogin ? "Đăng nhập để bắt đầu đặt giá." : "Chỉ mất vài giây để tham gia." }),
            el("div", { class: "segmented" }, tab("login", "Đăng nhập"), tab("register", "Đăng ký")),
            form, demo);
        node.replaceChildren(hero, card);
    }

    draw();
    return { node };
}

/* ====================== C. Danh sách phiên ====================== */

const FILTERS = [["ALL", "Tất cả"], ["ACTIVE", "Đang diễn ra"], ["SCHEDULED", "Sắp diễn ra"], ["FINISHED", "Đã kết thúc"]];
const PAGE_SIZE = 6;
const listState = { filter: "ALL", page: 1 };

// Cập nhật mọi đồng hồ .countdown[data-target] trong node mỗi giây (dùng cho danh sách)
function startCountdowns(root, interval = SECOND) {
    const tick = () => {
        for (const node of root.querySelectorAll(".countdown[data-target]")) {
            const remaining = Number(node.dataset.target) - nowMs();
            node.textContent = formatCountdown(remaining);
            node.classList.toggle("urgent", remaining > 0 && remaining < 10 * MINUTE);
        }
    };
    tick();
    const timer = setInterval(tick, interval);
    return () => clearInterval(timer);
}

function auctionCard(a) {
    const hasTimer = a.status === "ACTIVE" || a.status === "SCHEDULED";
    const timer = el("span", { class: "countdown", "data-target": hasTimer ? String(a.status === "ACTIVE" ? a.endsAt : a.startsAt) : null });
    if (!hasTimer) timer.textContent = formatDateTime(a.endsAt);

    return el("article", { class: "card auction" },
        el("div", {}, statusTag(a.status)),
        el("h3", { text: a.title }),
        el("span", { class: "muted", text: ownerName(a) }),
        el("div", { class: "spacer" }),
        el("div", {},
            el("span", { class: "muted", text: a.hasBids ? "Giá hiện tại" : "Giá khởi điểm" }),
            el("p", { class: "price money", text: formatMoney(a.currentPrice) })),
        el("div", { class: "meta" },
            el("span", { class: "muted", text: a.status === "ACTIVE" ? "Còn lại" : a.status === "SCHEDULED" ? "Bắt đầu sau" : "Kết thúc lúc" }),
            timer),
        a.status === "ACTIVE"
            ? el("a", { class: "btn-primary", href: "#/auction/" + a.id, style: "text-align:center;text-decoration:none;display:block;line-height:1.2;", text: "Vào phòng đấu giá" })
            : el("a", { class: "btn-secondary", href: "#/auction/" + a.id, style: "text-align:center;text-decoration:none;display:block;line-height:1.2;", text: "Xem chi tiết" }));
}

async function listView() {
    const node = el("div");
    const filters = el("div", { class: "pills", role: "group", "aria-label": "Lọc theo trạng thái" });
    const total = el("span", { class: "muted" });
    const grid = el("div", { class: "grid" });
    const pager = el("div", { class: "pager" });
    let stopCountdowns = () => {};
    let alive = true;

    node.append(
        el("div", { class: "page-head" }, el("h2", { text: "Phiên đấu giá" }), total),
        el("div", { class: "stack" }, filters, grid, pager));

    async function load() {
        grid.setAttribute("aria-busy", "true");
        try {
            const result = await api.listAuctions(listState.filter, listState.page, PAGE_SIZE);
            if (!alive) return;
            const pages = Math.max(1, Math.ceil(result.total / PAGE_SIZE));
            if (listState.page > pages) { listState.page = pages; return load(); }
            if (result.items[0]) syncClock(result.items[0].serverTime);

            total.textContent = result.total + " phiên";
            grid.replaceChildren(...(result.items.length
                ? result.items.map(auctionCard)
                : [el("p", { class: "muted empty", text: "Chưa có phiên nào trong mục này." })]));
            stopCountdowns();
            stopCountdowns = startCountdowns(grid);

            pager.replaceChildren();
            if (pages > 1) {
                pager.append(
                    el("button", { class: "btn-secondary btn-small", type: "button", text: "‹ Trước", disabled: listState.page <= 1, onclick: () => { listState.page--; load(); } }),
                    el("span", { class: "muted", text: `Trang ${listState.page} / ${pages}` }),
                    el("button", { class: "btn-secondary btn-small", type: "button", text: "Sau ›", disabled: listState.page >= pages, onclick: () => { listState.page++; load(); } }));
            }
        } catch (error) {
            grid.replaceChildren(el("p", { class: "form-error empty", text: errorText(error) }));
        } finally {
            grid.removeAttribute("aria-busy");
        }
    }

    function drawFilters() {
        filters.replaceChildren(...FILTERS.map(([value, label]) => el("button", {
            type: "button", text: label, "aria-pressed": String(listState.filter === value),
            onclick: () => { listState.filter = value; listState.page = 1; drawFilters(); load(); },
        })));
    }

    drawFilters();
    await load();
    return { node, cleanup: () => { alive = false; stopCountdowns(); } };
}

/* ====================== E. Form đăng / sửa phiên (dùng chung) ====================== */

function validateDraft(fields) {
    const errors = {};
    const title = fields.title.input.value.trim();
    if (!title) errors.title = "Vui lòng nhập tiêu đề.";
    else if (title.length > 100) errors.title = "Tiêu đề tối đa 100 ký tự.";

    const description = fields.description.input.value.trim();
    if (description.length > 2000) errors.description = "Mô tả tối đa 2000 ký tự.";

    const readMoney = (key, name) => {
        const raw = fields[key].input.value.trim();
        const n = Number(raw);
        if (!raw || !Number.isInteger(n) || n <= 0) errors[key] = name + " phải là số nguyên lớn hơn 0.";
        else if (n > 1000000000000) errors[key] = name + " tối đa 1.000.000.000.000.";
        return n;
    };
    const startingPrice = readMoney("startingPrice", "Giá khởi điểm");
    const minIncrement = readMoney("minIncrement", "Bước giá");

    const startsAt = new Date(fields.startsAt.input.value).getTime();
    const endsAt = new Date(fields.endsAt.input.value).getTime();
    if (Number.isNaN(startsAt)) errors.startsAt = "Vui lòng chọn giờ bắt đầu.";
    else if (!isAdmin() && startsAt < nowMs() + 30 * MINUTE) errors.startsAt = "Giờ bắt đầu phải cách hiện tại ít nhất 30 phút.";
    if (Number.isNaN(endsAt)) errors.endsAt = "Vui lòng chọn giờ kết thúc.";
    else if (!Number.isNaN(startsAt) && endsAt <= startsAt) errors.endsAt = "Giờ kết thúc phải sau giờ bắt đầu.";

    return { errors, draft: { title, description, startingPrice, minIncrement, startsAt, endsAt } };
}

// auction: dữ liệu có sẵn khi sửa; onSubmit(draft) ném lỗi để hiện ở cuối form; onCancel: thêm nút Hủy.
function auctionForm({ auction, submitLabel, onSubmit, onCancel }) {
    const defaultStart = nowMs() + 60 * MINUTE;
    const fields = {
        title: field({ id: "title", label: "Tiêu đề", maxlength: 100, value: auction && auction.title }),
        description: field({ id: "description", label: "Mô tả", type: "textarea", maxlength: 2000, value: auction && auction.description }),
        startingPrice: field({ id: "startingPrice", label: "Giá khởi điểm (đ)", type: "number", min: 1, step: 1, value: auction && auction.startingPrice }),
        minIncrement: field({ id: "minIncrement", label: "Bước giá (đ)", type: "number", min: 1, step: 1, value: auction && auction.minIncrement }),
        startsAt: field({ id: "startsAt", label: "Giờ bắt đầu", type: "datetime-local", value: toLocalInput(auction ? auction.startsAt : defaultStart) }),
        endsAt: field({ id: "endsAt", label: "Giờ kết thúc", type: "datetime-local", value: toLocalInput(auction ? auction.endsAt : defaultStart + 24 * 60 * MINUTE) }),
    };
    for (const key of ["title", "description"]) fields[key].wrap.classList.add("full");

    const formError = el("p", { class: "form-error full", role: "alert" });
    const submit = el("button", { class: "btn-primary", type: "submit", text: submitLabel });
    const actions = el("div", { class: "row full" }, submit,
        onCancel ? el("button", { class: "btn-secondary", type: "button", text: "Hủy", onclick: onCancel }) : null);
    const note = !isAdmin() ? el("p", { class: "muted full", style: "margin:0", text: "Phiên sẽ được quản trị viên duyệt trước khi lên sàn." }) : null;

    const form = el("form", { class: "form-grid", novalidate: true },
        Object.values(fields).map((f) => f.wrap), formError, actions, note);

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const { errors, draft } = validateDraft(fields);
        for (const [key, f] of Object.entries(fields)) f.setError(errors[key]);
        if (Object.keys(errors).length) return;
        submit.disabled = true;
        formError.textContent = "";
        try {
            await onSubmit(draft);
        } catch (error) {
            formError.textContent = errorText(error);
            submit.disabled = false;
        }
    });
    return form;
}

function createView() {
    const form = auctionForm({
        submitLabel: "Đăng phiên",
        onSubmit: async (draft) => {
            const created = await api.createAuction(draft);
            toast(isAdmin() ? "Đã tạo phiên." : "Đã gửi phiên, vui lòng chờ quản trị viên duyệt.");
            go(created.status === "PENDING_APPROVAL" ? "#/mine" : "#/auctions");
        },
    });
    return { node: el("div", {},
        el("div", { class: "page-head" }, el("h2", { text: "Đăng phiên đấu giá" }),
            el("span", { class: "muted", text: "Điền thông tin sản phẩm và khung giờ đấu giá." })),
        el("section", { class: "card form-card" }, form)) };
}

/* ====================== D. Phòng đấu giá ====================== */

async function auctionView(idParam) {
    const id = Number(idParam);
    if (!Number.isInteger(id)) throw new ApiError("NOT_FOUND", "Không tìm thấy phiên");
    const auction = await api.getAuction(id);
    syncClock(auction.serverTime);
    const s = { ...auction };

    const details = el("section", { class: "card" });
    const facts = el("dl", { class: "facts" });
    const endsEl = el("dd", {});
    const statusSlot = el("div", {});

    const buildDetails = () => {
        statusSlot.replaceChildren(statusTag(s.status));
        facts.replaceChildren(
            el("div", {}, el("dt", { text: "Giá khởi điểm" }), el("dd", { class: "money", text: formatMoney(s.startingPrice) })),
            el("div", {}, el("dt", { text: "Bước giá" }), el("dd", { class: "money", text: formatMoney(s.minIncrement) })),
            el("div", {}, el("dt", { text: "Bắt đầu" }), el("dd", { text: formatDateTime(s.startsAt) })),
            el("div", {}, el("dt", { text: "Kết thúc" }), endsEl));
        endsEl.textContent = formatDateTime(s.endsAt);
    };
    details.append(statusSlot,
        el("h2", { text: s.title }),
        el("span", { class: "muted", text: ownerName(s) }),
        el("p", { class: "desc", text: s.description || "Chưa có mô tả." }),
        facts);
    buildDetails();

    // Phiên chưa lên sàn: chỉ xem chi tiết, không có phòng đấu giá
    if (s.status === "PENDING_APPROVAL" || s.status === "REJECTED") {
        const banner = s.status === "REJECTED"
            ? el("section", { class: "card" }, el("h3", { text: "Phiên đã bị từ chối" }),
                el("p", { class: "reason", text: "Lý do: " + (s.rejectReason || "Không có lý do được ghi nhận.") }))
            : el("section", { class: "card" }, el("h3", { text: "Phiên đang chờ duyệt" }),
                el("p", { class: "sub", text: "Phiên sẽ lên sàn sau khi quản trị viên duyệt." }));
        return { node: el("div", { class: "room-main" }, details, banner) };
    }

    /* ---------- phòng đấu giá ---------- */
    const priceLabel = el("span", { class: "on-dark-mute" });
    const priceEl = el("p", { class: "big-price money" });
    const leaderEl = el("p", { style: "margin:0" });
    const timerLabel = el("span", { class: "on-dark-mute" });
    const timerEl = el("p", { class: "big-timer countdown" });
    const dot = el("span", { class: "dot", "data-off": true });
    const connText = el("span", { text: "Đang kết nối..." });
    const notices = el("div", { class: "notices", "aria-live": "polite" });
    const sideNote = el("p", { class: "side-note", hidden: true });
    const bidInput = el("input", { id: "bid", type: "number", min: 1, step: 1, inputmode: "numeric" });
    const bidError = el("p", { class: "field-error", style: "color:#ff9db9", role: "alert" });
    const bidButton = el("button", { class: "btn-primary", type: "submit", text: "Đặt giá" });
    const hint = el("span", { class: "on-dark-mute" });
    const bidForm = el("form", { class: "side-stack", novalidate: true },
        el("div", { class: "field" }, el("label", { for: "bid", text: "Giá bạn đặt (đ)" }), bidInput, hint, bidError), bidButton);

    const historyBody = el("tbody");
    const historyEmpty = el("p", { class: "muted", text: "Chưa có lượt đặt giá nào." });
    let historyCount = 0;

    let online = false;
    let pending = false;
    let conn = null;

    const role = () => (isAdmin() ? "ADMIN" : currentUser.id === s.ownerId ? "OWNER" : "BIDDER");

    function paint() {
        statusSlot.replaceChildren(statusTag(s.status));
        endsEl.textContent = formatDateTime(s.endsAt);
        priceLabel.textContent = s.status === "FINISHED" ? "Giá chốt" : s.hasBids ? "Giá hiện tại" : "Giá khởi điểm";
        priceEl.textContent = formatMoney(s.currentPrice);
        timerLabel.textContent = s.status === "ACTIVE" ? "Còn lại" : s.status === "SCHEDULED" ? "Bắt đầu sau" : "Đã kết thúc";
        leaderEl.textContent = s.status === "FINISHED"
            ? (s.hasBids ? "Người thắng: " + s.leaderDisplayName : "Chưa có người thắng")
            : s.status === "SCHEDULED" ? "Phiên chưa bắt đầu"
                : s.hasBids ? "Đang dẫn đầu: " + s.leaderDisplayName : "Chưa có người đặt giá";
        hint.textContent = "Tối thiểu: " + formatMoney(s.minimumNextBid);

        const who = role();
        const canSeeForm = who === "BIDDER";
        bidForm.hidden = !canSeeForm;
        sideNote.hidden = canSeeForm;
        if (who === "ADMIN") sideNote.textContent = "Quản trị viên không tham gia đặt giá";
        else if (who === "OWNER") sideNote.textContent = "Đây là phiên bạn đăng";

        const open = s.status === "ACTIVE";
        bidInput.disabled = !open || !online || pending;
        bidButton.disabled = !open || !online || pending;
        bidButton.textContent = !online ? "Đang kết nối lại..." : "Đặt giá";
        if (s.status === "SCHEDULED") bidButton.textContent = "Chưa đến giờ";
        if (s.status === "FINISHED") bidButton.textContent = "Phiên đã kết thúc";

        dot.toggleAttribute("data-off", !online);
        connText.textContent = online ? "Đã kết nối" : "Đang kết nối lại...";
    }

    function flashPrice() {
        priceEl.classList.remove("flash");
        void priceEl.offsetWidth;
        priceEl.classList.add("flash");
    }

    function addNotice(text, kind) {
        const p = el("p", { class: kind, text });
        notices.prepend(p);
        while (notices.children.length > 3) notices.lastChild.remove();
        setTimeout(() => p.remove(), 7000);
    }

    function historyRow(bid) {
        return el("tr", {},
            el("td", { text: bid.bidderName }),
            el("td", { class: "num money", text: formatMoney(bid.amount) }),
            el("td", { class: "num", text: CLOCK_TIME.format(bid.at) }));
    }

    function addHistory(bid, onTop) {
        historyEmpty.hidden = true;
        if (onTop) historyBody.prepend(historyRow(bid)); else historyBody.append(historyRow(bid));
        historyCount++;
        while (historyCount > 20) { historyBody.lastChild.remove(); historyCount--; }
    }

    function setBidInput(force) {
        if (force || document.activeElement !== bidInput || Number(bidInput.value) < s.minimumNextBid) {
            bidInput.value = s.minimumNextBid;
        }
    }

    function applyUpdate(snapshot, bid, extended) {
        if (snapshot.version <= s.version) return; // bỏ qua bản cũ hơn bản đang hiển thị
        const previousLeader = s.leaderDisplayName;
        Object.assign(s, snapshot);
        syncClock(snapshot.serverTime);
        if (bid) addHistory(bid, true);
        paint();
        setBidInput(false);
        flashPrice();
        if (previousLeader === currentUser.displayName && s.leaderDisplayName !== currentUser.displayName) {
            addNotice("Bạn đã bị vượt giá", "bad");
        }
        if (extended) addNotice("Phiên được gia hạn thêm 30 giây", "warn");
    }

    bidForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (pending || !online) return;
        const amount = Number(bidInput.value);
        bidError.textContent = "";
        if (!Number.isInteger(amount) || amount <= 0) { bidError.textContent = "Vui lòng nhập số tiền hợp lệ."; return; }
        if (amount < s.minimumNextBid) {
            bidError.textContent = "Giá tối thiểu hiện tại là " + formatMoney(s.minimumNextBid);
            bidInput.value = s.minimumNextBid;
            return;
        }
        pending = true;
        paint();
        let timedOut = false;
        const timer = setTimeout(() => {
            timedOut = true;
            pending = false;
            paint();
            addNotice("Chưa xác định kết quả. Vui lòng kiểm tra lịch sử đặt giá trước khi đặt lại.", "warn");
        }, 10 * SECOND);
        try {
            await conn.placeBid(amount);
            if (!timedOut) addNotice("Đặt giá thành công: " + formatMoney(amount), "good");
        } catch (error) {
            if (!timedOut) {
                if (error.code === "BID_TOO_LOW" && error.details) {
                    bidError.textContent = errorText(error);
                    bidInput.value = error.details.minimumNextBid;
                } else {
                    bidError.textContent = errorText(error);
                }
            }
        } finally {
            clearTimeout(timer);
            if (!timedOut) { pending = false; paint(); }
        }
    });

    const side = el("aside", { class: "room-side" },
        el("section", { class: "card card-featured side-stack" },
            el("div", {}, priceLabel, priceEl),
            leaderEl,
            el("div", {}, timerLabel, timerEl),
            notices, sideNote, bidForm,
            el("div", { class: "conn" }, dot, connText,
                USE_MOCK ? el("button", { class: "link-btn", type: "button", text: "Giả lập mất kết nối", onclick: () => conn && conn.simulateDrop() }) : null)));

    const history = el("section", { class: "card" },
        el("h3", { text: "Lịch sử đặt giá" }),
        historyEmpty,
        el("div", { class: "table-wrap" }, el("table", {},
            el("thead", {}, el("tr", {},
                el("th", { text: "Người đặt" }), el("th", { class: "num", text: "Số tiền" }), el("th", { class: "num", text: "Giờ" }))),
            historyBody)));

    const node = el("div", { class: "room" }, el("div", { class: "room-main" }, details, history), side);

    // Lịch sử ban đầu (mới nhất trên cùng, tối đa 20 dòng)
    const initialBids = await api.getBidHistory(id, null, 20);
    for (const bid of initialBids) addHistory(bid, false);
    setBidInput(true);
    paint();

    conn = socket.connect(id, {
        onUpdate: applyUpdate,
        onConnection: (value) => { online = value; paint(); },
    });

    // Đồng hồ: còn lại = endsAt - (Date.now() + offset), cập nhật mỗi 250ms
    const tick = () => {
        const now = nowMs();
        if (s.status === "SCHEDULED" && now >= s.startsAt) { s.status = "ACTIVE"; paint(); }
        if (s.status === "ACTIVE" && now >= s.endsAt) { s.status = "FINISHED"; paint(); }
        const target = s.status === "SCHEDULED" ? s.startsAt : s.endsAt;
        timerEl.textContent = formatCountdown(s.status === "FINISHED" ? 0 : target - now);
    };
    tick();
    const timer = setInterval(tick, 250);

    return { node, cleanup: () => { clearInterval(timer); conn.close(); } };
}

/* ====================== F. Phiên của tôi ====================== */

function itemRow(a, { won, actions }) {
    const when = a.status === "ACTIVE" ? "Kết thúc " + formatDateTime(a.endsAt)
        : a.status === "FINISHED" ? "Đã kết thúc " + formatDateTime(a.endsAt) : "Bắt đầu " + formatDateTime(a.startsAt);
    return el("article", { class: "card item" },
        el("div", {},
            el("div", { class: "row" }, statusTag(a.status), won ? el("span", { class: "tag tag-win", text: "Bạn đã thắng" }) : null),
            el("h3", {}, el("a", { href: "#/auction/" + a.id, text: a.title })),
            el("span", { class: "muted", text: when }),
            a.status === "REJECTED" ? el("p", { class: "reason", text: "Lý do từ chối: " + (a.rejectReason || "Không có") }) : null),
        el("div", { class: "item-side" },
            el("div", {},
                el("span", { class: "muted", text: a.hasBids ? "Giá hiện tại" : "Giá khởi điểm" }),
                el("div", { class: "money", style: "font-size:20px;font-weight:500", text: formatMoney(a.currentPrice) })),
            actions));
}

async function editAuctionDialog(a) {
    return modal({
        title: "Sửa phiên",
        content: (close) => auctionForm({
            auction: a, submitLabel: "Lưu thay đổi", onCancel: () => close(false),
            onSubmit: async (draft) => { await api.updateAuction(a.id, a.version, draft); close(true); },
        }),
    });
}

async function mineView() {
    const node = el("div");
    const tabs = el("div", { class: "pills", role: "group" });
    const list = el("div", { class: "items" });
    let tab = "joined";
    let seq = 0;

    async function load() {
        const mySeq = ++seq;
        list.replaceChildren(el("p", { class: "muted", text: "Đang tải..." }));
        try {
            let rows;
            if (tab === "joined") {
                const [joined, won] = await Promise.all([api.myAuctions("participated"), api.myAuctions("won")]);
                const wonIds = new Set(won.map((a) => a.id));
                const merged = [...won, ...joined.filter((a) => !wonIds.has(a.id))];
                rows = merged.map((a) => itemRow(a, {
                    won: wonIds.has(a.id),
                    actions: el("a", { class: a.status === "ACTIVE" ? "btn-primary btn-small" : "btn-secondary btn-small",
                        href: "#/auction/" + a.id, style: "text-decoration:none;display:inline-block;line-height:1.2",
                        text: a.status === "ACTIVE" ? "Vào phòng" : "Xem chi tiết" }),
                }));
            } else {
                const created = await api.myAuctions("created");
                rows = created.map((a) => itemRow(a, {
                    actions: a.status === "PENDING_APPROVAL"
                        ? el("div", { class: "actions" },
                            el("button", { class: "btn-secondary btn-small", type: "button", text: "Sửa", onclick: async () => { if (await editAuctionDialog(a)) { toast("Đã lưu thay đổi."); load(); } } }),
                            el("button", { class: "btn-secondary btn-small", type: "button", text: "Hủy phiên", onclick: async () => {
                                if (!(await confirmDialog("Hủy phiên?", `Bạn chắc chắn muốn hủy phiên "${a.title}"? Thao tác này không thể hoàn tác.`, "Hủy phiên"))) return;
                                try { await api.cancelAuction(a.id); toast("Đã hủy phiên."); } catch (error) { toast(errorText(error)); }
                                load();
                            } }))
                        : null,
                }));
            }
            if (mySeq !== seq) return;
            list.replaceChildren(...(rows.length ? rows : [el("p", { class: "muted empty", text: tab === "joined" ? "Bạn chưa tham gia phiên nào." : "Bạn chưa đăng phiên nào." })]));
        } catch (error) {
            if (mySeq === seq) list.replaceChildren(el("p", { class: "form-error empty", text: errorText(error) }));
        }
    }

    function drawTabs() {
        tabs.replaceChildren(...[["joined", "Đã tham gia / Đã thắng"], ["created", "Phiên tôi đăng"]].map(([value, label]) => el("button", {
            type: "button", text: label, "aria-pressed": String(tab === value),
            onclick: () => { tab = value; drawTabs(); load(); },
        })));
    }

    node.append(el("div", { class: "page-head" }, el("h2", { text: "Phiên của tôi" })), el("div", { class: "stack" }, tabs, list));
    drawTabs();
    await load();
    return { node };
}

/* ====================== G. Quản trị ====================== */

async function rejectDialog(a) {
    const reason = field({ id: "reason", label: "Lý do từ chối", type: "textarea", maxlength: 500 });
    return modal({
        title: `Từ chối "${a.title}"`,
        content: () => reason.wrap,
        confirmLabel: "Từ chối phiên",
        onConfirm: async () => {
            const text = reason.input.value.trim();
            if (!text || text.length > 500) { reason.setError("Lý do bắt buộc, từ 1 đến 500 ký tự."); return "Vui lòng nhập lý do."; }
            try { await api.rejectAuction(a.id, text); return null; } catch (error) { return errorText(error); }
        },
    });
}

async function adminView() {
    const node = el("div");
    const tabs = el("div", { class: "pills", role: "group" });
    const body = el("div");
    let tab = "pending";

    async function showPending() {
        body.replaceChildren(el("p", { class: "muted", text: "Đang tải..." }));
        const items = await api.pendingAuctions();
        if (!items.length) { body.replaceChildren(el("p", { class: "muted empty card", text: "Không có phiên nào đang chờ duyệt." })); return; }

        const rows = items.map((a) => el("tr", {},
            el("td", {}, el("a", { href: "#/auction/" + a.id, text: a.title })),
            el("td", { text: a.ownerDisplayName }),
            el("td", { class: "num money", text: formatMoney(a.startingPrice) }),
            el("td", { class: "num money", text: formatMoney(a.minIncrement) }),
            el("td", { text: formatDateTime(a.startsAt) }),
            el("td", { text: formatDateTime(a.endsAt) }),
            el("td", {}, el("div", { class: "actions" },
                el("button", { class: "btn-primary btn-small", type: "button", text: "Duyệt", onclick: async (event) => {
                    event.currentTarget.disabled = true;
                    try { await api.approveAuction(a.id); toast("Đã duyệt phiên."); } catch (error) { toast(errorText(error)); }
                    showPending();
                } }),
                el("button", { class: "btn-secondary btn-small", type: "button", text: "Từ chối", onclick: async () => {
                    if (await rejectDialog(a)) { toast("Đã từ chối phiên."); showPending(); }
                } })))));

        body.replaceChildren(el("section", { class: "card" }, el("div", { class: "table-wrap" }, el("table", {},
            el("thead", {}, el("tr", {},
                el("th", { text: "Tiêu đề" }), el("th", { text: "Người đăng" }),
                el("th", { class: "num", text: "Giá khởi điểm" }), el("th", { class: "num", text: "Bước giá" }),
                el("th", { text: "Bắt đầu" }), el("th", { text: "Kết thúc" }), el("th", { text: "Thao tác" }))),
            el("tbody", {}, rows)))));
    }

    function showCreate() {
        body.replaceChildren(el("section", { class: "card form-card" }, auctionForm({
            submitLabel: "Tạo phiên",
            onSubmit: async (draft) => { await api.createAuction(draft); toast("Đã tạo phiên."); go("#/auctions"); },
        })));
    }

    async function showStats() {
        body.replaceChildren(el("p", { class: "muted", text: "Đang tải..." }));
        try {
            const st = await api.stats();
            const stat = (label, value, money) => el("div", { class: "card stat" },
                el("span", { class: "muted", text: label }),
                el("p", { class: "value" + (money ? " money" : ""), text: money ? formatMoney(value) : String(value) }));
            const top = st.topWinners.length
                ? el("div", { class: "table-wrap" }, el("table", {},
                    el("thead", {}, el("tr", {}, el("th", { text: "Người thắng" }), el("th", { class: "num", text: "Số phiên thắng" }), el("th", { class: "num", text: "Tổng giá trị" }))),
                    el("tbody", {}, st.topWinners.map((w) => el("tr", {},
                        el("td", { text: w.displayName }), el("td", { class: "num", text: String(w.count) }), el("td", { class: "num money", text: formatMoney(w.total) }))))))
                : el("p", { class: "muted", text: "Chưa có người thắng nào." });
            body.replaceChildren(
                el("div", { class: "stats" },
                    stat("Tổng số phiên", st.totalAuctions), stat("Phiên có người thắng", st.auctionsWithWinner), stat("Tổng giá trị", st.totalValue, true)),
                el("section", { class: "card" }, el("h3", { text: "Top người thắng" }), top));
        } catch (error) {
            if (error.code === "STATS_UNAVAILABLE") {
                body.replaceChildren(el("section", { class: "card empty" },
                    el("p", { text: "Dịch vụ thống kê chưa chạy" }),
                    el("button", { class: "btn-secondary", type: "button", text: "Thử lại", onclick: showStats })));
            } else {
                body.replaceChildren(el("p", { class: "form-error empty", text: errorText(error) }));
            }
        }
    }

    async function show() {
        try {
            if (tab === "pending") await showPending(); else if (tab === "create") showCreate(); else await showStats();
        } catch (error) {
            body.replaceChildren(el("p", { class: "form-error empty", text: errorText(error) }));
        }
    }

    function drawTabs() {
        tabs.replaceChildren(...[["pending", "Hàng chờ duyệt"], ["create", "Tạo phiên"], ["stats", "Thống kê"]].map(([value, label]) => el("button", {
            type: "button", text: label, "aria-pressed": String(tab === value),
            onclick: () => { tab = value; drawTabs(); show(); },
        })));
    }

    node.append(el("div", { class: "page-head" }, el("h2", { text: "Quản trị" })), el("div", { class: "stack" }, tabs, body));
    drawTabs();
    await show();
    return { node };
}

/* ====================== Router (hash) và header ====================== */

const root = document.getElementById("app");
let cleanup = null;
let renderSeq = 0;

function parseHash() {
    const parts = location.hash.replace(/^#\/?/, "").split("/");
    return { name: parts[0] || "auctions", param: parts[1] };
}

function go(hash) {
    if (location.hash === hash) render(); else location.hash = hash;
}

function renderHeader(route) {
    const nav = document.getElementById("nav");
    const box = document.getElementById("user-box");
    nav.replaceChildren();
    box.replaceChildren();
    if (!currentUser) return;

    const links = [["auctions", "Phiên đấu giá"], ["create", "Đăng phiên"], ["mine", "Phiên của tôi"]];
    if (isAdmin()) links.push(["admin", "Quản trị"]);
    for (const [name, label] of links) {
        nav.append(el("a", { href: "#/" + name, text: label, "aria-current": route.name === name || (name === "auctions" && route.name === "auction") ? "page" : null }));
    }
    box.append(
        el("span", { class: "muted", text: currentUser.displayName }),
        el("span", { class: isAdmin() ? "tag" : "tag tag-done", text: isAdmin() ? "Quản trị viên" : "Người dùng" }),
        el("button", { class: "btn-secondary", type: "button", text: "Đăng xuất", onclick: async () => {
            try { await api.logout(); } catch { /* vẫn đăng xuất phía giao diện */ }
            setUser(null);
            go("#/login");
        } }));
}

const VIEWS = {
    login: loginView, auctions: listView, auction: auctionView,
    create: createView, mine: mineView, admin: adminView,
};

async function render() {
    const seq = ++renderSeq;
    if (cleanup) { cleanup(); cleanup = null; }
    let route = parseHash();

    if (!VIEWS[route.name]) route = { name: currentUser ? "auctions" : "login" };
    if (!currentUser && route.name !== "login") { location.replace("#/login"); return; }
    if (currentUser && route.name === "login") { location.replace("#/auctions"); return; }
    if (route.name === "admin" && !isAdmin()) {
        toast("Bạn không có quyền");
        location.replace("#/auctions");
        return;
    }

    renderHeader(route);
    root.replaceChildren(el("p", { class: "muted", text: "Đang tải..." }));
    window.scrollTo(0, 0);
    try {
        const result = await VIEWS[route.name](route.param);
        if (seq !== renderSeq) { if (result.cleanup) result.cleanup(); return; }
        root.replaceChildren(result.node);
        cleanup = result.cleanup || null;
    } catch (error) {
        if (seq !== renderSeq) return;
        if (error.code === "UNAUTHORIZED") { setUser(null); location.replace("#/login"); return; }
        const notFound = error.code === "NOT_FOUND";
        root.replaceChildren(el("section", { class: "card empty" },
            el("h2", { text: notFound ? "Không tìm thấy phiên" : "Không tải được trang" }),
            notFound ? null : el("p", { class: "sub", text: errorText(error) }),
            el("a", { href: "#/auctions", text: "← Về danh sách phiên" })));
    }
}

if (USE_MOCK) document.getElementById("mock-note").hidden = false;
window.addEventListener("hashchange", render);
render();
