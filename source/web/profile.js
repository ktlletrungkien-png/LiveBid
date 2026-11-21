// "Thông tin tài khoản" (REST PUT /api/me): đổi tên hiển thị và (tùy chọn) đổi mật khẩu.
// Mở từ menu tài khoản ở góc phải (bấm vào ảnh đại diện → "Cập nhật thông tin").

import { api, session } from "./api.js";
import { el, initials, toast } from "./util.js";

const MAX_DISPLAY_NAME = 50;
const MIN_PASSWORD = 8;

export default {
    route: "profile",
    title: "Thông tin tài khoản",
    nav: false,
    render(container) {
        let disposed = false;
        const user = session.user;

        const avatar = el("span", { class: "avatar avatar-xl", "aria-hidden": "true", text: initials(user.displayName) });
        const nameText = el("strong", { class: "profile-name", text: user.displayName });
        const displayName = el("input", {
            name: "displayName", type: "text", autocomplete: "nickname", maxlength: MAX_DISPLAY_NAME,
            value: user.displayName,
        });
        const current = el("input", { name: "currentPassword", type: "password", autocomplete: "current-password" });
        const fresh = el("input", { name: "newPassword", type: "password", autocomplete: "new-password" });
        const confirm = el("input", { name: "confirmPassword", type: "password", autocomplete: "new-password" });
        const message = el("p", { class: "form-message", role: "status" });
        const submit = el("button", { type: "submit", class: "primary", text: "Lưu thay đổi" });

        function field(label, input, hint) {
            return el("label", {}, el("span", { text: label }), input,
                hint ? el("small", { class: "field-hint", text: hint }) : null);
        }

        function show(text, kind) {
            message.textContent = text;
            message.className = `form-message ${kind}`;
        }

        const form = el("form", { class: "card profile-form", novalidate: true },
            el("div", { class: "profile-head" },
                avatar,
                el("div", {},
                    nameText,
                    el("span", {
                        class: "muted",
                        text: `@${user.username} · ${user.role === "ADMIN" ? "Quản trị viên" : "Người dùng"}`,
                    }))),
            el("h3", { text: "Thông tin hiển thị" }),
            field("Tên hiển thị", displayName, "Tên này hiện cho mọi người trong phòng đấu giá và lịch sử đặt giá."),
            el("h3", { text: "Đổi mật khẩu (không bắt buộc)" }),
            field("Mật khẩu hiện tại", current),
            field("Mật khẩu mới", fresh, "Ít nhất 8 ký tự. Để trống nếu không muốn đổi."),
            field("Nhập lại mật khẩu mới", confirm),
            message,
            el("div", { class: "actions" }, submit, el("a", { href: "#/auctions", class: "button-link", text: "Quay lại" })),
        );

        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const name = displayName.value.trim();
            const changePassword = fresh.value !== "" || confirm.value !== "";
            if (!name) {
                show("Tên hiển thị không được để trống.", "error");
                return;
            }
            if (name.length > MAX_DISPLAY_NAME) {
                show(`Tên hiển thị tối đa ${MAX_DISPLAY_NAME} ký tự.`, "error");
                return;
            }
            if (changePassword) {
                if (!current.value) {
                    show("Hãy nhập mật khẩu hiện tại để đổi mật khẩu.", "error");
                    return;
                }
                if (fresh.value.length < MIN_PASSWORD) {
                    show(`Mật khẩu mới phải có ít nhất ${MIN_PASSWORD} ký tự.`, "error");
                    return;
                }
                if (fresh.value !== confirm.value) {
                    show("Hai lần nhập mật khẩu mới không khớp.", "error");
                    return;
                }
            }
            submit.disabled = true;
            show("Đang lưu…", "");
            try {
                const updated = await api.updateProfile(name,
                    changePassword ? current.value : undefined, changePassword ? fresh.value : undefined);
                if (disposed) {
                    return;
                }
                session.updateUser(updated);
                nameText.textContent = updated.displayName;
                avatar.textContent = initials(updated.displayName);
                displayName.value = updated.displayName;
                current.value = "";
                fresh.value = "";
                confirm.value = "";
                show(changePassword ? "Đã cập nhật thông tin và đổi mật khẩu." : "Đã cập nhật thông tin.", "ok");
                toast("Đã cập nhật thông tin tài khoản.", "ok");
            } catch (error) {
                if (!disposed) {
                    show(error.message, "error");
                }
            } finally {
                submit.disabled = false;
            }
        });

        container.append(
            el("h2", { text: "Thông tin tài khoản" }),
            el("p", { class: "muted", text: "Tên đăng nhập và vai trò không đổi được." }),
            form,
        );
        return () => {
            disposed = true;
        };
    },
};
