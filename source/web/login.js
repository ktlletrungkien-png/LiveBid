// Màn hình đăng nhập / đăng ký (REST /api/login, /api/register).

import { api, session } from "./api.js";
import { el } from "./util.js";

export default {
    route: "login",
    title: "Đăng nhập",
    nav: false,
    render(container) {
        let mode = "login";
        const message = el("p", { class: "form-message", role: "alert" });
        const form = el("form", { class: "card auth-form", novalidate: true });
        const tabs = el("div", { class: "tabs", role: "tablist" });

        function setMode(next) {
            mode = next;
            message.textContent = "";
            draw();
        }

        function field(label, name, type, autocomplete) {
            return el("label", {},
                el("span", { text: label }),
                el("input", { name, type, autocomplete, required: true }));
        }

        function draw() {
            tabs.replaceChildren(
                el("button", {
                    type: "button", role: "tab", class: mode === "login" ? "active" : null,
                    "aria-selected": String(mode === "login"), text: "Đăng nhập", onclick: () => setMode("login"),
                }),
                el("button", {
                    type: "button", role: "tab", class: mode === "register" ? "active" : null,
                    "aria-selected": String(mode === "register"), text: "Đăng ký", onclick: () => setMode("register"),
                }),
            );
            const fields = [
                field("Tên đăng nhập", "username", "text", "username"),
                field("Mật khẩu", "password", "password", mode === "login" ? "current-password" : "new-password"),
            ];
            if (mode === "register") {
                fields.push(field("Tên hiển thị", "displayName", "text", "nickname"));
            }
            const hint = mode === "register"
                ? el("p", { class: "muted", text: "Tên đăng nhập 3–30 ký tự (chữ không dấu, số, dấu gạch dưới). Mật khẩu từ 8 ký tự." })
                : null;
            // replaceChildren biến null thành chữ "null", nên phải lọc bỏ trước.
            form.replaceChildren(...[
                ...fields,
                hint,
                message,
                el("button", { type: "submit", class: "primary", text: mode === "login" ? "Đăng nhập" : "Tạo tài khoản" }),
            ].filter(Boolean));
        }

        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const data = new FormData(form);
            const submit = form.querySelector("button[type=submit]");
            submit.disabled = true;
            message.textContent = "";
            try {
                const result = mode === "login"
                    ? await api.login(data.get("username").trim(), data.get("password"))
                    : await api.register(data.get("username").trim(), data.get("password"), data.get("displayName"));
                session.save(result);
                location.hash = "#/auctions";
            } catch (error) {
                message.textContent = error.message;
                submit.disabled = false;
            }
        });

        draw();
        container.append(
            el("div", { class: "login-hero" },
                el("div", { class: "hero-copy" },
                    // Hai dòng cố định để không bị ngắt dòng xấu (chữ "lúc." lẻ loi).
                    el("h2", { class: "hero-title" },
                        el("span", { class: "hero-line", text: "Đặt giá" }),
                        el("span", { class: "hero-line", text: "đúng lúc." })),
                    el("p", { class: "hero-sub muted", text: "Theo dõi giá và đồng hồ trực tiếp, đặt giá ngay trong phòng đấu giá." })),
                el("div", { class: "hero-form" }, tabs, form)),
        );
        return () => {};
    },
};
