// Bộ định tuyến theo hash (#/auctions, #/auction/5, ...) và thanh điều hướng.
//
// Mỗi màn hình là một "view": { route, title, nav, order, requires, render(container, params) }.
// render chạy đồng bộ và có thể trả về một hàm dọn dẹp (ngắt WebSocket, dừng bộ đếm giờ...) để router
// gọi khi chuyển sang màn hình khác. Công việc bất đồng bộ trong view phải tự kiểm tra "đã bị dọn dẹp chưa".

import { api, session } from "./api.js";
import { clear, el, toast } from "./util.js";

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
    box.append(
        el("span", { class: "user-name", text: user.displayName }),
        el("span", { class: `badge badge-role-${user.role.toLowerCase()}`, text: user.role }),
        el("button", { type: "button", class: "link-button", text: "Đăng xuất", onclick: logout }),
    );
}

async function logout() {
    try {
        await api.logout();
    } catch {
        // Token có thể đã hết hạn; dù sao cũng bỏ đăng nhập ở phía trình duyệt.
    }
    session.clear();
}
