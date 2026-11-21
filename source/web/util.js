// Hàm tiện ích dùng chung cho mọi màn hình.
//
// Quy ước an toàn (chống XSS): dữ liệu từ server hoặc người dùng luôn được đưa vào DOM bằng textContent
// (qua el() bên dưới), không bao giờ dùng innerHTML.

/**
 * Tạo phần tử DOM. {@code attrs}: class, text, on<Sự kiện>, value, disabled... ; các phần tử con có thể là
 * chuỗi (đưa vào bằng text node), Node, mảng, hoặc null/false (bị bỏ qua).
 */
export function el(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs || {})) {
        if (value === undefined || value === null || value === false) {
            continue;
        }
        if (key === "class") {
            node.className = value;
        } else if (key === "text") {
            node.textContent = value;
        } else if (key === "value") {
            node.value = value;
        } else if (key.startsWith("on") && typeof value === "function") {
            node.addEventListener(key.slice(2).toLowerCase(), value);
        } else if (value === true) {
            node.setAttribute(key, "");
        } else {
            node.setAttribute(key, String(value));
        }
    }
    for (const child of children.flat()) {
        if (child === null || child === undefined || child === false) {
            continue;
        }
        node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
}

/** Xóa hết nội dung một phần tử. */
export function clear(node) {
    node.replaceChildren();
    return node;
}

const MONEY = new Intl.NumberFormat("vi-VN");

/** 280000 → "280.000đ". */
export function formatMoney(amount) {
    return MONEY.format(amount) + "đ";
}

/** 280000 → "280.000" (không có đơn vị, dùng để điền vào ô nhập). */
export function formatNumber(amount) {
    return MONEY.format(amount);
}

/** Giờ địa phương của người xem, ví dụ "21:30 17/11/2026"; {@code seconds: true} thêm giây (lịch sử bid). */
export function formatTime(epochMillis, { seconds = false } = {}) {
    return new Date(epochMillis).toLocaleString("vi-VN", {
        hour: "2-digit", minute: "2-digit", second: seconds ? "2-digit" : undefined,
        day: "2-digit", month: "2-digit", year: "numeric",
    });
}

/** Khoảng thời gian còn lại: "04:59", "1 ngày 02:03:04". Âm hoặc 0 thì là "00:00". */
export function formatDuration(millis) {
    const totalSeconds = Math.max(0, Math.floor(millis / 1000));
    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;
    const pad = (n) => String(n).padStart(2, "0");
    const clock = hours > 0 || days > 0 ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
    return days > 0 ? `${days} ngày ${clock}` : clock;
}

/**
 * Đọc số tiền người dùng nhập. Chỉ nhận số nguyên, có thể có dấu chấm ngăn cách hàng nghìn
 * ("200000" hoặc "200.000"). Mọi dạng khác (dấu phẩy, chữ, số thập phân) trả NaN để không hiểu nhầm
 * "200.5" thành 2005.
 */
export function parseMoneyInput(text) {
    const compact = String(text ?? "").replace(/\s+/g, "");
    if (!/^(\d+|\d{1,3}(\.\d{3})+)$/.test(compact)) {
        return Number.NaN;
    }
    const value = Number(compact.replaceAll(".", ""));
    return Number.isSafeInteger(value) && value > 0 ? value : Number.NaN;
}

/**
 * Chữ cái đầu cho ảnh đại diện: chữ cái đầu tiên của hai từ cuối có chữ ("Cao Công Hoè" → "CH",
 * "Hoè (Desktop)" → "HD"). Bỏ qua dấu câu, số; không có chữ nào thì "?".
 */
export function initials(name) {
    const letters = String(name ?? "").trim().split(/\s+/)
        .map((word) => word.match(/\p{L}/u)?.[0])
        .filter(Boolean);
    return letters.slice(-2).join("").toUpperCase() || "?";
}

export const STATUS_LABEL = {
    SCHEDULED: "Sắp diễn ra",
    ACTIVE: "Đang diễn ra",
    FINISHED: "Đã kết thúc",
};

/** Nhãn trạng thái dạng huy hiệu màu. */
export function statusBadge(status) {
    return el("span", { class: `badge badge-${String(status).toLowerCase()}`, text: STATUS_LABEL[status] ?? status });
}

/**
 * UUID v4 cho requestId. crypto.randomUUID chỉ có trong "secure context" (HTTPS hoặc localhost);
 * khi mở trang qua http://192.168.x.x trong LAN thì không có, nên phải tự tạo từ getRandomValues.
 */
export function uuid() {
    if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
        return crypto.randomUUID();
    }
    const bytes = new Uint8Array(16);
    crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0"));
    return `${hex.slice(0, 4).join("")}-${hex.slice(4, 6).join("")}-${hex.slice(6, 8).join("")}-`
        + `${hex.slice(8, 10).join("")}-${hex.slice(10).join("")}`;
}

/** Epoch ms → giá trị cho {@code <input type="datetime-local">} (giờ địa phương). */
export function toLocalInput(epochMillis) {
    const d = new Date(epochMillis);
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Giá trị của {@code <input type="datetime-local">} → epoch ms; NaN nếu trống. */
export function fromLocalInput(text) {
    return text ? new Date(text).getTime() : Number.NaN;
}

/** Thông báo nổi ở góc màn hình, tự biến mất sau vài giây. {@code kind}: info | ok | warn | error. */
export function toast(message, kind = "info", durationMs = 5000) {
    let box = document.getElementById("toasts");
    if (!box) {
        box = el("div", { id: "toasts", "aria-live": "polite" });
        document.body.append(box);
    }
    const node = el("div", { class: `toast toast-${kind}`, role: "status", text: message });
    box.append(node);
    setTimeout(() => node.remove(), durationMs);
}
