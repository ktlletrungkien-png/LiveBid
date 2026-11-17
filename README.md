# BÀI TẬP LỚN: LẬP TRÌNH MẠNG

## LiveBid — Hệ thống đấu giá trực tuyến thời gian thực đa giao thức

> 🚧 Dự án đang ở giai đoạn khởi tạo. Các mục bên dưới sẽ được cập nhật dần theo tiến độ.

---

## 🧑‍💻 THÔNG TIN NHÓM

| STT | Họ và Tên | MSSV | Email | Đóng góp |
|-----|-----------|------|-------|----------|
| 1 | Lê Trung Kiên | B23DCCN461 | (email) | Lõi server: TCP, framing, executor, `AuctionService`, scheduler, test đồng thời |
| 2 | Cao Công Hoè | B23DCCN349 | (email) | CSDL, xác thực, REST, WebSocket gateway, trang web |
| 3 | Hà Mạnh Quân | B23DCCN671 | (email) | Desktop client, UDP, RMI Settlement, kịch bản demo |

**Tên nhóm:** (…)
**Chủ đề đã đăng ký:** Hệ thống đấu giá trực tuyến (WebSocket, TCP, UDP, RMI, Web client)

---

## 🧠 MÔ TẢ HỆ THỐNG

Nhiều người dùng tham gia các phiên đấu giá và đặt giá theo thời gian thực, bằng **ứng dụng desktop** (giao thức TCP tự thiết kế) hoặc **trình duyệt** (WebSocket). Mọi đường vào đều đi qua **một nơi duy nhất quyết định chấp nhận giá** (`AuctionService` chạy trên một executor đơn luồng), nên hai người đặt cùng lúc không làm sai trạng thái.

**Cấu trúc logic tổng quát:**

```
 Trình duyệt ──WebSocket──► ┐
 Postman/curl ──REST──────► ├─► [HTTP/WS Gateway] ─┐
                                                   │ gọi cùng một AuctionService
 Desktop client ──TCP (JSON\n)────────────────────┼──► [AUCTION SERVER] ──JDBC──► SQLite
                                                   │     single-thread executor
 Desktop client ◄──UDP broadcast (tìm server)──────┤          │
 Mọi client     ◄──UDP multicast (sắp bắt đầu)─────┘          └──RMI──► [Settlement Service]
```

**Vai trò từng giao thức:**

| Kiến thức | Dùng ở đâu | Lý do |
|---|---|---|
| TCP socket | Đặt giá từ desktop client | Tin cậy, đúng thứ tự, tự thiết kế giao thức và đóng khung |
| UDP broadcast | Client tự tìm server trong LAN | Chưa biết IP nên chưa mở được TCP |
| UDP multicast | Thông báo "phiên sắp bắt đầu" | Một-nhiều, nhẹ, mất gói không ảnh hưởng tính đúng |
| REST | Tra cứu phiên, lịch sử, thống kê; admin tạo phiên | Request/response, dễ test bằng curl/Postman |
| WebSocket | Trang web xem phòng và đặt giá real-time | Trình duyệt không mở được TCP thô; server cần chủ động đẩy dữ liệu |
| RMI | Server gửi kết quả sang Settlement khi phiên kết thúc | Gọi hàm từ xa, nằm ngoài đường găng của bid, lỗi thì retry |

**Sơ đồ hệ thống:**

![System Diagram](./statics/diagram.png)

---

## ⚙️ CÔNG NGHỆ SỬ DỤNG

| Thành phần | Công nghệ | Ghi chú |
|------------|-----------|---------|
| Ngôn ngữ | Java 21 | Server, client, settlement |
| Build | Maven 3.9+ | Tự tải thư viện bên dưới |
| TCP / UDP / RMI | `java.net`, `java.rmi` | Có sẵn trong JDK |
| REST + WebSocket | Javalin 6.4.0 | Chạy trên Jetty, chung cổng 8080 |
| JSON | Gson 2.11.0 | |
| Database | SQLite (sqlite-jdbc 3.47.1.0) | File DB trên máy server |
| Log | SLF4J Simple 2.0.16 | |
| Desktop client | Swing | Có sẵn trong JDK |
| Web client | HTML + JavaScript thuần | Không framework |
| Kiểm thử | JUnit 5.11.4 | |

