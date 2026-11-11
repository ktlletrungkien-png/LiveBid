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

Lệnh đã chạy: `PING`, `REGISTER`, `LOGIN`, `LOGOUT`, `LIST_AUCTIONS`, `GET_AUCTION`, `JOIN_AUCTION`,
`LEAVE_AUCTION`, `PLACE_BID`, `GET_BID_HISTORY`, `GET_MY_AUCTIONS`, `CREATE_AUCTION`, `UPDATE_AUCTION`
(`AUTH` chỉ dùng trên WebSocket nên TCP trả `INTERNAL_ERROR` "chưa hỗ trợ").

Quy tắc phiên đăng nhập trên TCP: mọi lệnh trừ `PING`/`REGISTER`/`LOGIN` cần đăng nhập (chưa thì `UNAUTHORIZED`);
`CREATE_AUCTION`/`UPDATE_AUCTION` cần ADMIN (`FORBIDDEN`); người đặt giá luôn lấy từ phiên đăng nhập, không
từ payload. Một kết nối là một tài khoản: đang đăng nhập mà `LOGIN`/`REGISTER` lại thì bị từ chối
(`VALIDATION_ERROR`), phải `LOGOUT` trước. `LOGOUT` rời mọi phòng đang tham gia.

Server khởi động theo thứ tự: mở SQLite (`database.path`, mặc định `./data/auction.db`) → dựng service →
`processDueAuctions()` bắt kịp phiên đã đến giờ → scheduler → mở cổng TCP → UDP. Lỗi bind UDP chỉ cảnh báo,
không làm server dừng.

Thử nhanh bằng PowerShell khi server đang chạy:

```powershell
$c = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 5000); $s = $c.GetStream()
$w = New-Object System.IO.StreamWriter($s); $w.NewLine = "`n"; $r = New-Object System.IO.StreamReader($s)
$w.WriteLine('{"protocolVersion":1,"kind":"REQUEST","type":"PING","requestId":"1"}'); $w.Flush(); $r.ReadLine()
$c.Close()
```

## ⚖️ NGHIỆP VỤ ĐẤU GIÁ (`core/`)

**Xử lý đồng thời.** Mọi thao tác đọc/ghi đấu giá đi qua `BusinessExecutor`, một executor **một luồng** có
hàng đợi giới hạn (đầy thì trả `SERVER_BUSY`). Hai người đặt cùng lúc là hai tác vụ xếp hàng; tác vụ sau
luôn thấy giá mới, nên chỉ một người được nhận. Không cần khóa cho dữ liệu đấu giá.

**Luồng đặt giá** (`AuctionServiceImpl.placeBid`):

```
đã đăng nhập, không phải ADMIN → requestId đã nhận? trả biên nhận cũ
→ 1đ ≤ giá ≤ 1.000.000.000.000đ → lấy giờ server, tự mở/chốt phiên nếu đã tới hạn
→ đang ở phòng? startsAt ≤ now < endsAt? giá ≥ mức tối thiểu?
→ transaction: thêm bid + cập nhật giá/người dẫn đầu/version (+ gia hạn nếu trong 10 giây cuối)
→ commit → lưu biên nhận → phát AUCTION_UPDATED (hoặc AUCTION_EXTENDED) cho cả phòng → trả biên nhận
```

| Quy tắc | Cách làm |
|---|---|
| Mức tối thiểu | Bid đầu: `≥ giá khởi điểm`; sau đó: `≥ giá hiện tại + bước giá` (không bắt buộc bội số) |
| Thời gian | Server là nguồn giờ (`Clock`). Mỗi thao tác tự kiểm tra mốc, **không phụ thuộc scheduler** |
| Anti-sniping | Bid khi còn ≤ 10 s thì `endsAt += 30 s`, tối đa 10 lần; chỉ phát **một** sự kiện `AUCTION_EXTENDED` |
| Chống gửi trùng | Cache 10.000 biên nhận gần nhất theo (người dùng, `requestId`); mất khi restart (hạn chế đã biết) |
| Chốt phiên | Một lần duy nhất; cùng transaction ghi `settlement_queue` để Settlement gửi bù được |
| Sự kiện | Chỉ phát **sau commit**; mỗi sự kiện mang `AuctionView` đầy đủ, `version` tăng dần |

`AuctionScheduler` cứ 500 ms đưa `processDueAuctions()` vào executor: mở phiên tới giờ, chốt phiên hết giờ,
báo "sắp bắt đầu" (5 phút trước) qua `AuctionLifecycleListener`. Khi khởi động, gọi `processDueAuctions()`
một lần trước khi mở cổng để chốt các phiên quá hạn lúc server tắt.

**Cho Hoè:** `AuthService` cũng nên đưa thao tác DB vào cùng `BusinessExecutor`; riêng băm PBKDF2 chạy ở
luồng riêng rồi mới đưa kết quả vào executor. REST/WebSocket chỉ gọi `AuctionService`.

**Cho Quân:** cài `AuctionLifecycleListener` (gọi trên luồng executor, phải trả về ngay): `UdpAnnouncer` dùng
`auctionStartingSoon`, `SettlementClient` dùng `auctionFinished` rồi đọc `settlement_queue` để retry.

## 👤 TẠO TÀI KHOẢN ADMIN

Tài khoản ADMIN không đăng ký được qua web; tạo bằng công cụ dòng lệnh (chạy từ thư mục `source/`):

```powershell
mvn -q install -DskipTests
mvn -pl server exec:java@create-admin
# hoặc chỉ định file CSDL:
mvn -pl server exec:java@create-admin -Dexec.args="./data/auction.db"
```

Công cụ hỏi tên đăng nhập, tên hiển thị và mật khẩu (nhập hai lần). Trùng tên đăng nhập thì báo lỗi và thoát mã 1.

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
│   ├── ServiceException.java # lỗi nghiệp vụ mang ErrorCode
│   ├── AuctionLifecycleListener.java # móc "sắp bắt đầu" / "đã chốt" cho UDP (Quân), Settlement (Quân)
│   ├── BusinessExecutor.java # executor MỘT luồng: mọi thao tác nghiệp vụ + DB xếp hàng ở đây
│   ├── AuctionServiceImpl.java # cài đặt AuctionService
│   ├── RoomManager.java      # kênh nào đang ở phòng nào (TCP + WebSocket)
│   ├── AuctionScheduler.java # nhịp 500 ms: đưa việc mở/chốt phiên vào executor
│   └── AuctionSettings.java  # anti-sniping, thời gian báo trước, cỡ snapshot
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
