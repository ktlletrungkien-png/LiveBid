// Bộ định tuyến theo hash (#/auctions, #/auction/5, ...) và thanh điều hướng.
//
// Mỗi màn hình là một "view": { route, title, nav, order, requires, render(container, params) }.
// render chạy đồng bộ và có thể trả về một hàm dọn dẹp (ngắt WebSocket, dừng bộ đếm giờ...) để router
// gọi khi chuyển sang màn hình khác. Công việc bất đồng bộ trong view phải tự kiểm tra "đã bị dọn dẹp chưa".

import { api, session } from "./api.js";
import { clear, el, initials, toast } from "./util.js";

const views = new Map();
let cleanup = null;
let started = false;

/** Đăng ký một màn hình. Màn hình đăng ký muộn (tính năng nạp tùy chọn) vẫn hiện trên thanh điều hướng. */
export function registerView(view) {
    views.set(view.route, view);
    if (started) {
        renderNav();
        // Người dùng đang mở thẳng trang này (ví dụ tải lại #/admin) khi view chưa kịp đăng ký.
        if (parseHash().name === view.route) {
            route();
        }
    }
}

export function navigate(hash) {
    if (location.hash === hash) {
        route();
    } else {
        location.hash = hash;
    }
}

export function start() {
    started = true;
    window.addEventListener("hashchange", route);
    // Menu tài khoản đóng khi bấm ra ngoài hoặc nhấn Esc.
    document.addEventListener("click", (event) => {
        if (!event.target.closest?.(".user-menu")) {
            closeMenus();
        }
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closeMenus();
        }
    });
    session.onChange((reason) => {
        renderNav();
        if (!session.user) {
            if (reason === "expired") {
                toast("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.", "warn");
            }
            route();
        }
    });
    renderNav();
    route();
}

function parseHash() {
    const parts = location.hash.replace(/^#\/?/, "").split("/").filter(Boolean);
    return { name: parts[0] ?? "auctions", params: parts.slice(1) };
}

function route() {
    if (cleanup) {
        try {
            cleanup();
        } catch (error) {
            console.error("Lỗi khi dọn dẹp màn hình cũ", error);
        }
        cleanup = null;
    }
    const container = document.getElementById("app");
    const { name, params } = parseHash();
    const user = session.user;

    if (!user && name !== "login") {
        location.hash = "#/login";
        return;
    }
    if (user && name === "login") {
        location.hash = "#/auctions";
        return;
    }
    const view = views.get(name);
    clear(container);
    renderNav();
    if (!view) {
        container.append(el("p", { class: "muted", text: "Đang tải…" }));
        return;
    }
    if (view.requires === "ADMIN" && user?.role !== "ADMIN") {
        toast("Chỉ tài khoản quản trị được vào trang này.", "warn");
        location.hash = "#/auctions";
        return;
    }
    document.title = `${view.title} - LiveBid`;
    const result = view.render(container, params);
    cleanup = typeof result === "function" ? result : null;
}

function renderNav() {
    const nav = document.getElementById("nav");
    const box = document.getElementById("user-box");
    if (!nav || !box) {
        return;
    }
    const user = session.user;
    const current = parseHash().name;
    clear(nav);
    clear(box);
    if (!user) {
        return;
    }
    const visible = [...views.values()]
        .filter((view) => view.nav && (view.requires !== "ADMIN" || user.role === "ADMIN"))
        .sort((a, b) => (a.order ?? 100) - (b.order ?? 100));
    for (const view of visible) {
        nav.append(el("a", {
            href: `#/${view.route}`,
            class: view.route === current ? "active" : null,
            text: view.title,
        }));
    }
    box.append(userMenu(user));
}

/**
 * Ảnh đại diện + tên ở góc phải. Bấm vào mở menu: thông tin tài khoản, "Cập nhật thông tin", "Đăng xuất".
 */
function userMenu(user) {
    const role = user.role.toLowerCase();
    const menu = el("div", { class: "menu", role: "menu", hidden: true },
        el("div", { class: "menu-head" },
            el("span", { class: "avatar avatar-lg", "aria-hidden": "true", text: initials(user.displayName) }),
            el("div", {},
                el("strong", { text: user.displayName }),
                el("span", { class: "muted", text: `@${user.username}` }))),
        el("a", { href: "#/profile", role: "menuitem", class: "menu-item", text: "Cập nhật thông tin", onclick: closeMenus }),
        el("button", { type: "button", role: "menuitem", class: "menu-item danger", text: "Đăng xuất", onclick: logout }),
    );
    const trigger = el("button", {
        type: "button",
        class: "user-trigger",
        "aria-haspopup": "menu",
        "aria-expanded": "false",
        title: "Tài khoản",
        onclick: () => {
            const open = menu.hidden;
            closeMenus();
            if (open) {
                menu.hidden = false;
                trigger.setAttribute("aria-expanded", "true");
                menu.querySelector(".menu-item")?.focus();
            }
        },
    },
        el("span", { class: "avatar", "aria-hidden": "true", text: initials(user.displayName) }),
        el("span", { class: "user-name", text: user.displayName }),
        el("span", { class: `badge badge-role-${role}`, text: user.role }),
        el("span", { class: "caret", "aria-hidden": "true" }),
    );
    return el("div", { class: "user-menu" }, trigger, menu);
}

function closeMenus() {
    document.querySelectorAll(".user-menu .menu").forEach((menu) => {
        menu.hidden = true;
    });
    document.querySelectorAll(".user-trigger").forEach((button) => button.setAttribute("aria-expanded", "false"));
}

async function logout() {
    closeMenus();
    try {
        await api.logout();
    } catch {
        // Token có thể đã hết hạn; dù sao cũng bỏ đăng nhập ở phía trình duyệt.
    }
    session.clear();
}
