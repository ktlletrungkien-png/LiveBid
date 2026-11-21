# Thay đổi trên nhánh `feature/web` (so với `main`)

Tài liệu này cho cả team biết nhánh đã đổi gì, đang ở trạng thái nào và cần làm gì tiếp.
Ngày: 22/11/2026.

## 1. Tóm tắt

- Nhánh hơn `main` 1 commit (`617f9e2`) cùng nhiều thay đổi **chưa commit**.
- Phần **giao diện web** đã được dựng lại hoàn toàn theo `DESIGN.md` (phong cách Stripe), chạy bằng **dữ liệu mẫu**, chưa nối API thật.
- Phần **Java (server, client, common, tests)** có thay đổi về hồ sơ người dùng và giao diện Swing, mô tả ở mục 4.

## 2. Web: giao diện mới (`source/web`)

### Cách chạy
Mở `index.html` bằng Live Server (hoặc `python -m http.server` trong `source/web`). Không cần build, không framework, không CDN, không font ngoài.

### Tài khoản thử (chỉ khi `USE_MOCK = true`)

| Vai trò | Tên đăng nhập | Mật khẩu |
|---|---|---|
| USER | `nguyenvana` | `matkhau123` |
| ADMIN | `admin` | `admin12345` |

Trang đăng nhập có sẵn hai nút điền nhanh hai tài khoản này.

### Các màn hình (router theo hash)

| Đường dẫn | Nội dung |
|---|---|
| `#/login` | Đăng nhập / Đăng ký (kiểm tra từng ô, đăng ký luôn tạo USER) |
| `#/auctions` | Lưới phiên, lọc theo trạng thái, phân trang, đồng hồ đếm ngược |
| `#/auction/:id` | Phòng đấu giá: giá hiện tại, đặt giá, lịch sử 20 bid, trạng thái kết nối |
| `#/create` | Đăng phiên (USER chờ duyệt, ADMIN lên lịch ngay) |
| `#/mine` | Phiên của tôi: "Đã tham gia / Đã thắng" và "Phiên tôi đăng" (Sửa, Hủy) |
| `#/admin` | Chỉ ADMIN: hàng chờ duyệt (Duyệt / Từ chối), tạo phiên, thống kê |

Quy tắc đã cài trong giao diện: USER không vào được `#/admin`; ADMIN và chủ phiên không đặt giá được; phiên chờ duyệt / bị từ chối chỉ chủ phiên và ADMIN xem; đặt giá trong 10 giây cuối thì phiên gia hạn 30 giây (tối đa 10 lần); mất kết nối thì khóa nút đặt giá.

### File

| File | Trạng thái | Ghi chú |
|---|---|---|
| `index.html` | Viết lại | Khung trang + CSS bố cục bổ sung (màu và component vẫn dùng `style.css`) |
| `app.js` | Viết lại | Toàn bộ giao diện và router. Dữ liệu chèn bằng `textContent`, không dùng `innerHTML` |
| `api.js` | Viết lại | Đối tượng `api` (REST) và `socket` (WebSocket giả lập). Cờ `USE_MOCK` ở đầu file |
| `style.css` | Giữ nguyên | Không sửa trong đợt này |
| `DESIGN.md` | Mới (chưa add) | Tài liệu phong cách thiết kế |

### Nối API thật
Đổi `USE_MOCK = false` trong `api.js`. Mỗi hàm của `api` có chú thích endpoint tương ứng (`/api/login`, `/api/auctions`, `/api/auctions/{id}/bids`, `/api/me/auctions`, `/api/auctions/pending`, `/api/stats`…). Khi đó `api` gọi `fetch` theo `location.host`, gửi `Authorization: Bearer <token>` và hiển thị `error.message` từ phản hồi lỗi dạng `{"error":{"code","message","details"}}`.

