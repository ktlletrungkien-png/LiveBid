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
mvn -pl server exec:java             # chạy server (không dùng -q: -q ẩn luôn log của server)
```

Server chạy đến khi bấm `Ctrl+C`. Khi sẵn sàng, log in ra dòng `TCP server lắng nghe tại 0.0.0.0:5000`.

Cấu hình: sao chép `source/config/server.properties.example` thành `source/config/server.properties`.
Server tìm file theo thứ tự: tham số dòng lệnh → `-Dlivebid.config=...` → `config/server.properties` →
`../config/server.properties`; không có file thì dùng giá trị mặc định.

| Cổng | Giao thức | Mục đích |
|---|---|---|
| 5000 | TCP | Desktop client |
| 8080 | HTTP / WebSocket | REST API, `ws://<server>:8080/ws` |
| 5001 | UDP | Discovery (broadcast) |
| 239.255.42.99:5002 | UDP multicast | Thông báo phiên sắp bắt đầu |

## 🔌 TẦNG TCP (desktop client, cổng 5000)

| Lớp | Nhiệm vụ |
|---|---|
| `TcpServer` | Mở cổng, nhận kết nối, giới hạn `network.maxConnections` (vượt thì trả `SERVER_BUSY` rồi đóng) |
| `ClientSession` | Một kết nối: luồng đọc riêng; hàng đợi gửi giới hạn 256 thông điệp + **một** luồng ghi; giữ trạng thái đăng nhập |
| `LineFrameDecoder` | Tách khung JSON một dòng kết thúc `\n`, tối đa 64 KiB, kiểm tra ngay khi đọc từng byte |
| `MessageRouter` | Kiểm tra phong bì, chuyển lệnh tới handler, đổi lỗi thành RESPONSE `success=false` |

Xử lý lỗi trên một kết nối:

| Tình huống | Server làm gì |
|---|---|
| JSON hỏng, thiếu trường, lệnh lạ | Trả `INVALID_MESSAGE`, kết nối **vẫn mở** |
| Thông điệp quá 64 KiB | Trả `INVALID_MESSAGE` rồi **đóng** kết nối đó; các kết nối khác không ảnh hưởng |
| Không nhận byte nào trong `network.idleTimeoutSeconds` | Đóng kết nối (client nên gửi `PING` định kỳ) |
| Client nhận quá chậm, hàng đợi gửi đầy | Đóng kết nối thay vì làm chậm executor |

Lệnh đã chạy: `PING`. Các lệnh khác hiện trả `INTERNAL_ERROR` "Server chưa hỗ trợ lệnh ..." cho tới khi
có handler.

Thử nhanh bằng PowerShell khi server đang chạy:

```powershell
$c = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 5000); $s = $c.GetStream()
$w = New-Object System.IO.StreamWriter($s); $w.NewLine = "`n"; $r = New-Object System.IO.StreamReader($s)
$w.WriteLine('{"protocolVersion":1,"kind":"REQUEST","type":"PING","requestId":"1"}'); $w.Flush(); $r.ReadLine()
$c.Close()
```

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
├── ServerMain.java   # đọc cấu hình, khởi động các thành phần
├── ServerConfig.java # đọc server.properties
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
