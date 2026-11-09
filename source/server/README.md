# MODULE SERVER

## 🎯 MỤC TIÊU

Auction Server là nơi **duy nhất** quyết định chấp nhận giá. Server chịu trách nhiệm:

- Nhận kết nối TCP từ desktop client (giao thức JSON một dòng, kết thúc `\n`).
- Cung cấp REST API và WebSocket cho trình duyệt (Javalin, cổng 8080).
- Trả lời UDP broadcast tìm server; gửi UDP multicast "phiên sắp bắt đầu".
- Xử lý nghiệp vụ đấu giá qua một `single-thread executor`, lưu SQLite.
- Gọi dịch vụ Settlement qua RMI khi phiên kết thúc (có hàng đợi retry).

## ⚙️ CÔNG NGHỆ SỬ DỤNG

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 |
| TCP / UDP / RMI | `java.net`, `java.rmi` (JDK) |
| REST + WebSocket | Javalin |
| JSON | Gson |
| Database | SQLite (sqlite-jdbc) |

## 🚀 HƯỚNG DẪN CHẠY

```bash
cd source
mvn -q install -DskipTests          # build toàn bộ module (lần đầu)
mvn -q -pl server exec:java          # chạy server
```

Cấu hình: sao chép `source/config/server.properties.example` thành `source/config/server.properties`.

| Cổng | Giao thức | Mục đích |
|---|---|---|
| 5000 | TCP | Desktop client |
| 8080 | HTTP / WebSocket | REST API, `ws://<server>:8080/ws` |
| 5001 | UDP | Discovery (broadcast) |
| 239.255.42.99:5002 | UDP multicast | Thông báo phiên sắp bắt đầu |

## 🔗 API

(Bổ sung khi triển khai — xem mục 10 kế hoạch.)

| Endpoint | Method | Mô tả | Quyền |
|---|---|---|---|
| `/api/register`, `/api/login` | POST | Đăng ký, đăng nhập | Công khai |
| `/api/auctions` | GET / POST | Danh sách / tạo phiên | Đăng nhập / ADMIN |
| `/api/auctions/{id}` | GET / PUT | Chi tiết / sửa phiên | Đăng nhập / ADMIN |
| `/api/auctions/{id}/bids` | GET | Lịch sử bid | Đăng nhập |
| `/api/me/auctions` | GET | Phiên của tôi | Đăng nhập |
| `/api/stats` | GET | Thống kê | Đăng nhập |

## 📦 CẤU TRÚC VÀ PHÂN CÔNG

```
server/src/main/java/com/livebid/server/
├── ServerMain.java
├── core/        # Hợp đồng chung + nghiệp vụ đấu giá            — Kiên
│   ├── AuctionService.java   # interface: mọi đọc/ghi đấu giá đi qua đây (single-thread executor)
│   ├── AuthService.java      # interface: đăng ký, đăng nhập, token  (Hoè cài đặt)
│   ├── ClientChannel.java    # một kết nối TCP hoặc WebSocket
│   ├── UserPrincipal.java    # người dùng đã đăng nhập trên kết nối
│   └── ServiceException.java # lỗi nghiệp vụ mang ErrorCode
├── model/       # Bản ghi DB: User, Auction, Bid, SettlementTask  — Kiên
├── repo/        # Interface repository + TransactionManager       — Kiên định nghĩa
│   └── memory/  # Bản trong bộ nhớ để test / phát triển song song
├── tcp/         # TcpServer, framing, ClientSession               — Kiên
├── db/          # Cài đặt SQLite của repo/*, migration            — Hoè
├── auth/        # Cài đặt AuthService (PBKDF2, token)             — Hoè
├── http/        # REST + WebSocket (Javalin)                      — Hoè
├── udp/         # Discovery broadcast, multicast thông báo         — Quân
└── settlement/  # SettlementClient (RMI) + retry                   — Quân
```

Quy ước:

- Chỉ `AuctionService` được đọc/ghi `AuctionRepository` và `BidRepository`. REST, WebSocket, TCP đều gọi `AuctionService`.
- Đổi chữ ký interface trong `core/` hoặc `repo/` phải qua Pull Request và báo cả nhóm.
- Mỗi người chỉ sửa package của mình; cần thay đổi ở package người khác thì nhờ người đó hoặc mở PR để họ duyệt.

## 📝 GHI CHÚ

- Chạy Settlement trước server nếu muốn ghi biên bản ngay; nếu Settlement chưa chạy, kết quả được xếp hàng và gửi bù.
- Trên Windows cần mở firewall cho các cổng trên (xem README gốc).
