# MODULE WEB

Trang web HTML + JS thuần (không framework), được server phục vụ qua cổng HTTP 8080.

- REST (`/api/...`): đăng nhập, danh sách phiên, lịch sử bid, thống kê.
- WebSocket (`ws://<server>:8080/ws`): xác thực bằng token, vào phòng, đặt giá, nhận sự kiện real-time.

| File | Nội dung |
|---|---|
| `index.html` | Khung giao diện: đăng nhập, danh sách phiên, phòng đấu giá |
| `app.js` | Gọi REST, quản lý kết nối WebSocket |
| `style.css` | Giao diện |

Mở trình duyệt tại `http://<IP server>:8080/` sau khi server chạy.