Chưa làm: kết nối WebSocket thật. Đặt giá và sự kiện real-time hiện là `socket` giả lập trong `api.js` (người khác tự đặt giá mỗi 6–14 giây, có nút "Giả lập mất kết nối").

## 3. Web: hai bản đang tồn tại song song (CHƯA CHỐT HƯỚNG)

Trong `source/web` hiện có **hai bản web**:

- **Bản thật (đã có trong commit `617f9e2`)**: `router.js`, `login.js`, `list.js`, `room.js`, `mine.js`, `admin.js`, `stats.js`, `profile.js` (chưa add), `socket.js`, `util.js`. Bản này nối REST `/api` và WebSocket `/ws` của server (server phục vụ chính thư mục `source/web`). Đây là phần **đang liên quan trực tiếp tới hệ thống, không phải code chết**.
- **Bản dữ liệu mẫu (mới)**: `index.html`, `app.js`, `api.js` như mục 2. Chạy độc lập, không cần server.

Vì `index.html` / `app.js` mới thay cách nạp cũ, **trang hiện chỉ chạy bản dữ liệu mẫu**; bản thật không được nạp. Hai bản cũng không chạy chung được vì `api.js` mới không còn `export` / `session` mà các file cũ import.

- Bản cũ của `index.html`, `app.js`, `api.js` vẫn lấy lại được từ commit `617f9e2` (`git show 617f9e2:source/web/api.js`). Phần sửa chưa commit của 3 file đó trước khi ghi đè thì không còn.
- **Đừng xóa** các file bản thật cho tới khi team chốt hướng. Hai hướng đang cân nhắc:
  1. Giữ bản cũ làm bản chạy thật; đưa giao diện mới sang `preview.*` để xem thử.
  2. Biến bản mới thành bản thật (nối REST/WebSocket dựa trên `socket.js`), sau đó bỏ dần bản cũ.
- Phần hồ sơ người dùng (`profile.js`, mục 4) chưa có trong giao diện mới.

## 4. Các thay đổi khác trên nhánh (Java, tài liệu)

Phần này lấy từ `git diff --stat main`, chưa được rà soát chi tiết trong đợt web này.

- **server**: thêm chức năng hồ sơ người dùng (`AuthServiceImpl`, `AuthService`, `UserRepository`, `SqliteUserRepository`, `InMemoryUserRepository`, `AuthCommands`, `HttpGateway`).
- **common**: `MessageType` thêm 2 loại thông điệp; file mới `UpdateProfilePayload.java` (chưa add).
- **client (Swing)**: đổi giao diện (`MainFrame`, `LoginPanel`, `RoomPanel`, `AuctionListPanel`, `MyAuctionsPanel`, `ConnectPanel`, `AuctionFormDialog`, `ClientMain`); file mới chưa add: `ProfileDialog`, `RoundButtonUI`, `UiTheme`, `UnderlineTabbedPaneUI`, thư mục `resources/` và test `client/ui`.
- **tests**: thêm test cho auth, SQLite, REST, WebSocket.
- **tài liệu**: `README.md` gốc, `server/README.md`, `web/README.md`.

## 5. Việc cần làm tiếp

1. Chốt hướng web (mục 3); chỉ dọn file sau khi đã chốt.
2. Chạy test Java (`source/tests`), vì chưa chạy sau các thay đổi.
3. Add các file chưa được theo dõi nếu cần giữ (`DESIGN.md`, `profile.js`, file Java mới của client, `UpdateProfilePayload.java`); cân nhắc `.gitignore` cho `source/.claude/`.
4. Nối API thật (`USE_MOCK = false`) và WebSocket cho phòng đấu giá.
5. Kiểm tra giao diện web trên trình duyệt và ở bề rộng điện thoại. Mới kiểm tra cú pháp, logic `api` mẫu bằng Node và dựng trang đăng nhập; các màn còn lại chưa chạy thử trên trình duyệt thật.
6. Cập nhật `source/web/README.md` cho khớp cấu trúc web mới.
