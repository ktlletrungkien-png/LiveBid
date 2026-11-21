# MODULE WEB

Trang web HTML + CSS + JavaScript thuần (không framework, không thư viện CDN), do chính Auction Server phục vụ
qua cổng HTTP 8080.

- REST (`/api/...`): đăng nhập, danh sách phiên, phiên của tôi, quản trị, thống kê.
- WebSocket (`/ws`): vào phòng, đặt giá, nhận sự kiện real-time.

## 🚀 Cách mở

1. Chạy server (từ thư mục `source/`): `mvn -pl server exec:java`.
2. Mở trình duyệt tại `http://localhost:8080/` (cùng máy) hoặc `http://<IP server>:8080/` (máy khác trong LAN).
3. Đăng ký tài khoản USER ngay trên trang. Tài khoản ADMIN tạo bằng `mvn -pl server exec:java@create-admin`
   (xem `source/server/README.md`).

Server tìm thư mục web theo khóa `http.webRoot` (mặc định `web`, tính từ thư mục chạy `source/`).

## 🖥️ Các màn hình

| Màn hình | Đường dẫn | Dùng | Nội dung |
|---|---|---|---|
| Đăng nhập / Đăng ký | `#/login` | REST | Lưu token và tài khoản trong `sessionStorage` của tab |
| Phiên đấu giá | `#/auctions` | REST | Lọc theo trạng thái, phân trang, làm mới; bấm một dòng để vào phòng |
| Phòng đấu giá | `#/auction/<id>` | WebSocket | Giá, người dẫn đầu, đồng hồ đếm ngược, đặt giá, lịch sử, thông báo |
| Phiên của tôi | `#/mine` | REST | Phiên đã tham gia / đã thắng |
| Quản trị (ADMIN) | `#/admin` | REST | Tạo phiên, sửa phiên chưa bắt đầu |
| Thống kê | `#/stats` | REST | Số liệu từ Settlement; hiện "Dịch vụ thống kê chưa chạy" khi Settlement tắt |
| Thông tin tài khoản | `#/profile` | REST | Đổi tên hiển thị; đổi mật khẩu (cần mật khẩu hiện tại) |

**Menu tài khoản:** bấm vào ảnh đại diện + tên ở góc phải thanh trên cùng để mở menu gồm "Cập nhật thông tin"
và "Đăng xuất". Menu đóng khi bấm ra ngoài hoặc nhấn Esc. Ảnh đại diện là chữ cái đầu của hai từ cuối trong tên
("Cao Công Hoè" → "CH"), cùng quy tắc với desktop client.

### Phòng đấu giá

- Vào phòng: mở `/ws` → `AUTH` (token) → `JOIN_AUCTION` → nhận snapshot (phiên + 20 bid gần nhất).
- Hiển thị **"Giá khởi điểm"** khi chưa có ai đặt, **"Giá đã được đặt"** khi đã có bid.
- Bỏ qua mọi cập nhật có `version` nhỏ hơn hoặc bằng bản đang hiển thị.
- Đồng hồ: `offset = serverTime - Date.now()` khi nhận `AuctionView`; còn lại = `endsAt - (Date.now() + offset)`,
  cập nhật mỗi 250 ms. Không dùng giờ máy làm mốc tuyệt đối.
- Ô đặt giá điền sẵn mức tối thiểu. Bị từ chối `BID_TOO_LOW` thì điền lại `details.minimumAllowed`.
  Quá 10 giây không có trả lời (hoặc mất kết nối giữa chừng) → **"Chưa xác định kết quả"**, không tự coi là thất bại.
- Thông báo **"Bạn đã bị vượt giá"**, **"Phiên được gia hạn"**, người thắng hoặc **"Chưa có người thắng"**.
- Trạng thái kết nối luôn hiện. Mất kết nối → khóa nút đặt giá, tự kết nối lại sau 1, 2, 4… tối đa 10 giây,
  gửi lại `AUTH` + `JOIN_AUCTION` để nhận snapshot mới. Gửi `PING` mỗi 15 giây.

## 🎨 Thiết kế

**Ý tưởng:** phòng đấu giá đổi màu theo vị trí của bạn, để nhìn một cái là biết mình đang ở đâu mà không cần đọc chữ.

| Vị trí | Ô giá |
|---|---|
| Đang xem, chưa đặt giá | Xanh đêm |
| Đang dẫn đầu, hoặc đã thắng | Xanh lục |
| Đã đặt nhưng bị vượt giá | Đỏ |
| Phiên chưa bắt đầu, hoặc đã kết thúc không thắng | Xám xanh |