**Cài đặt môi trường:** chỉ cần cài [JDK 21](https://adoptium.net/) và [Maven](https://maven.apache.org/download.cgi), rồi kiểm tra bằng `java -version` và `mvn -v`. Các thư viện còn lại Maven tự tải khi build.

---

## 🚀 HƯỚNG DẪN CHẠY DỰ ÁN

### 1. Clone repository

```bash
git clone <repository-url>
cd <tên-repo>/source
```

### 2. Cấu hình

```bash
copy config\server.properties.example config\server.properties
copy config\client.properties.example config\client.properties
```

### 3. Build

```bash
mvn install
```

### 4. Chạy (theo thứ tự)

```bash
mvn -q -pl settlement exec:java     # 1. Dịch vụ Settlement (RMI)
mvn -pl server exec:java            # 2. Auction Server (không thêm -q, vì -q ẩn log của server)
mvn -q -pl client exec:java         # 3. Desktop client (mở nhiều cửa sổ để thử)
```

Trình duyệt: mở `http://<IP server>:8080/`.

### 5. Kiểm thử nhanh

```bash
mvn test
```

Chạy 3 test tự động:

| Test | Kiểm tra |
|---|---|
| Hai người cùng đặt giá | Hai client đặt cùng một mức giá đồng thời, chỉ một lượt được chấp nhận |
| Framing TCP | Thông điệp bị chia nhỏ, nhiều thông điệp một lần đọc, JSON lỗi, thông điệp quá 64 KiB |
| Bid sau giờ kết thúc | Bid tại hoặc sau `endsAt` bị từ chối, kể cả khi scheduler chưa chạy |

### 6. Chạy trên nhiều máy (Windows)

Mở firewall cho mạng Private (PowerShell quyền admin):

```powershell
New-NetFirewallRule -DisplayName "LiveBid" -Direction Inbound -Protocol TCP -LocalPort 5000,8080,1099 -RemoteAddress LocalSubnet -Profile Private -Action Allow
New-NetFirewallRule -DisplayName "LiveBid UDP" -Direction Inbound -Protocol UDP -LocalPort 5001,5002 -RemoteAddress LocalSubnet -Profile Private -Action Allow
```

---

## 🔗 GIAO TIẾP (GIAO THỨC SỬ DỤNG)

| Kênh | Giao thức | Cổng | Mô tả |
|------|-----------|------|-------|
| Desktop ↔ Server | TCP | 5000 | JSON một dòng, UTF-8, kết thúc `\n`, tối đa 64 KiB |
| Trình duyệt ↔ Server | WebSocket | 8080 `/ws` | Cùng định dạng JSON với TCP, mỗi frame một thông điệp |
| Tra cứu / quản trị | HTTP REST | 8080 `/api` | JSON, token `Authorization: Bearer ...` |
| Tìm server | UDP broadcast | 5001 | Gói `LIVEBID_DISCOVER` → `{ "name":"LiveBid", "tcpPort":5000, "httpPort":8080 }` |
| Thông báo | UDP multicast | 239.255.42.99:5002 | `{ "auctionId", "title", "startsAt" }` |
| Settlement | Java RMI | 1099 | `SettlementService.recordResult(...)`, `getSummary()` |

Ví dụ thông điệp TCP/WebSocket:

```json
{"protocolVersion":1,"kind":"REQUEST","type":"PLACE_BID","requestId":"2e8d7fd0-...","payload":{"auctionId":1,"amount":280000}}
```

(Danh sách đầy đủ các yêu cầu TCP, sự kiện và mã lỗi sẽ được bổ sung khi triển khai.)

### REST API (`http://<server>:8080/api`)

Thành công: body là DTO JSON trực tiếp. Lỗi: mã HTTP theo `ErrorCode`, body
`{"error":{"code":"BID_TOO_LOW","message":"...","details":{...}}}`. Mọi endpoint trừ `register` và `login` cần
header `Authorization: Bearer <token>` (thiếu hoặc sai → `401 UNAUTHORIZED`).

| Phương thức | Đường dẫn | Quyền | Body / tham số | Trả về |
|---|---|---|---|---|
| POST | `/api/register` | Công khai | `{username, password, displayName}` | `201` `{user, token}` · `400` · `409 USERNAME_TAKEN` |
| POST | `/api/login` | Công khai | `{username, password}` | `200` `{user, token}` · `401 INVALID_CREDENTIALS` |
| POST | `/api/logout` | Đăng nhập | — | `204` (thu hồi token) |
| GET | `/api/me` | Đăng nhập | — | `200` `UserView` |
| GET | `/api/auctions` | Đăng nhập | `?status=ACTIVE&page=1&pageSize=20` (`status` bỏ trống = tất cả) | `200` `{items, page, pageSize, hasMore}` |
| GET | `/api/auctions/{id}` | Đăng nhập | — | `200` `AuctionView` · `404 AUCTION_NOT_FOUND` |
| GET | `/api/auctions/{id}/bids` | Đăng nhập | `?before=<bidId>&limit=20` | `200` danh sách `BidView`, mới nhất trước |
| GET | `/api/me/auctions` | Đăng nhập | `?filter=participated` hoặc `won` | `200` danh sách `AuctionView` |
| GET | `/api/stats` | Đăng nhập | — | `200` thống kê Settlement · `503` khi Settlement chưa chạy |
| POST | `/api/auctions` | ADMIN | `{title, description, startingPrice, minIncrement, startsAt, endsAt}` | `201` `AuctionView` · `403` |
| PUT | `/api/auctions/{id}` | ADMIN | `{expectedVersion, draft:{...}}` | `200` · `409 VERSION_CONFLICT` |

Thử bằng `curl.exe` trong PowerShell:

```powershell
curl.exe -s -X POST http://127.0.0.1:8080/api/register -H "Content-Type: application/json" -d '{\"username\":\"hoe\",\"password\":\"12345678\",\"displayName\":\"Hoè\"}'
curl.exe -s http://127.0.0.1:8080/api/auctions?status=ACTIVE -H "Authorization: Bearer <token>"
```

### WebSocket (`ws://<server>:8080/ws`)

Cùng định dạng thông điệp và cùng bộ lệnh với TCP (dùng chung `MessageRouter`); mỗi frame văn bản là một
thông điệp, tối đa 64 KiB; kết nối im lặng quá 45 giây bị đóng (trình duyệt gửi `PING` mỗi 15 giây).

1. Lấy token bằng REST `/api/login`.
2. Mở `/ws`, gửi `AUTH` với `{"token":"..."}`, nhận `{user}`.
3. `JOIN_AUCTION` `{"auctionId":1}` → snapshot `{auction, recentBids}`; từ đây nhận sự kiện của phiên.
4. `PLACE_BID` `{"auctionId":1,"amount":280000}` → biên nhận; mọi người trong phòng nhận `AUCTION_UPDATED`.
5. Sự kiện server đẩy: `AUCTION_STARTED`, `AUCTION_UPDATED`, `AUCTION_EXTENDED`, `AUCTION_FINISHED` (payload `AuctionView`).

```json
{"protocolVersion":1,"kind":"REQUEST","type":"AUTH","requestId":"7c1e...","payload":{"token":"q3Zk..."}}
{"protocolVersion":1,"kind":"EVENT","type":"AUCTION_UPDATED","payload":{"auctionId":1,"currentPrice":280000,"version":8,"...":"..."}}
```

Mất kết nối thì trang web tự kết nối lại (chờ 1, 2, 4… tối đa 10 giây), gửi lại `AUTH` và `JOIN_AUCTION`.

---

## 📊 KẾT QUẢ THỰC NGHIỆM

(Sẽ bổ sung: ảnh chụp giao diện, kết quả chạy `mvn test`.)

---

## 🧩 CẤU TRÚC DỰ ÁN

```
.
├── README.md
├── INSTRUCTION.md
├── statics/                 # Hình ảnh, sơ đồ, dữ liệu mẫu
└── source/
    ├── .gitignore
    ├── pom.xml              # Maven cha
    ├── common/              # Hằng số giao thức, DTO, interface RMI dùng chung
    ├── server/              # TCP, UDP, REST/WebSocket, nghiệp vụ, SQLite
    ├── client/              # Desktop client (Swing)
    ├── settlement/          # Dịch vụ RMI
    ├── web/                 # Trang web HTML + JS
    ├── tests/               # Kiểm thử tích hợp
    └── config/              # Cấu hình mẫu (*.properties.example)
```

---

## 🧩 HƯỚNG PHÁT TRIỂN THÊM

- [ ] TLS cho TCP và WebSocket (`SSLSocket`, WSS)
- [ ] Chat trong phòng đấu giá
- [ ] Đặt giá tự động
- [ ] Ảnh sản phẩm

---

## 📝 GHI CHÚ

- Repo tuân thủ cấu trúc trong `INSTRUCTION.md`.
- Không commit `data/`, log, file cấu hình cá nhân và thư mục build (xem `source/.gitignore`).

---

## 📚 TÀI LIỆU THAM KHẢO

- [Java ServerSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/ServerSocket.html), [DatagramSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/DatagramSocket.html), [MulticastSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/MulticastSocket.html)
- [Java RMI](https://docs.oracle.com/en/java/javase/21/docs/api/java.rmi/java/rmi/package-summary.html)
- [Javalin](https://javalin.io/documentation)
- [SQLite](https://www.sqlite.org/docs.html)
