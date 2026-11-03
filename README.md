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
mvn -q -pl server exec:java         # 2. Auction Server
mvn -q -pl client exec:java         # 3. Desktop client (mở nhiều cửa sổ để thử)
```

Trình duyệt: mở `http://<IP server>:8080/`.

### 5. Kiểm thử nhanh

```bash
mvn test
```

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

(Danh sách đầy đủ các yêu cầu, sự kiện, mã lỗi và endpoint REST sẽ được bổ sung khi triển khai.)

---

## 📊 KẾT QUẢ THỰC NGHIỆM

(Sẽ bổ sung: ảnh chụp giao diện, kết quả các ca kiểm thử, số liệu đo tải.)

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