Ở 10 giây cuối (cùng cửa sổ chống bắn tỉa của server), đồng hồ chuyển đỏ và nháy. Hiệu ứng chuyển động tắt khi hệ điều hành bật "giảm chuyển động".

- **Màu:** nền phấn xám xanh, chữ và đầu trang màu xanh đêm, nút chính màu nghệ (như tấm paddle đấu giá). Màu theo vai trò (`--lead`, `--outbid`, `--saffron`), không theo tên màu.
- **Chữ:** một họ duy nhất, Archivo. Số tiền, đồng hồ và số lô đặt ở độ rộng 62%, đậm 900, chữ số đều nét; chữ thường ở độ rộng 100%. Nhãn viết thường, không viết hoa.
- **Danh sách:** là mục lục lô (số lô, tên, giá, đồng hồ đếm ngược ngay trên từng dòng), không chia thành các thẻ giống nhau.
- **Điện thoại:** danh mục lô xếp lại thành từng dòng, ô đặt giá dính ở đáy màn hình để đặt giá bằng một tay.
- **Nút chọn nhanh** trong ô đặt giá: "Tối thiểu", "+1 bước", "+5 bước", "+10 bước".

## 📦 Cấu trúc

| File | Nội dung |
|---|---|
| `index.html` | Khung trang: thanh điều hướng, vùng nội dung |
| `app.js` | Điểm vào: đăng ký các màn hình, nạp tính năng mở rộng, khởi động router |
| `router.js` | Định tuyến theo `#/...`, thanh điều hướng, đăng xuất |
| `api.js` | Gọi REST, lưu phiên đăng nhập, lỗi `ApiError` |
| `socket.js` | `LiveSocket`: kết nối `/ws`, AUTH, ghép RESPONSE theo `requestId`, PING, tự kết nối lại |
| `util.js` | Dựng DOM an toàn, định dạng tiền/thời gian, đọc số tiền nhập, thông báo nổi |
| `login.js`, `list.js`, `room.js`, `profile.js` | Màn hình đăng nhập, danh sách phiên, phòng đấu giá, thông tin tài khoản |
| `mine.js`, `admin.js`, `stats.js` | Phiên của tôi, quản trị, thống kê (nạp tùy chọn: thiếu file nào thì bỏ qua) |
| `style.css` | Toàn bộ giao diện (tự đổi theo chế độ sáng/tối của hệ điều hành); bảng màu khai báo ở `:root` |
| `fonts/` | Archivo (font biến thiên, đủ tiếng Việt), tự host dạng woff2, không dùng CDN. Giấy phép SIL OFL trong `fonts/OFL-Archivo.txt` |

## 🔒 An toàn

- Dữ liệu từ server và người dùng luôn được chèn bằng `textContent` (hàm `el()` trong `util.js`), không dùng
  `innerHTML`, nên tên sản phẩm kiểu `<img onerror=...>` chỉ hiện ra như chữ.
- Danh tính khi đặt giá do server lấy từ kết nối đã `AUTH`; trang không gửi `userId` hay `role`.
- `requestId` là UUID ngẫu nhiên. Khi mở trang qua `http://<IP LAN>` (không phải "secure context"),
  `crypto.randomUUID` không có nên trang tự tạo UUID từ `crypto.getRandomValues`.

## 🧪 Kiểm tra tay

1. Mở hai trình duyệt (một cửa sổ ẩn danh), đăng nhập hai tài khoản USER khác nhau.
2. Cả hai vào cùng một phiên đang diễn ra; một bên đặt giá → bên kia cập nhật giá, lịch sử ngay, không cần tải lại.
3. Bên vừa bị vượt thấy "Bạn đã bị vượt giá".
4. Ngắt mạng của máy trình duyệt → trạng thái "Mất kết nối", nút đặt giá bị khóa; có mạng lại → tự vào lại phòng.
5. Tắt server → "Mất kết nối"; bật lại server → vì token mất khi server khởi động lại, trang báo
   "Phiên đăng nhập đã hết hạn" và về màn hình đăng nhập.
6. Chụp màn hình lưu vào `statics/` với tên `web_*.png`.

## 📝 Ghi chú

- Token chỉ lưu trong bộ nhớ server: **khởi động lại server thì mọi token mất**, trang tự chuyển về màn hình
  đăng nhập với thông báo "Phiên đăng nhập đã hết hạn". Mất mạng ngắn khi server vẫn chạy thì tự vào lại phòng.
- `sessionStorage` riêng cho từng tab: mỗi tab có thể đăng nhập một tài khoản khác nhau.
- Phát hiện "bị vượt giá" so sánh theo tên hiển thị; nếu hai người trùng tên hiển thị có thể báo nhầm.
