// LiveBid web client (HTML + JS thuần, không framework).
//
//   REST (/api)      : đăng nhập, danh sách phiên, lịch sử, quản trị, thống kê.
//   WebSocket (/ws)  : vào phòng, đặt giá, nhận sự kiện real-time.
// Định dạng thông điệp WebSocket giống TCP (xem README gốc, mục Giao tiếp).
//
// Cấu trúc: mỗi màn hình là một module "view" đăng ký với router.js.
//   Lõi (luôn có)        : login.js, list.js, room.js
//   Tính năng mở rộng    : mine.js, admin.js, stats.js — nạp tùy chọn bên dưới; thiếu file nào thì bỏ qua
//                          file đó, các màn hình còn lại vẫn chạy bình thường.

import login from "./login.js";
import list from "./list.js";
import room from "./room.js";
import { registerView, start } from "./router.js";

const OPTIONAL_VIEWS = ["./mine.js", "./admin.js", "./stats.js"];

for (const view of [login, list, room]) {
    registerView(view);
}

// Nạp xong các tính năng mở rộng rồi mới chạy router, để mở thẳng một đường dẫn như #/admin không bị "không tìm thấy".
await Promise.allSettled(OPTIONAL_VIEWS.map(async (path) => {
    try {
        const module = await import(path);
        registerView(module.default);
    } catch (error) {
        console.info(`Bỏ qua tính năng chưa có: ${path}`, error?.message ?? "");
    }
}));

start();
