# HƯỚNG DẪN GIAO VIỆC — LiveBid — Cao Công Hoè

> **File này viết cho AI agent** (Claude, ChatGPT, Copilot, Cursor, Gemini… đều được) đang làm việc thay
> Cao Công Hoè trên repo LiveBid, và cho chính Cao Công Hoè đọc.
>
> - Người giao việc, chủ repo, trưởng nhóm: **Lê Trung Kiên** (gọi tắt **Kiên**). Kiên phụ trách lõi server.
> - Người nhận file này: **Cao Công Hoè** (B23DCCN349). Phụ trách: **CSDL SQLite, xác thực, REST API, WebSocket gateway, trang web**.
> - Thành viên còn lại: **Hà Mạnh Quân** (desktop client, UDP, RMI Settlement).
> - Ngày giao: 10/11/2026. Báo cáo tiến độ với giảng viên: **11/11/2026** (xem mục 0).
>
> File này **không được commit** (đã có `/HUONG_DAN_*.md` trong `.gitignore`). Đặt file ở thư mục gốc repo
> để agent đọc được.

---

## MỤC LỤC

- [0. Đọc trước tiên: quy tắc bắt buộc cho agent](#0-đọc-trước-tiên-quy-tắc-bắt-buộc-cho-agent)
- [1. Tổng quan dự án](#1-tổng-quan-dự-án)
- [2. Kiến trúc và giao thức](#2-kiến-trúc-và-giao-thức)
- [3. Quy tắc nghiệp vụ](#3-quy-tắc-nghiệp-vụ)
- [4. Mã nguồn hiện có — đọc kỹ trước khi viết code](#4-mã-nguồn-hiện-có--đọc-kỹ-trước-khi-viết-code)
- [5. Môi trường, build, chạy](#5-môi-trường-build-chạy)
- [6. Quyền sở hữu file — được sửa gì, không được sửa gì](#6-quyền-sở-hữu-file--được-sửa-gì-không-được-sửa-gì)
- [7. Git, commit, Pull Request](#7-git-commit-pull-request)
- [8. Khi bị chặn hoặc cần thành viên khác](#8-khi-bị-chặn-hoặc-cần-thành-viên-khác)
- [9. Phần việc của Cao Công Hoè](#9-phần-việc-của-cao-công-hoè)
- [10. Checklist trước khi mở Pull Request](#10-checklist-trước-khi-mở-pull-request)

---

## 0. Đọc trước tiên: quy tắc bắt buộc cho agent

### 0.1. Trình tự bắt đầu phiên làm việc

Agent **phải** làm đúng thứ tự sau, không được bỏ bước:

1. Đọc **toàn bộ** file này một lượt trước khi làm bất cứ việc gì.
2. **Hỏi người dùng (Cao Công Hoè) về cách chia commit** (mục 7.2). Câu hỏi mẫu:
   > "Phần việc của bạn gồm 6 mốc (M1–M6). Bạn muốn chia commit thế nào?
   > (a) mỗi mốc 1 commit; (b) gộp một số mốc liên tiếp — mốc nào?; (c) nhiều commit nhỏ trong mỗi mốc;
   > (d) cách khác. Và hôm nay bạn muốn làm đến mốc nào?"
   Chờ trả lời rồi mới viết code. Ghi nhớ câu trả lời trong suốt phiên.
3. Kiểm tra danh tính Git (mục 7.1). Nếu `user.name`/`user.email` không phải của Cao Công Hoè, **dừng lại** và
   hướng dẫn người dùng sửa.
4. Cập nhật `main`, tạo nhánh mới (mục 7.3).
5. Build thử một lần trên `main` (`mvn install` trong thư mục `source/`, mục 5). Nếu `main` đang đỏ (build
   hoặc test lỗi) thì **không sửa code của người khác**: báo người dùng theo mục 8.
6. Làm từng mốc theo mục 9. Hết mỗi mốc: build + test xanh, rồi commit theo cách người dùng đã chọn.

### 0.2. Ràng buộc cho buổi báo cáo 11/11/2026 — KHÔNG ĐƯỢC LÀM HỎNG DEMO

Ngày 11/11 Kiên báo cáo tiến độ. Demo gồm đúng 3 việc:

1. `mvn test` trong `source/` → tất cả test xanh.
2. Chạy server: `mvn -pl server exec:java` → log có dòng `TCP server lắng nghe tại 0.0.0.0:5000`.
3. Gửi `PING` qua TCP (bằng PowerShell, xem `source/server/README.md`) → server trả `serverTime`.

Vì vậy, **tối 10/11 chỉ cần làm đến mốc được ghi "Mục tiêu tối 10/11" ở mục 9.** Làm thêm cũng được,
nhưng mọi thứ đẩy lên GitHub trước buổi báo cáo phải bảo đảm:

- `mvn install` (kèm test) trong `source/` **xanh hoàn toàn** trên máy Cao Công Hoè.
- **Không sửa** file thuộc quyền Kiên hoặc Hà Mạnh Quân (mục 6). Đặc biệt không đụng `ServerMain.java`,
  `ServerConfig.java`, `server/core/**`, `server/tcp/**`, `common/**`.
- Test mới **không** được: dùng cổng cố định (5000, 5001, 5002, 8080, 1099…) — luôn dùng cổng 0/cổng ngẫu
  nhiên; cần mạng Internet; cần một chương trình khác đang chạy; chạy quá ~10 giây; phụ thuộc thứ tự chạy.
- Không thêm Maven module mới, không đổi phiên bản Java/thư viện trong `source/pom.xml`.
- Chỉ mở Pull Request, **không tự merge**. Kiên duyệt và merge.

Nếu không chắc một thay đổi có ảnh hưởng demo không → để Pull Request ở trạng thái **Draft** và ghi rõ trong
mô tả "chưa merge trước 11/11".

### 0.3. Nguyên tắc làm việc chung

- **Không đoán.** Tên lớp, chữ ký hàm, định dạng JSON, tên cột DB, cổng… đều đã được quy định trong file
  này hoặc trong code hiện có. Nếu không tìm thấy hoặc thấy mâu thuẫn → mục 8, không tự bịa.
- **Đọc code hiện có trước khi viết** (mục 4). Đặc biệt đọc javadoc của interface mình phải cài đặt.
- **Viết theo phong cách code hiện có:** Java 21, `record` cho dữ liệu bất biến, `final class`,
  javadoc/chú thích tiếng Việt có dấu, log bằng SLF4J (`LoggerFactory.getLogger(...)`), không dùng
  `System.out` trong server (trừ công cụ dòng lệnh), không bắt rồi nuốt exception im lặng.
- **Người dùng (Cao Công Hoè) phải hiểu code** vì giảng viên sẽ vấn đáp từng người về phần code mình phụ trách
  (25% điểm, chấm cá nhân). Khi hoàn thành mỗi mốc, agent tóm tắt cho người dùng: đã làm gì, vì sao thiết kế
  như vậy, các câu giảng viên có thể hỏi (mục 9 có gợi ý).
- Không thêm thư viện ngoài nếu file này không cho phép. Thư viện đã có: Gson 2.11.0, Javalin 6.4.0,
  sqlite-jdbc 3.47.1.0, SLF4J Simple 2.0.16, JUnit 5.11.4.
- Chuỗi hiển thị cho người dùng và thông báo lỗi viết **tiếng Việt có dấu**. Tên lớp/hàm/biến tiếng Anh.

---

## 1. Tổng quan dự án

**LiveBid** — bài tập lớn môn Lập trình mạng (PTIT, giảng viên TS. Đặng Ngọc Hùng). Hệ thống **đấu giá trực
tuyến thời gian thực, đa giao thức**.

Nhiều người dùng tham gia các phiên đấu giá và đặt giá theo thời gian thực, bằng **ứng dụng desktop**
(Java Swing, giao thức TCP tự thiết kế) hoặc **trình duyệt** (WebSocket). Mọi đường vào đều đi qua **một
nơi duy nhất quyết định chấp nhận giá**: `AuctionService` chạy trên một executor **một luồng**. Nhờ vậy hai
người đặt cùng lúc không làm sai trạng thái.

Ví dụ: bàn phím cơ, giá khởi điểm 200.000đ, bước giá 20.000đ, kéo dài 5 phút. A đặt 200.000đ, B đặt
240.000đ, A đặt 260.000đ. Hết giờ không ai vượt → A thắng với 260.000đ.

### 1.1. Thành viên

| Thành viên | MSSV | Phụ trách |
|---|---|---|
| Lê Trung Kiên | B23DCCN461 | Lõi server: TCP, framing, executor, `AuctionService`, scheduler, test đồng thời, hợp đồng chung |
| Cao Công Hoè | B23DCCN349 | CSDL SQLite, xác thực, REST, WebSocket gateway, trang web |
| Hà Mạnh Quân | B23DCCN671 | Desktop client, UDP, RMI Settlement, kịch bản demo |

### 1.2. Yêu cầu của môn học (file `INSTRUCTION.md` của giảng viên)

- Cấu trúc repo bắt buộc: `README.md`, `INSTRUCTION.md`, `statics/`, `source/` (có `.gitignore`,
  `client/`, `server/`, mỗi module có `README.md`). **Không ai được sửa hoặc xóa `INSTRUCTION.md`.**
- Toàn bộ mô tả hệ thống, giao thức, hướng dẫn build/run nằm trong `README.md` gốc.
- Chỉ chấm repo có README đầy đủ và **chạy được**.
- **Lịch sử commit phải thể hiện đóng góp của tất cả thành viên** — mỗi người tự commit bằng tài khoản Git
  của mình.
- Chấm điểm: Ý tưởng & chức năng 40% (nhóm) · Tổ chức mã nguồn & repo **20% (cá nhân)** · Tài liệu 15%
  (nhóm) · Trình bày & vấn đáp **25% (cá nhân)**. Giảng viên đọc README → chạy demo → hỏi **từng thành
  viên** về phần code của họ.

### 1.3. Phạm vi

- Hai vai trò: `ADMIN` (tạo/sửa phiên, không đặt giá) và `USER` (đặt giá).
- Một server trung tâm, nhiều client; dữ liệu lưu ở server (SQLite).
- Tiền VND, số nguyên `long`. Không thanh toán thật, không ảnh sản phẩm.
- Demo trên một máy hoặc nhiều máy Windows cùng mạng LAN. Chạy local (không Docker, vì broadcast/multicast
  không qua được mạng Docker).

---

## 2. Kiến trúc và giao thức

```
 Trình duyệt ──WebSocket──► ┐
 Postman/curl ──REST──────► ├─► [HTTP/WS Gateway: server/http] ─┐
                                                                 │ gọi cùng một AuctionService
 Desktop client ──TCP (JSON\n)──► [server/tcp] ─────────────────┼──► [server/core: AuctionServiceImpl]
                                                                 │      BusinessExecutor (1 luồng)
 Desktop client ◄──UDP broadcast (tìm server) [server/udp]       │            │ JDBC
 Mọi client     ◄──UDP multicast (sắp bắt đầu) [server/udp]      │            ▼
                                                                 │      SQLite [server/db]
                       [server/settlement: SettlementClient] ──RMI──► [module settlement]
```

Gateway HTTP/WS chạy **cùng tiến trình** với Auction Server. Settlement là **tiến trình riêng**.

### 2.1. Vai trò từng giao thức (giảng viên rất hay hỏi "vì sao")

| Giao thức | Dùng ở đâu | Lý do |
|---|---|---|
| TCP socket | Desktop client ↔ server | Tin cậy, đúng thứ tự; tự thiết kế giao thức và đóng khung |
| UDP broadcast | Client tự tìm server trong LAN | Chưa biết IP nên chưa mở được TCP |
| UDP multicast | Thông báo "phiên sắp bắt đầu" | Một-nhiều, nhẹ; mất gói không ảnh hưởng tính đúng |
| REST | Tra cứu phiên, lịch sử, thống kê; admin tạo/sửa phiên | Request/response, dễ test bằng curl/Postman |
| WebSocket | Trang web xem phòng và đặt giá real-time | Trình duyệt không mở được TCP thô; server cần chủ động đẩy |
| Java RMI | Server gửi kết quả phiên sang Settlement | Gọi hàm từ xa; nằm **ngoài** đường găng của bid, lỗi thì thử lại |

**Nguyên tắc:** UDP và RMI **không bao giờ** ảnh hưởng đúng/sai của một lượt đặt giá. Dữ liệu chuẩn luôn lấy
qua TCP/WebSocket/REST.

### 2.2. Cổng và cấu hình

| Kênh | Giao thức | Cổng | Khóa cấu hình |
|---|---|---|---|
| Desktop ↔ Server | TCP | 5000 | `server.tcpPort` |
| REST + WebSocket | HTTP | 8080 (`/api`, `/ws`) | `server.httpPort` |
| Tìm server | UDP broadcast | 5001 | `server.udpDiscoveryPort` |
| Thông báo | UDP multicast | 239.255.42.99:5002 | `server.multicastGroup`, `server.multicastPort` |
| Settlement | Java RMI | 1099 | `settlement.rmiHost`, `settlement.rmiPort` |
| CSDL | SQLite | — | `database.path` (mặc định `./data/auction.db`) |

Cấu hình server ở `source/config/server.properties` (copy từ `server.properties.example`; file thật **không**
commit). Lớp `ServerConfig` (của Kiên) đọc file này; mỗi mảng đọc khóa của mình bằng
`config.getString(key, default)` / `config.getInt(key, default)`. Thư mục chạy là `source/`, nên mọi đường
dẫn tương đối tính từ `source/`.

### 2.3. Định dạng thông điệp TCP/WebSocket

TCP: mỗi thông điệp là **một object JSON viết trên một dòng, UTF-8, kết thúc `\n`**, tối đa 64 KiB.
WebSocket: cùng định dạng JSON, mỗi frame văn bản là một thông điệp (không cần `\n`).

```json
{"protocolVersion":1,"kind":"REQUEST","type":"PLACE_BID","requestId":"2e8d7fd0-...","payload":{"auctionId":1,"amount":280000}}
{"protocolVersion":1,"kind":"RESPONSE","type":"PLACE_BID","requestId":"2e8d7fd0-...","success":true,"payload":{"bidId":15,"auctionId":1,"acceptedAmount":280000,"acceptedAt":1793080800000,"version":8}}
{"protocolVersion":1,"kind":"RESPONSE","type":"PLACE_BID","requestId":"2e8d7fd0-...","success":false,"error":{"code":"BID_TOO_LOW","message":"Giá tối thiểu hiện tại là 300000đ","details":{"minimumAllowed":300000}}}
{"protocolVersion":1,"kind":"EVENT","type":"AUCTION_UPDATED","payload":{"auctionId":1,"currentPrice":280000,"version":8,"serverTime":1793080800000,"endsAt":1793081100000,"...":"..."}}
```

- `kind`: `REQUEST` (client → server, luôn có `requestId` UUID), `RESPONSE` (trả lời đúng một REQUEST, cùng
  `requestId`), `EVENT` (server chủ động đẩy, không có `requestId`).
- RESPONSE thất bại cho một yêu cầu không đọc được (JSON hỏng) có thể thiếu `type`/`requestId` nhưng luôn có
  `error`.
- Trường `null` không xuất hiện trong JSON.

**Bảng lệnh** (chi tiết kiểu Java ở `source/common/README.md`):

| Lệnh | Payload gửi lên | Payload trả về | Quyền |
|---|---|---|---|
| `PING` | — | `{"serverTime": ...}` | Ai cũng được |
| `AUTH` (chỉ WebSocket) | `AuthPayload{token}` | `UserView` | Chưa đăng nhập |
| `REGISTER` | `RegisterPayload{username,password,displayName}` | `LoginResult{user, token}` | Chưa đăng nhập |
| `LOGIN` | `LoginPayload{username,password}` | `LoginResult{user, token}` | Chưa đăng nhập |
| `LOGOUT` | — | — | Đã đăng nhập |
| `LIST_AUCTIONS` | `ListAuctionsPayload{status,page,pageSize}` | `PageResult<AuctionView>` | Đã đăng nhập |
| `GET_AUCTION` | `AuctionIdPayload{auctionId}` | `AuctionView` | Đã đăng nhập |
| `JOIN_AUCTION` | `AuctionIdPayload` | `RoomSnapshot{auction, recentBids}` | Đã đăng nhập |
| `LEAVE_AUCTION` | `AuctionIdPayload` | — | Đã đăng nhập |
| `PLACE_BID` | `PlaceBidPayload{auctionId,amount}` | `BidReceipt` | `USER`, đang trong phòng |
| `GET_BID_HISTORY` | `BidHistoryPayload{auctionId,beforeBidId,limit}` | `List<BidView>` | Đã đăng nhập |
| `GET_MY_AUCTIONS` | `MyAuctionsPayload{filter}` | `List<AuctionView>` | Đã đăng nhập |
| `CREATE_AUCTION` | `AuctionDraft` | `AuctionView` | `ADMIN` |
| `UPDATE_AUCTION` | `UpdateAuctionPayload{auctionId,expectedVersion,draft}` | `AuctionView` | `ADMIN` |

Sự kiện `AUCTION_STARTED`, `AUCTION_UPDATED`, `AUCTION_EXTENDED`, `AUCTION_FINISHED` đều có payload
`AuctionView`.

**Mã lỗi** (`ErrorCode`, kèm mã HTTP cho REST qua `code.httpStatus()`): `INVALID_MESSAGE`(400),
`UNSUPPORTED_VERSION`(400), `UNAUTHORIZED`(401), `FORBIDDEN`(403), `VALIDATION_ERROR`(400),
`USERNAME_TAKEN`(409), `INVALID_CREDENTIALS`(401), `AUCTION_NOT_FOUND`(404), `AUCTION_NOT_STARTED`(409),
`AUCTION_FINISHED`(409), `NOT_IN_ROOM`(409), `BID_TOO_LOW`(409, `details.minimumAllowed`),
`VERSION_CONFLICT`(409), `SERVER_BUSY`(503), `INTERNAL_ERROR`(500).

### 2.4. UDP và RMI (tóm tắt)

- Tìm server: client gửi chuỗi `LIVEBID_DISCOVER` (UTF-8) tới broadcast cổng 5001; server trả JSON
  `DiscoveryReply` `{"name":"LiveBid","tcpPort":5000,"httpPort":8080}`. Client lấy IP từ **địa chỉ nguồn**
  của gói trả lời.
- Thông báo: server gửi JSON `AuctionAnnouncement` `{"auctionId":..,"title":..,"startsAt":..}` tới nhóm
  multicast 239.255.42.99:5002 khi phiên còn 5 phút nữa mở.
- RMI: interface `com.livebid.common.rmi.SettlementService` (`recordResult(AuctionResultDto)`,
  `getSummary()`), đăng ký trong RMI Registry cổng 1099 với tên `Protocol.RMI_SETTLEMENT_NAME`
  (`"SettlementService"`).

---

## 3. Quy tắc nghiệp vụ

(Đã cài trong `AuctionServiceImpl` của Kiên. Ghi ở đây để mọi phần khác hiển thị/kiểm tra nhất quán.)

- **Giá hợp lệ:** bid đầu tiên `≥ giá khởi điểm`; các bid sau `≥ giá hiện tại + bước giá` (bước giá là
  mức tăng tối thiểu, không bắt buộc bội số). Giá trong khoảng 1 … 1.000.000.000.000đ. Kiểu `long`, **không
  dùng `double`**. `AuctionView.minimumNextBid()` trả về mức tối thiểu cho bid tiếp theo.
- **Thời gian:** server là nguồn thời gian; bid hợp lệ khi `startsAt ≤ giờ server < endsAt`. Thời gian
  lưu epoch milliseconds UTC. Client chỉ đếm ngược ước tính theo `endsAt` và `serverTime` trong
  `AuctionView`.
- **Trạng thái:** `SCHEDULED → ACTIVE → FINISHED`. Mỗi phiên chỉ chốt một lần.
- **Chống bắn tỉa:** bid hợp lệ khi còn ≤ 10 giây → `endsAt += 30 giây`, tối đa 10 lần; phát
  `AUCTION_EXTENDED`.
- **`version`:** tăng sau mỗi thay đổi của phiên. Client **bỏ qua** cập nhật có `version` ≤ bản đang hiển
  thị.
- **Danh tính:** người đặt giá luôn lấy từ phiên đăng nhập của kết nối, **không bao giờ** tin `userId`/`role`
  do client gửi. `ADMIN` không được đặt giá.
- **Chống gửi trùng:** cùng người dùng + cùng `requestId` → trả lại biên nhận cũ, không tạo bid mới.
- **Chỉ báo thành công và phát sự kiện sau khi commit DB.**
- Username: 3–30 ký tự `[a-zA-Z0-9_]`, lưu chữ thường, không trùng. Mật khẩu ≥ 8 ký tự. Tên hiển thị hỗ
  trợ tiếng Việt. Đăng ký công khai luôn tạo `USER`.

---

## 4. Mã nguồn hiện có — đọc kỹ trước khi viết code

Repo (thư mục `source/`) là Maven multi-module, Java 21, `groupId=com.livebid`:

```
source/
├── pom.xml                  # Maven cha: modules common, server, client, settlement, tests (Kiên)
├── config/                  # server.properties.example, client.properties.example
├── common/                  # Dùng chung — Kiên
│   └── src/main/java/com/livebid/common/
│       ├── Protocol.java            # PROTOCOL_VERSION=1, cổng mặc định, MAX_MESSAGE_BYTES=65536,
│       │                            # DISCOVERY_REQUEST="LIVEBID_DISCOVER", RMI_SETTLEMENT_NAME
│       ├── protocol/                # Message, MessageKind, MessageType, ErrorCode, ErrorInfo,
│       │                            # JsonCodec, ProtocolException
│       ├── payload/                 # dữ liệu REQUEST: LoginPayload, PlaceBidPayload, ...
│       ├── dto/                     # dữ liệu trả về: AuctionView, BidView, BidReceipt, RoomSnapshot,
│       │                            # LoginResult, UserView, PageResult, AuctionDraft, Role, AuctionStatus,
│       │                            # MyAuctionFilter
│       ├── udp/                     # DiscoveryReply, AuctionAnnouncement
│       └── rmi/                     # SettlementService, AuctionResultDto, SettlementSummaryDto
├── server/src/main/java/com/livebid/server/
│   ├── ServerMain.java, ServerConfig.java   # Kiên
│   ├── core/                # Kiên: AuctionService (+Impl), AuthService (interface), BusinessExecutor,
│   │                        # ClientChannel, UserPrincipal, ServiceException, AuctionLifecycleListener,
│   │                        # AuctionScheduler, AuctionSettings, RoomManager
│   ├── model/               # Kiên: User, NewUser, Auction, NewAuction, Bid, SettlementTask
│   ├── repo/                # Kiên: UserRepository, AuctionRepository, BidRepository,
│   │   │                    # SettlementQueueRepository, TransactionManager, DataAccessException
│   │   └── memory/          # bản lưu trong bộ nhớ (InMemory*) để test
│   ├── tcp/                 # Kiên: TcpServer, ClientSession, LineFrameDecoder, MessageRouter
│   ├── db/  auth/  http/    # Hoè (hiện chỉ có package-info.java)
│   └── udp/  settlement/    # Quân (hiện chỉ có package-info.java)
├── client/                  # Quân — desktop client Swing (hiện chỉ có ClientMain rỗng)
├── settlement/              # Quân — dịch vụ RMI (hiện chỉ có SettlementMain rỗng)
├── web/                     # Hoè — index.html, app.js, style.css (khung trống)
└── tests/                   # JUnit 5; phụ thuộc common + server
    └── src/test/java/com/livebid/tests/   # TcpFramingTest, ConcurrentBidTest, BidTimingTest (Kiên)
```

### 4.1. Những lớp quan trọng — agent phải mở file đọc javadoc

| Lớp | Điều cần biết |
|---|---|
| `common.protocol.Message` | Record phong bì. Tạo bằng `Message.request(type, payload)`, `Message.success(req, payload)`, `Message.failure(req, code, text, detailsMap)`, `Message.failure(type, requestId, code, text, details)`, `Message.event(type, payload)`. Đọc payload: `msg.payloadAs(X.class)` (ném `ProtocolException` nếu sai kiểu). `msg.typeEnum()` trả `null` nếu lệnh lạ |
| `common.protocol.JsonCodec` | **Gson dùng chung** — không tự `new Gson()`. `encode(msg)` → JSON một dòng (chưa có `\n`); `decode(line)` → `Message` + kiểm tra phong bì; `gson()`, `toTree()`, `fromTree()` |
| `server.core.BusinessExecutor` | **Lớp** (không phải interface), executor **một luồng** tên `business-executor`. `submit(Callable<T>)` → `CompletableFuture<T>`; `run(Runnable)`; `isExecutorThread()`; `checkExecutorThread()`; `close()`. Hàng đợi đầy → future lỗi `SERVER_BUSY` |
| `server.core.AuctionService` | Interface mọi thao tác đấu giá; mọi hàm trả `CompletableFuture`; lỗi là `ServiceException` (bị bọc trong `CompletionException` khi `join()`) |
| `server.core.AuctionServiceImpl` | Cài đặt. Constructor: `(BusinessExecutor, AuctionRepository, BidRepository, UserRepository, SettlementQueueRepository, TransactionManager, Clock, AuctionSettings)`; `setLifecycleListener(AuctionLifecycleListener)`; `processDueAuctions()` |
| `server.core.AuthService` | Interface **Hoè cài đặt**: `register`, `login` (trả `CompletableFuture<UserPrincipal>`), `issueToken`, `resolveToken`, `revokeToken` |
| `server.core.AuctionLifecycleListener` | Móc **Quân cài đặt**: `auctionStartingSoon(AuctionView)`, `auctionFinished(AuctionView)`. Gọi trên luồng executor **sau commit**; cài đặt phải trả về ngay |
| `server.core.ClientChannel` | Một kết nối (TCP hoặc WebSocket): `id()`, `transport()`, `remoteAddress()`, `send(Message)` (không chặn), `close(reason)`, `isOpen()` |
| `server.core.ServiceException` | Lỗi nghiệp vụ mang `ErrorCode` + `details` |
| `server.repo.*` | Interface truy cập dữ liệu (đọc javadoc từng hàm: thứ tự sắp xếp, hành vi khi trùng…). `TransactionManager.inTransaction(Work<T>)` |
| `server.repo.memory.*` | Bản trong bộ nhớ: **hành vi chuẩn** mà bản SQLite phải giống hệt |
| `server.tcp.MessageRouter` | Nhận dòng JSON, kiểm tra phong bì, gọi handler theo `MessageType`, đổi lỗi thành RESPONSE. `register(MessageType, Handler)` |
| `server.tcp.LineFrameDecoder` | Tách luồng byte thành dòng JSON, giới hạn 64 KiB, ném `FrameTooLargeException` |
| `server.ServerConfig` | `ServerConfig.load(args)`, `getString(key, def)`, `getInt(key, def)`, `ServerConfig.defaults()` cho test |

### 4.2. Quy tắc luồng (threading) — quan trọng nhất

1. **Mọi lệnh gọi repository (SQLite dùng chung MỘT `Connection`) phải chạy bên trong
   `BusinessExecutor.submit(...)`.** Ngoại lệ duy nhất: test đơn luồng và công cụ dòng lệnh chạy độc lập.
2. Bên trong tác vụ của executor: **không** gọi mạng (RMI, HTTP, UDP chờ trả lời), **không** băm mật khẩu,
   **không** `sleep`, **không** `join()/get()` một future khác (tự khóa chết).
3. Ở luồng đọc socket, luồng HTTP, luồng giao diện Swing: **không** `join()/get()` future của
   `AuctionService`. Dùng `thenAccept`/`whenComplete`/`handle`.
4. `ClientChannel.send()` phải trả về ngay (bỏ vào hàng đợi gửi, một luồng ghi riêng).

### 4.3. Việc Kiên còn phải làm (các bên khác có thể phải chờ)

| Mã | Việc của Kiên | Ai cần | Trạng thái lúc giao |
|---|---|---|---|
| K1 | Đổi `MessageRouter` để làm việc với `ClientChannel` (không chỉ `ClientSession`), chuyển trạng thái đăng nhập (`principal()`/`setPrincipal()`) vào `ClientChannel`, thêm handler `AUTH` cho WebSocket | Hoè (WebSocket) | Chưa làm |
| K2 | Chuyển `LineFrameDecoder` + `FrameTooLargeException` sang module `common` để client dùng chung | Quân (client TCP) | Chưa làm |
| K3 | `ServerMain` gắn đủ: `AuctionServiceImpl` + scheduler + handler các lệnh (`LOGIN`, `REGISTER`, `JOIN_AUCTION`, `PLACE_BID`…); thay `InMemory*` bằng SQLite khi Hoè xong; gắn `AuthServiceImpl`, `HttpGateway`, UDP, `SettlementClient` khi từng phần được merge; gộp nhiều `AuctionLifecycleListener` | Cả hai | Chưa làm (hiện chỉ có PING) |

Trước khi làm một mốc có ghi "Chờ K1/K2/K3", agent kiểm tra `main` mới nhất xem Kiên đã làm chưa (đọc code,
không đoán). Nếu chưa → mục 8.

---

## 5. Môi trường, build, chạy

- Hệ điều hành của nhóm: **Windows**, terminal PowerShell. Câu lệnh trong file này viết cho PowerShell.
- Cần **JDK 21** (Temurin) và **Maven 3.9+**. Kiểm tra: `java -version` (21.x), `mvn -v` (Java version: 21).
- Mọi lệnh Maven chạy trong thư mục `source/`:

```powershell
cd <thư mục repo>\source
mvn install                  # build + chạy toàn bộ test; phải ra BUILD SUCCESS
mvn test                     # chỉ chạy test
mvn -q install -DskipTests   # build nhanh, bỏ test
mvn -pl server exec:java     # chạy server (Ctrl+C để dừng)
mvn -pl settlement exec:java # chạy Settlement
mvn -pl client exec:java     # chạy desktop client
mvn -pl tests test -Dtest=TênLớpTest   # chạy một lớp test
```

- Lần đầu: `copy config\server.properties.example config\server.properties` (và tương tự cho client).
- Test đặt trong module `tests` (`source/tests/src/test/java/com/livebid/tests/<package-của-bạn>/`). Module
  `tests` đã phụ thuộc `common` và `server`.
- Nếu build lỗi do code **của người khác** → không sửa, báo theo mục 8.

---

## 6. Quyền sở hữu file — được sửa gì, không được sửa gì

| Đường dẫn | Chủ | Người khác được sửa? |
|---|---|---|
| `INSTRUCTION.md` | Giảng viên | **Không ai** |
| `source/pom.xml`, `source/common/**`, `server/.../ServerMain.java`, `ServerConfig.java`, `server/.../core/**`, `model/**`, `repo/**`, `tcp/**`, `.gitignore`, `statics/diagram.png` | Kiên | Không. Cần đổi → mục 8 |
| `server/.../db/**`, `server/.../auth/**`, `server/.../http/**`, `server/src/main/resources/db/**`, `source/web/**`, `tests/.../tests/db/**`, `tests/.../tests/auth/**`, `tests/.../tests/http/**` | Hoè | Không |
| `source/settlement/**`, `source/client/**`, `server/.../udp/**`, `server/.../settlement/**`, `tests/.../tests/udp/**`, `tests/.../tests/settlementclient/**` | Quân | Không |
| `README.md` gốc | Chung | Mỗi người chỉ sửa **mục của mảng mình** |
| `source/server/pom.xml`, `source/tests/pom.xml`, `source/config/*.example` | Kiên | Được **thêm** (không xóa/sửa dòng cũ) khi file này cho phép rõ; ghi rõ trong mô tả PR |

Repo bật branch protection: không đẩy thẳng lên `main`; mọi thay đổi qua Pull Request, Kiên duyệt.

---

## 7. Git, commit, Pull Request

### 7.1. Danh tính Git — bắt buộc

Lịch sử commit là căn cứ chấm **20% điểm cá nhân**. Commit phải đứng tên chính Cao Công Hoè:

```powershell
git config user.name     # phải là tên/tài khoản GitHub của Cao Công Hoè
git config user.email    # phải là email gắn với tài khoản GitHub đó
# nếu sai:
git config user.name "Tên GitHub của bạn"
git config user.email "email-github@example.com"
```

Agent **không** commit dưới tên người khác, **không** thêm dòng `Co-authored-by` của người khác (nếu công cụ
tự thêm dòng attribution của AI, hỏi người dùng có muốn giữ không).

### 7.2. Chia commit — người dùng tự chọn

Mục 9 chia phần việc thành các **mốc** (M1, M2, …). Mốc là đơn vị công việc trọn vẹn, **không bắt buộc** mỗi
mốc đúng một commit. Người dùng chọn (agent đã hỏi ở bước 0.1):

- **Mỗi mốc một commit** — gọn, dễ duyệt (khuyến nghị mặc định).
- **Gộp các mốc liên tiếp** — được, miễn là các mốc đó không phải chờ cùng một việc của Kiên khác nhau
  (mục 9 ghi rõ chỗ nào nên tách).
- **Nhiều commit nhỏ trong một mốc** — cũng tốt: giảng viên thích thấy commit thường xuyên. Khi đó mỗi
  commit vẫn phải build được.

Yêu cầu với **mọi** commit:

- Build được, test liên quan xanh. Không commit code dở làm hỏng build.
- Chỉ chứa file thuộc quyền mình (mục 6). Không commit `target/`, `data/`, `*.db`, `*.log`,
  `config/*.properties`, file IDE, file của agent.
- Message theo dạng **Conventional Commits**, phần mô tả tiếng Việt hoặc tiếng Anh, ngắn gọn, đúng việc:

```
<loại>(<phạm vi>): <mô tả>

feat(db): schema SQLite và các repository
feat(auth): băm mật khẩu PBKDF2 và AuthServiceImpl
test(db): kiểm tra phân trang lịch sử bid
fix(http): trả 401 khi thiếu token
docs(readme): mô tả REST API
```

Loại: `feat` (tính năng), `fix` (sửa lỗi), `test`, `docs`, `refactor`, `chore`.
Phạm vi gợi ý: `db`, `auth`, `http`, `web`, `readme`.

### 7.3. Nhánh và Pull Request

```powershell
git checkout main
git pull
git checkout -b feature/<tên-ngắn>     # ví dụ `feature/sqlite`, `feature/auth`, `feature/rest-api`, `feature/websocket`, `feature/web`
# ... làm việc, commit ...
git push -u origin feature/<tên-ngắn>
```

Mở Pull Request vào `main` trên GitHub. Mô tả PR gồm:

1. Làm mốc nào (M?), tóm tắt thay đổi.
2. Cách kiểm tra (lệnh test, cách chạy thử).
3. **"Cách gắn vào ServerMain"** (nếu có): đoạn code Kiên cần thêm vào `ServerMain` để dùng phần của bạn —
   bạn **không** tự sửa `ServerMain`.
4. Các file ngoài phần của mình có đụng tới (nếu được phép theo mục 6) và lý do.
5. Ảnh hưởng tới demo 11/11: "không ảnh hưởng" hoặc "để Draft".

Trước khi push: `git pull --rebase origin main` (hoặc merge `main` vào nhánh) để cập nhật, chạy lại
`mvn install`.

---

## 8. Khi bị chặn hoặc cần thành viên khác

Agent **phải dừng ngay** phần việc bị ảnh hưởng và **báo cho người dùng** khi gặp một trong các trường hợp:

- Cần sửa file **không thuộc quyền** Cao Công Hoè (mục 6), kể cả sửa một dòng.
- Một mốc ghi "Chờ K1/K2/K3/…" mà trên `main` chưa có code đó.
- Interface/lớp được nhắc trong file này **không tồn tại**, **khác chữ ký**, hoặc javadoc mâu thuẫn với file
  này.
- `main` build đỏ hoặc test của người khác lỗi.
- Cần kết quả của Hà Mạnh Quân (xem mục 9 — chỗ nào phụ thuộc).
- Yêu cầu không rõ, có hai cách hiểu, hoặc cần quyết định thiết kế ảnh hưởng người khác (tên khóa cấu hình,
  định dạng JSON, cột DB dùng chung…).

Khi đó agent:

1. Nói rõ cho người dùng: đang bị chặn ở đâu, vì sao, cần **ai** làm gì.
2. Soạn sẵn tin nhắn để người dùng gửi (Zalo/Messenger/GitHub Issue) theo mẫu:

```
[LiveBid] Cao Công Hoè cần hỗ trợ — <Kiên / Hà Mạnh Quân>
Đang làm: <mốc M?, tên việc>
Vướng: <mô tả ngắn, tên file/lớp/hàm cụ thể>
Cần: <việc cụ thể người kia phải làm, hoặc câu hỏi cần trả lời>
Đề xuất của mình (nếu có): <ví dụ chữ ký hàm muốn có>
Trong lúc chờ, mình sẽ: <làm tiếp mốc khác / dừng>
```

3. Trong lúc chờ: chuyển sang mốc khác **không** phụ thuộc chỗ bị chặn, nếu có. Không tự "vá tạm" bằng cách
   sửa file người khác, sao chép code của người khác vào package mình, hay tự định nghĩa lại hợp đồng chung.
4. Ghi lại các điểm đang chờ để nhắc lại người dùng ở cuối phiên.

---

## 9. Phần việc của Cao Công Hoè

### 9.0. Tổng quan phần việc

Hoè phụ trách **mọi thứ liên quan tới dữ liệu và trình duyệt**:

1. **CSDL SQLite** (`server/db`): schema, migration, cài đặt SQLite cho 4 interface repository và
   `TransactionManager` mà Kiên đã định nghĩa.
2. **Xác thực** (`server/auth`): băm mật khẩu PBKDF2, cài `AuthService`, token cho web, công cụ tạo tài khoản
   ADMIN.
3. **HTTP gateway** (`server/http`): REST API và WebSocket bằng Javalin, chạy cùng tiến trình server, cổng 8080.
4. **Trang web** (`source/web`): HTML + JavaScript thuần (không framework).

Hoè **không** viết logic đấu giá. REST và WebSocket chỉ chuyển yêu cầu vào `AuctionService` (của Kiên) rồi
đổi kết quả thành JSON.

| Mốc | Tên | Phụ thuộc | Mục tiêu |
|---|---|---|---|
| **M1** | SQLite: schema + các repository | Không | **Bắt buộc tối 10/11** |
| **M2** | Xác thực: PBKDF2, `AuthServiceImpl`, công cụ tạo admin | M1 (riêng công cụ tạo admin) | Tối 10/11 nếu kịp |
| **M3** | REST API (Javalin) | M2. Chạy thật trong server cần K3 | Sau 11/11 |
| **M4** | WebSocket `/ws` | **Chờ K1** (+ M3) | Sau K1 |
| **M5** | Trang web: đăng nhập, danh sách phiên, phòng đấu giá | M3, M4, K3 | |
| **M6** | Trang quản trị, thống kê, phiên của tôi, README | M5 | Cuối cùng |

**Gợi ý gộp commit** (người dùng quyết định, xem 7.2): M1+M2 gộp được (không chờ ai). **Không nên** gộp M3
với M4 vì M4 phải chờ K1 của Kiên. M5+M6 gộp được.

**Mục tiêu tối 10/11:** xong **M1** (và M2 nếu kịp), mở Pull Request. Không làm M3 trở đi trước buổi báo
cáo nếu chưa chắc giữ `mvn install` xanh.

---

### 9.1. M1 — SQLite: schema và các repository

**Mục đích:** thay các bản `InMemory*` bằng bản lưu bền vững SQLite, **hành vi giống hệt** bản trong bộ nhớ.

#### File cần tạo

```
server/src/main/resources/db/V1__init.sql
server/src/main/java/com/livebid/server/db/SqliteDatabase.java
server/src/main/java/com/livebid/server/db/Migrations.java          (có thể gộp vào SqliteDatabase)
server/src/main/java/com/livebid/server/db/SqliteTransactionManager.java
server/src/main/java/com/livebid/server/db/SqliteUserRepository.java
server/src/main/java/com/livebid/server/db/SqliteAuctionRepository.java
server/src/main/java/com/livebid/server/db/SqliteBidRepository.java
server/src/main/java/com/livebid/server/db/SqliteSettlementQueueRepository.java
tests/src/test/java/com/livebid/tests/db/SqliteRepositoryTest.java  (có thể tách nhiều lớp)
```

Đọc trước: `server/repo/*.java` (javadoc từng hàm), `server/model/*.java`, `server/repo/memory/*.java`.
Test cũ của bản trong bộ nhớ đã bị xóa khỏi repo nhưng vẫn xem được trong lịch sử Git để tham khảo hành vi:
`git show 37983bb:source/tests/src/test/java/com/livebid/tests/InMemoryRepositoryTest.java`.

#### Schema `V1__init.sql`

Tên cột và kiểu **phải đúng như sau** (Kiên và Quân dựa vào ý nghĩa các cột này):

```sql
CREATE TABLE users (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    username            TEXT    NOT NULL UNIQUE,          -- luôn chữ thường
    display_name        TEXT    NOT NULL,
    password_hash       TEXT    NOT NULL,                 -- Base64
    password_salt       TEXT    NOT NULL,                 -- Base64
    password_iterations INTEGER NOT NULL,
    role                TEXT    NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at          INTEGER NOT NULL                  -- epoch ms UTC
);

CREATE TABLE auctions (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    title             TEXT    NOT NULL,
    description       TEXT    NOT NULL DEFAULT '',
    starting_price    INTEGER NOT NULL CHECK (starting_price > 0),
    min_increment     INTEGER NOT NULL CHECK (min_increment > 0),
    current_price     INTEGER NOT NULL,
    current_bidder_id INTEGER NULL REFERENCES users (id),
    winner_id         INTEGER NULL REFERENCES users (id),
    starts_at         INTEGER NOT NULL,
    ends_at           INTEGER NOT NULL,
    extension_count   INTEGER NOT NULL DEFAULT 0,
    status            TEXT    NOT NULL CHECK (status IN ('SCHEDULED', 'ACTIVE', 'FINISHED')),
    version           INTEGER NOT NULL,
    created_by        INTEGER NOT NULL REFERENCES users (id),
    created_at        INTEGER NOT NULL,
    CHECK (ends_at > starts_at)
);

CREATE TABLE bids (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id  INTEGER NOT NULL REFERENCES auctions (id),
    user_id     INTEGER NOT NULL REFERENCES users (id),
    amount      INTEGER NOT NULL CHECK (amount > 0),
    accepted_at INTEGER NOT NULL,
    request_id  TEXT    NOT NULL
);
CREATE INDEX idx_bids_auction ON bids (auction_id, id);
CREATE INDEX idx_bids_user    ON bids (user_id, auction_id);

CREATE TABLE settlement_queue (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id      INTEGER NOT NULL UNIQUE REFERENCES auctions (id),
    status          TEXT    NOT NULL CHECK (status IN ('PENDING', 'DONE')),
    attempts        INTEGER NOT NULL DEFAULT 0,
    last_attempt_at INTEGER NULL
);
CREATE INDEX idx_settlement_status ON settlement_queue (status, id);
```

Enum Java (`Role`, `AuctionStatus`, `SettlementTask.Status`) lưu bằng `name()`, đọc bằng `valueOf()`.

#### `SqliteDatabase`

- `public static SqliteDatabase open(Path file)`: tạo thư mục cha nếu chưa có
  (`Files.createDirectories`), mở **một** `Connection` `jdbc:sqlite:<đường dẫn tuyệt đối>`, chạy:
  `PRAGMA foreign_keys = ON`, `PRAGMA journal_mode = WAL`, `PRAGMA busy_timeout = 5000`, rồi chạy migration.
- `public Connection connection()`, `public Path file()`, `close()` (implements `AutoCloseable`).
- Javadoc ghi rõ: **chỉ dùng từ luồng `BusinessExecutor`** (ngoại lệ: lúc khởi động, công cụ dòng lệnh, test
  đơn luồng). Không cần (và không nên) dùng connection pool.

#### Migration

- Bảng `schema_version (version INTEGER NOT NULL)`. Đọc phiên bản hiện tại (0 nếu chưa có).
- Danh sách migration trong code, ví dụ `List.of("db/V1__init.sql")`; chạy những bản có số lớn hơn phiên bản
  hiện tại, **mỗi bản trong một transaction**, cập nhật `schema_version`.
- Đọc file từ classpath (`getResourceAsStream`), UTF-8, tách câu lệnh theo `;` (bỏ dòng trống/chú thích `--`).
- Mở DB lần thứ hai không được lỗi, không chạy lại V1 (test kiểm tra).

#### `SqliteTransactionManager implements TransactionManager`

- Khởi tạo với `SqliteDatabase`.
- `inTransaction(work)`: nếu **chưa** trong transaction: `setAutoCommit(false)` → `work.run()` → `commit()`;
  nếu `work` ném `RuntimeException`/`Error` → `rollback()` rồi ném lại **đúng exception đó** (để
  `ServiceException` của Kiên đi xuyên qua); `finally` trả `setAutoCommit(true)`.
- Hỗ trợ lồng nhau: nếu đang trong transaction (đếm độ sâu) thì chỉ chạy `work.run()`, để transaction ngoài
  cùng commit/rollback.
- `SQLException` → bọc thành `DataAccessException`.

#### Các repository

Mỗi lớp nhận `SqliteDatabase` qua constructor, dùng **`PreparedStatement`** cho mọi câu lệnh (không nối chuỗi
SQL với dữ liệu), đóng `ResultSet`/`Statement` bằng try-with-resources, bọc `SQLException` thành
`DataAccessException`. Không tự mở transaction bên trong repository (việc đó của người gọi).

| Lớp | Lưu ý |
|---|---|
| `SqliteUserRepository` | `findByUsername` so khớp chữ thường (`username.toLowerCase(Locale.ROOT)`). `insert` lưu username chữ thường; trùng → `DataAccessException` (bắt lỗi ràng buộc UNIQUE). Lấy id bằng `getGeneratedKeys()` |
| `SqliteAuctionRepository` | `insert`: `current_price = starting_price`, `status = 'SCHEDULED'`, `version = 1`, `extension_count = 0`, bidder/winner `NULL`. `update`: ghi đè **mọi** cột theo id; 0 dòng bị ảnh hưởng → `DataAccessException`. `findPage(status, offset, limit)`: `status` null = tất cả, `ORDER BY starts_at, id LIMIT ? OFFSET ?`. `findByStatus`: cùng thứ tự. `findParticipatedBy(userId)`: phiên có ít nhất một bid của user (`WHERE id IN (SELECT auction_id FROM bids WHERE user_id = ?)`), cùng thứ tự. `findWonBy`: `winner_id = ?`. Cột NULL → `Long` null (`rs.getObject(..)` hoặc `wasNull()`) |
| `SqliteBidRepository` | `findHistory(auctionId, beforeBidId, limit)`: mới nhất trước, `ORDER BY id DESC`, `beforeBidId` null = từ mới nhất, khác null thì `id < beforeBidId`. `countByAuction` |
| `SqliteSettlementQueueRepository` | `enqueue`: `INSERT OR IGNORE` (đã có thì bỏ qua), `status='PENDING'`, `attempts=0`. `findPending`: `ORDER BY id`. `markDone`. `recordFailure`: `attempts = attempts + 1`, `last_attempt_at = ?` |

#### Test (`tests/.../tests/db/`)

Dùng `@TempDir Path dir` → `SqliteDatabase.open(dir.resolve("test.db"))`, đóng ở `@AfterEach`. Gọi repository
trực tiếp từ luồng test (đơn luồng, được phép). Tối thiểu:

1. Mở DB hai lần: không lỗi, đủ bảng, `schema_version = 1`.
2. User: `insert` lưu chữ thường; `findByUsername("ABC")` tìm được `abc`; trùng username → `DataAccessException`.
3. Auction: `insert` đúng giá trị mặc định; `update(a.withBid(...))` → đọc lại đúng giá, người dẫn đầu,
   `version` tăng; `findPage` lọc theo status, đúng thứ tự, đúng offset/limit.
4. Bid: lịch sử mới nhất trước, phân trang bằng `beforeBidId`; `countByAuction`.
5. `findParticipatedBy`, `findWonBy`.
6. Settlement queue: `enqueue` hai lần chỉ một bản; `recordFailure` tăng `attempts`; `markDone` → không còn
   trong `findPending`.
7. Transaction: trong `inTransaction` thêm user rồi ném `RuntimeException` → user **không** được lưu; exception
   ném ra đúng loại ban đầu.
8. Khóa ngoại: thêm bid với `auction_id` không tồn tại → `DataAccessException`.

Chú ý: `auctions.created_by` có khóa ngoại tới `users`, nên test phải tạo user trước khi tạo phiên.

#### Cách gắn vào ServerMain (ghi vào mô tả PR, Kiên làm — K3)

```java
SqliteDatabase db = SqliteDatabase.open(Path.of(config.getString("database.path", "./data/auction.db")));
TransactionManager tx = new SqliteTransactionManager(db);
UserRepository users = new SqliteUserRepository(db);
AuctionRepository auctions = new SqliteAuctionRepository(db);
BidRepository bids = new SqliteBidRepository(db);
SettlementQueueRepository settlementQueue = new SqliteSettlementQueueRepository(db);
// đóng db khi tắt server (shutdown hook), sau khi đóng BusinessExecutor
```

#### Câu hỏi vấn đáp cần chuẩn bị (agent giải thích cho Hoè)

- Vì sao dùng `PreparedStatement`? (chống SQL injection, kiểu dữ liệu đúng)
- Transaction là gì, vì sao thêm bid và cập nhật giá phải cùng một transaction? Rollback khi nào?
- Vì sao chỉ một `Connection` và mọi truy cập DB đi qua executor một luồng?
- Khóa ngoại, chỉ mục `bids(auction_id, id)` để làm gì? `INSERT OR IGNORE` cho hàng đợi Settlement?
- Vì sao tiền lưu `INTEGER` mà không dùng số thực?

---

### 9.2. M2 — Xác thực: PBKDF2, AuthServiceImpl, công cụ tạo admin

Đọc trước: `server/core/AuthService.java`, `server/core/BusinessExecutor.java`, `server/core/UserPrincipal.java`,
`server/core/ServiceException.java`, `common/dto/Role.java`.

#### File cần tạo

```
server/src/main/java/com/livebid/server/auth/PasswordHasher.java
server/src/main/java/com/livebid/server/auth/AuthServiceImpl.java
server/src/main/java/com/livebid/server/auth/CreateAdminTool.java
tests/src/test/java/com/livebid/tests/auth/PasswordHasherTest.java
tests/src/test/java/com/livebid/tests/auth/AuthServiceImplTest.java
```

Được phép **thêm** vào `source/server/pom.xml` (ghi rõ trong PR): một `<execution>` cho exec-maven-plugin để
chạy công cụ tạo admin (xem dưới). Được phép **thêm** khóa `auth.pbkdf2Iterations=600000` vào
`config/server.properties.example`.

#### `PasswordHasher`

- Thuật toán `PBKDF2WithHmacSHA256` (`SecretKeyFactory`), salt **16 byte** ngẫu nhiên (`SecureRandom`) cho
  **mỗi** mật khẩu, khóa 256 bit, số vòng lặp truyền qua constructor (mặc định 600 000; test dùng 1 000 cho
  nhanh).
- `HashedPassword hash(char[] password)` → record `HashedPassword(String hash, String salt, int iterations)`
  (Base64).
- `boolean verify(char[] password, String hash, String salt, int iterations)`: dùng **số vòng lặp đã lưu**
  (không phải số hiện tại), so sánh bằng `MessageDigest.isEqual` (thời gian không đổi).
- Xóa mảng `char[]` mật khẩu sau khi dùng (`Arrays.fill(pw, '\0')`) ở nơi tạo ra nó. **Không log mật khẩu.**
- Không dùng SHA-256 đơn lẻ, không lưu mật khẩu rõ.

#### `AuthServiceImpl implements AuthService`

Constructor: `AuthServiceImpl(BusinessExecutor executor, UserRepository users, PasswordHasher hasher, Clock clock)`.
Bên trong tạo một **pool băm riêng** (`Executors.newFixedThreadPool(2)`, luồng daemon tên `auth-hash-N`);
implements `AutoCloseable` để đóng pool.

**Quy tắc luồng (bắt buộc):** mọi lệnh gọi `users.*` nằm trong `executor.submit(...)`; băm/kiểm tra mật khẩu
chạy trên pool băm, **không** trên executor. Không `join()` trong executor.

`register(username, password, displayName)`:

1. Kiểm tra dữ liệu (đồng bộ, lỗi → future thất bại `VALIDATION_ERROR` với câu tiếng Việt cụ thể):
   username khớp `^[a-zA-Z0-9_]{3,30}$`; password 8–128 ký tự; displayName sau `trim()` dài 1–50, không chứa
   ký tự điều khiển.
2. `executor.submit` → `findByUsername(lower)` đã có → `USERNAME_TAKEN` ("Tên đăng nhập đã được sử dụng").
3. Pool băm → `hasher.hash(...)`.
4. `executor.submit` → `users.insert(new NewUser(lower, displayName, hash, salt, iterations, Role.USER, clock.millis()))`;
   nếu `DataAccessException` (hai người đăng ký cùng tên cùng lúc) → `USERNAME_TAKEN`.
5. Trả `UserPrincipal(id, username, displayName, Role.USER)`.

Ghép các bước bằng `thenCompose`/`thenApplyAsync(…, hashPool)`.

`login(username, password)`:

1. `executor.submit` → tìm user theo chữ thường.
2. Pool băm → `verify`. Nếu **không có user**, vẫn chạy `verify` với một hash giả cố định để thời gian trả lời
   tương đương (không lộ username nào tồn tại).
3. Sai user hoặc sai mật khẩu → **cùng một** lỗi `INVALID_CREDENTIALS` "Sai tên đăng nhập hoặc mật khẩu".

Token:

- `issueToken(user)`: 32 byte `SecureRandom`, Base64 URL không padding; lưu `ConcurrentHashMap<String, UserPrincipal>`.
- `resolveToken(token)`: null/rỗng/không có → `Optional.empty()`. `revokeToken(token)`: xóa.
- Token chỉ trong bộ nhớ, mất khi restart server (hạn chế đã biết, ghi trong javadoc).

#### `CreateAdminTool` (công cụ dòng lệnh)

- `main(String[] args)`: đường dẫn DB lấy từ `args[0]` nếu có, nếu không thì
  `ServerConfig.load(new String[0]).getString("database.path", "./data/auction.db")`.
- Hỏi username, tên hiển thị, mật khẩu (nhập hai lần, phải khớp). Đọc mật khẩu bằng
  `System.console().readPassword()`; nếu `System.console()` là `null` (thường gặp khi chạy qua Maven) thì đọc
  từ `System.in` và in cảnh báo "mật khẩu sẽ hiện trên màn hình".
- Kiểm tra dữ liệu như `register`; tạo user `Role.ADMIN` bằng `SqliteUserRepository` + `PasswordHasher` (công
  cụ chạy độc lập, gọi repository trực tiếp được). Trùng username → báo lỗi, thoát mã 1.
- **Không** có mật khẩu mặc định trong mã nguồn. Được dùng `System.out` ở công cụ này.
- Thêm vào `server/pom.xml`, bên trong plugin `exec-maven-plugin` đã có:

```xml
<executions>
    <execution>
        <id>create-admin</id>
        <goals><goal>java</goal></goals>
        <configuration>
            <mainClass>com.livebid.server.auth.CreateAdminTool</mainClass>
        </configuration>
    </execution>
</executions>
```

Chạy: `mvn -q install -DskipTests` rồi `mvn -pl server exec:java@create-admin`. Ghi lệnh này vào
`source/server/README.md` (thêm một mục ngắn "Tạo tài khoản ADMIN" — được phép thêm mục này).

#### Test

`PasswordHasherTest`: cùng mật khẩu hai lần → salt khác, hash khác; `verify` đúng/sai; dùng số vòng lặp đã lưu.

`AuthServiceImplTest` (dùng `new BusinessExecutor()`, `InMemoryUserRepository`, `new PasswordHasher(1000)`,
`Clock.systemUTC()`; đóng executor/service ở `@AfterEach`; chờ future bằng `.get(5, SECONDS)` trong test):

1. Đăng ký thành công: username lưu chữ thường, vai trò `USER`, mật khẩu không lưu rõ.
2. Đăng ký trùng (khác hoa thường) → `USERNAME_TAKEN`.
3. Username/mật khẩu/tên hiển thị sai → `VALIDATION_ERROR`.
4. Đăng nhập đúng → đúng `UserPrincipal`; sai mật khẩu và user không tồn tại → cùng `INVALID_CREDENTIALS`.
5. Token: cấp → giải được; thu hồi → rỗng; token lạ → rỗng.

Lỗi lấy ra bằng `ServiceException` bên trong `ExecutionException`/`CompletionException` (xem cách
`BidTimingTest` của Kiên làm).

#### Cách gắn vào ServerMain (ghi vào PR, Kiên làm — K3)

```java
PasswordHasher hasher = new PasswordHasher(config.getInt("auth.pbkdf2Iterations", 600_000));
AuthServiceImpl auth = new AuthServiceImpl(executor, users, hasher, Clock.systemUTC());
// handler LOGIN/REGISTER/LOGOUT trong MessageRouter do Kiên viết, gọi auth.login/register
```

#### Câu hỏi vấn đáp

- Vì sao PBKDF2 mà không SHA-256? Salt để làm gì? Vì sao lưu số vòng lặp?
- Vì sao băm mật khẩu không chạy trên executor nghiệp vụ?
- Vì sao báo cùng một lỗi cho "sai user" và "sai mật khẩu"?
- Token web khác trạng thái đăng nhập trên kết nối TCP thế nào?

---

### 9.3. M3 — REST API (Javalin)

Phụ thuộc: M2. Chạy được độc lập bằng test. Chạy thật trong server cần Kiên gắn vào `ServerMain` (K3).

Đọc trước: `server/core/AuctionService.java`, `common/dto/*`, `common/payload/*`, `common/protocol/ErrorCode.java`.
Tài liệu Javalin 6: https://javalin.io/documentation

#### File

```
server/src/main/java/com/livebid/server/http/HttpGateway.java
server/src/main/java/com/livebid/server/http/ApiErrors.java     (đổi lỗi → JSON + mã HTTP; tùy chọn)
tests/src/test/java/com/livebid/tests/http/RestApiTest.java
```

#### `HttpGateway`

```java
public final class HttpGateway implements AutoCloseable {
    public record Settings(int port, Path webRoot) { }
    public HttpGateway(Settings settings, AuthService auth, AuctionService auctions,
                       Supplier<Optional<SettlementSummaryDto>> stats);
    public void start();   // mở cổng; port 0 = cổng ngẫu nhiên (cho test)
    public int port();     // cổng thực tế sau start()
    public void close();
}
```

- Phục vụ file tĩnh từ `webRoot` (thư mục `source/web`, cấu hình `http.webRoot`, mặc định `web`) tại `/` với
  `Location.EXTERNAL`. Nếu thư mục không tồn tại: log cảnh báo, vẫn chạy API.
- JSON đọc/ghi bằng `JsonCodec.gson()`. Trả về `Content-Type: application/json; charset=utf-8`.
- Thành công: body là **DTO trực tiếp** (không bọc phong bì `Message`).
- Lỗi: mã HTTP = `errorCode.httpStatus()`, body:
  `{"error":{"code":"BID_TOO_LOW","message":"...","details":{...}}}`.
  `ServiceException` → mã của nó; body JSON sai/thiếu → 400 `INVALID_MESSAGE`; lỗi khác → 500
  `INTERNAL_ERROR` (log chi tiết, không gửi stack trace cho client).
- Kết quả bất đồng bộ: `ctx.future(() -> future.handle((value, error) -> { ...ghi ctx...; return null; }))`.
  **Không** `join()` trên luồng HTTP.
- Xác thực: header `Authorization: Bearer <token>` → `auth.resolveToken(token)`; lưu `UserPrincipal` vào
  `ctx.attribute("principal")`. Thiếu/sai → 401 `UNAUTHORIZED`. Áp cho mọi `/api/*` trừ `register`, `login`.
- Kiểm tra quyền ADMIN nằm trong `AuctionService` (trả `FORBIDDEN`); gateway chỉ chuyển `UserPrincipal` vào.

#### Endpoint

| Phương thức | Đường dẫn | Body / query | Trả về | Ghi chú |
|---|---|---|---|---|
| POST | `/api/register` | `RegisterPayload` | 201 `LoginResult{user, token}` | `auth.register` rồi `issueToken` |
| POST | `/api/login` | `LoginPayload` | 200 `LoginResult` | |
| POST | `/api/logout` | — | 204 | `revokeToken` |
| GET | `/api/me` | — | 200 `UserView` | |
| GET | `/api/auctions` | `?status=ACTIVE&page=1&pageSize=20` | 200 `PageResult<AuctionView>` | `status` không có = tất cả; mặc định page 1, pageSize 20 |
| GET | `/api/auctions/{id}` | — | 200 `AuctionView` | |
| GET | `/api/auctions/{id}/bids` | `?before=<bidId>&limit=20` | 200 `List<BidView>` | |
| GET | `/api/me/auctions` | `?filter=participated` hoặc `won` | 200 `List<AuctionView>` | không phân biệt hoa thường |
| GET | `/api/stats` | — | 200 `SettlementSummaryDto` | `stats.get()` rỗng → 503 `SERVER_BUSY` "Dịch vụ thống kê chưa sẵn sàng" |
| POST | `/api/auctions` | `AuctionDraft` | 201 `AuctionView` | ADMIN |
| PUT | `/api/auctions/{id}` | `{"expectedVersion":3,"draft":{...AuctionDraft}}` | 200 `AuctionView` | ADMIN; id lấy từ đường dẫn |

Tham số số không hợp lệ (`id=abc`, `page=-1` sai kiểu) → 400 `VALIDATION_ERROR`/`INVALID_MESSAGE`.
`stats.get()` có thể chặn (gọi RMI) — gọi ngoài executor, trên luồng HTTP là được.

Được phép **thêm** khóa `http.webRoot=web` vào `config/server.properties.example`.

#### Test `RestApiTest`

Dựng đủ phụ thuộc thật mà **không** cần SQLite hay server chạy sẵn:

```java
BusinessExecutor executor = new BusinessExecutor();
var bidsRepo = new InMemoryBidRepository();
var auctionsRepo = new InMemoryAuctionRepository(bidsRepo);
var usersRepo = new InMemoryUserRepository();
var service = new AuctionServiceImpl(executor, auctionsRepo, bidsRepo, usersRepo,
        new InMemorySettlementQueueRepository(), new InMemoryTransactionManager(),
        Clock.systemUTC(), AuctionSettings.defaults());
var auth = new AuthServiceImpl(executor, usersRepo, new PasswordHasher(1000), Clock.systemUTC());
var gateway = new HttpGateway(new HttpGateway.Settings(0, Path.of("web")), auth, service, Optional::empty);
gateway.start();  // gọi http://127.0.0.1:gateway.port()/... bằng java.net.http.HttpClient
```

Tài khoản ADMIN trong test: tạo trực tiếp qua `usersRepo.insert(new NewUser(..., Role.ADMIN, ...))` với hash từ
`PasswordHasher(1000)`, rồi đăng nhập qua `/api/login`.

Ca tối thiểu: đăng ký → 201 có token; đăng ký trùng → 409; đăng nhập sai → 401; gọi `/api/auctions` không token
→ 401; ADMIN tạo phiên → 201; USER tạo phiên → 403; danh sách và chi tiết phiên; phiên không tồn tại → 404;
`/api/stats` → 503 khi chưa có Settlement; body JSON hỏng → 400. Đóng gateway, executor ở `@AfterEach`.

#### Cách gắn vào ServerMain (ghi vào PR — K3)

```java
HttpGateway http = new HttpGateway(new HttpGateway.Settings(
        config.getInt("server.httpPort", Protocol.DEFAULT_HTTP_PORT),
        Path.of(config.getString("http.webRoot", "web"))),
        auth, auctionService, statsSupplier /* settlementClient::fetchSummary của Quân, hoặc Optional::empty */);
http.start();
```

Thử bằng curl (PowerShell dùng `curl.exe`):

```powershell
curl.exe -s -X POST http://127.0.0.1:8080/api/register -H "Content-Type: application/json" -d '{\"username\":\"hoe\",\"password\":\"12345678\",\"displayName\":\"Hoè\"}'
curl.exe -s http://127.0.0.1:8080/api/auctions -H "Authorization: Bearer <token>"
```

#### Câu hỏi vấn đáp

- REST là gì, vì sao dùng REST cho tra cứu/quản trị mà không dùng cho đặt giá?
- Ý nghĩa các mã 200/201/204/400/401/403/404/409/503. 401 khác 403 thế nào?
- Vì sao handler HTTP không được `join()` future?

---

### 9.4. M4 — WebSocket `/ws`

**Chờ K1** (Kiên đổi `MessageRouter` sang làm việc với `ClientChannel`, chuyển trạng thái đăng nhập vào
`ClientChannel`, thêm handler `AUTH`). Trước khi bắt đầu: đọc `server/tcp/MessageRouter.java` và
`server/core/ClientChannel.java` trên `main` mới nhất. Nếu `MessageRouter.Handler` vẫn nhận `ClientSession`
và `ClientChannel` chưa có `principal()` → **dừng, báo Kiên** (mục 8). Không tự viết router riêng cho WebSocket.

**Thiết kế:** WebSocket **dùng lại** `MessageRouter` của Kiên — cùng định dạng thông điệp, cùng handler, cùng
`AuctionService`. Phần của Hoè chỉ là **lớp kênh** cho WebSocket và đăng ký endpoint.

#### File

```
server/src/main/java/com/livebid/server/http/WsChannel.java
(sửa) server/src/main/java/com/livebid/server/http/HttpGateway.java   — thêm app.ws("/ws", ...)
tests/src/test/java/com/livebid/tests/http/WebSocketTest.java
```

#### `WsChannel implements ClientChannel`

- `id()`: `"ws-" + số tăng dần` hoặc UUID. `transport()` = `WEBSOCKET`. `remoteAddress()` từ context.
- Trạng thái đăng nhập: trường `volatile UserPrincipal` theo đúng các hàm K1 thêm vào `ClientChannel`.
- `send(Message)`: **không chặn** — bỏ vào `ArrayBlockingQueue` dung lượng 256; **một** luồng ghi riêng (daemon,
  tên `ws-write-<id>`) lấy ra, gọi `wsContext.send(JsonCodec.encode(message))`. Hàng đợi đầy → `close("client
  nhận quá chậm")`. Lý do: Jetty không cho hai luồng gửi đồng thời trên một session; executor nghiệp vụ không
  được chờ mạng.
- `close(reason)`: một lần duy nhất (`AtomicBoolean`), đóng session WebSocket, dừng luồng ghi.

#### Endpoint trong `HttpGateway`

- `HttpGateway` nhận thêm `MessageRouter` (constructor mới hoặc setter — ghi rõ trong PR).
- `onConnect`: tạo `WsChannel`, lưu map `sessionId → WsChannel`; giới hạn kích thước thông điệp 64 KiB
  (cấu hình Jetty `maxTextMessageSize`, xem tài liệu Javalin 6/Jetty 11).
- `onMessage`: chuyển nguyên chuỗi JSON cho router theo API của K1 (ví dụ `router.dispatch(channel, text)`).
- `onClose`/`onError`: đánh dấu kênh đóng, gọi hàm báo ngắt kết nối của router/`AuctionService.onDisconnect`
  (theo API K1), xóa khỏi map.
- Trình tự phía trình duyệt: mở `ws://<host>:8080/ws` → gửi `AUTH` với token lấy từ `/api/login` → `JOIN_AUCTION`
  → `PLACE_BID`… Server đẩy `AUCTION_*` về.

#### Test `WebSocketTest`

Dùng `java.net.http.HttpClient.newWebSocketBuilder()`. Dựng server như `RestApiTest` + router theo cách Kiên
cung cấp ở K1/K3 (nếu chưa có cách tạo router kèm handler → hỏi Kiên). Ca tối thiểu: `PING` → có `serverTime`;
`PLACE_BID` khi chưa `AUTH` → `UNAUTHORIZED`; `AUTH` đúng token → thành công; ADMIN tạo phiên đang mở, hai
USER vào phòng, một người đặt giá → cả hai nhận `AUCTION_UPDATED`.

#### Câu hỏi vấn đáp

- WebSocket bắt đầu bằng HTTP Upgrade thế nào? Chạy trên TCP không?
- Vì sao WebSocket không cần tự đóng khung như TCP thô?
- Vì sao mỗi kênh cần hàng đợi gửi và một luồng ghi?

---

### 9.5. M5 — Trang web người dùng

Phụ thuộc: M3, M4, K3. HTML + CSS + JavaScript **thuần** (không framework, không thư viện CDN). Dùng file sẵn có
`source/web/index.html`, `app.js`, `style.css` (có thể tách thêm file JS).

#### Màn hình và hành vi

1. **Đăng nhập / Đăng ký** (REST `/api/login`, `/api/register`). Lưu token và `UserView` trong
   `sessionStorage`. Hiện lỗi tiếng Việt từ `error.message`.
2. **Danh sách phiên** (REST `/api/auctions`): lọc `SCHEDULED`/`ACTIVE`/`FINISHED`/tất cả, phân trang, nút làm
   mới. Mỗi dòng: tên, giá hiện tại, trạng thái, thời gian bắt đầu/kết thúc. Bấm để vào phòng.
3. **Phòng đấu giá** (WebSocket):
   - Kết nối `/ws`, gửi `AUTH`, rồi `JOIN_AUCTION` → `RoomSnapshot` (phiên + 20 bid gần nhất).
   - Hiển thị: tên, mô tả, giá khởi điểm, bước giá, **"Giá khởi điểm"** khi `hasBids=false` và **"Giá đã được
     đặt"** khi `true`, người dẫn đầu, đồng hồ đếm ngược, lịch sử bid, trạng thái.
   - **So `version`:** bỏ qua `AuctionView` có `version` ≤ bản đang hiện.
   - **Đồng hồ:** khi nhận `AuctionView`, tính `offset = serverTime - Date.now()`; thời gian còn lại =
     `endsAt - (Date.now() + offset)`; cập nhật mỗi 250 ms. Không dùng giờ máy làm mốc tuyệt đối.
   - Ô đặt giá điền sẵn `minimumNextBid` (= `hasBids ? currentPrice + minIncrement : startingPrice`). Gửi
     `PLACE_BID` với `requestId = crypto.randomUUID()`; khóa nút khi đang chờ. Bị từ chối → hiện lý do; nếu
     `BID_TOO_LOW` điền lại `details.minimumAllowed`. Quá 10 giây không có trả lời → **"Chưa xác định kết quả"**
     (không tự kết luận thất bại).
   - Có `AUCTION_UPDATED` → cập nhật giá, tải lại lịch sử (`GET_BID_HISTORY`, `limit` 20). Trước đó mình dẫn đầu,
     nay người khác dẫn đầu → thông báo **"Bạn đã bị vượt giá"** (so `leaderDisplayName` với tên hiển thị của
     mình; hạn chế: tên hiển thị có thể trùng).
   - `AUCTION_EXTENDED` → báo "Phiên được gia hạn". `AUCTION_FINISHED` → hiện người thắng hoặc **"Chưa có người
     thắng"**, khóa ô đặt giá.
   - **Trạng thái kết nối** luôn hiện; mất kết nối → khóa nút đặt giá, tự kết nối lại (chờ 1, 2, 4… tối đa 10
     giây), gửi lại `AUTH` + `JOIN_AUCTION` để nhận snapshot mới.
   - Gửi `PING` mỗi 15 giây (server đóng kết nối im lặng quá 45 giây).
4. Định dạng tiền `new Intl.NumberFormat('vi-VN').format(x) + 'đ'`; thời gian theo giờ địa phương.
5. **An toàn:** chèn dữ liệu bằng `textContent`, **không** dùng `innerHTML` với dữ liệu người dùng (chống XSS).

URL API/WS lấy theo `location.host` (đã có trong `app.js`), vì trang do chính server phục vụ.

#### Kiểm tra

Không bắt buộc test tự động cho web. Kiểm tra tay: hai trình duyệt (một cửa sổ ẩn danh) vào cùng phòng, đặt
giá, cả hai cập nhật không cần tải lại; tắt server → trạng thái "mất kết nối", nút bị khóa; bật lại → tự vào
lại phòng. Chụp màn hình lưu vào `statics/` (tên `web_*.png`).

---

### 9.6. M6 — Quản trị, thống kê, phiên của tôi, tài liệu

1. **Phiên của tôi**: `/api/me/auctions?filter=participated|won`.
2. **Quản trị** (chỉ hiện khi `role = ADMIN`): form tạo phiên (`POST /api/auctions`) và sửa phiên chưa bắt đầu
   (`PUT /api/auctions/{id}` với `expectedVersion`). Giờ nhập bằng `<input type="datetime-local">` → đổi sang
   epoch ms. Kiểm tra phía client (giá > 0, kết thúc sau bắt đầu) nhưng vẫn hiện lỗi từ server.
   `VERSION_CONFLICT` → báo "Phiên đã bị thay đổi, tải lại".
3. **Thống kê** (`/api/stats`): tổng số phiên, số phiên có người thắng, tổng giá trị, top người thắng. 503 →
   hiện "Dịch vụ thống kê chưa chạy" (không coi là lỗi). Dữ liệu thật cần `SettlementClient` của Quân được gắn.
4. **Tài liệu:**
   - `README.md` gốc, mục **"🔗 GIAO TIẾP"**: thêm bảng endpoint REST (đường dẫn, phương thức, quyền, body, mã
     trả về), ví dụ curl, mô tả WebSocket (`AUTH` → `JOIN_AUCTION` → `PLACE_BID`, sự kiện). Chỉ sửa phần
     REST/WebSocket.
   - `source/web/README.md`: cách mở trang, các màn hình, cách thử.
   - Mục "🔗 API" trong `source/server/README.md`: cập nhật bảng cho đúng (được phép sửa mục này).

---

### 9.7. Phụ thuộc với người khác — tóm tắt

| Hoè cần | Từ ai | Dùng ở |
|---|---|---|
| K1: `MessageRouter` dùng `ClientChannel`, `principal` trong `ClientChannel`, handler `AUTH` | Kiên | M4 |
| K3: `ServerMain` gắn SQLite, `AuthServiceImpl`, `HttpGateway`, các handler lệnh | Kiên | chạy thật M3–M6 |
| `Supplier<Optional<SettlementSummaryDto>>` (`settlementClient::fetchSummary`) | Quân (Kiên gắn) | `/api/stats` (M3, M6) |

| Người khác cần Hoè | Ai | Dùng ở |
|---|---|---|
| Bản SQLite các repository (M1) | Kiên | K3 |
| `AuthServiceImpl` (M2) | Kiên | handler LOGIN/REGISTER |
| `SqliteSettlementQueueRepository` (M1) | Quân | `SettlementClient` chạy thật |

---

## 10. Checklist trước khi mở Pull Request

Agent tự kiểm tra từng dòng và báo kết quả cho người dùng:

- [ ] Đã hỏi người dùng cách chia commit và làm đúng như vậy.
- [ ] `git config user.name` / `user.email` là của Cao Công Hoè; commit không đứng tên người khác.
- [ ] Đang ở nhánh `feature/...`, đã cập nhật từ `main` mới nhất.
- [ ] `mvn install` trong `source/` ra **BUILD SUCCESS**, mọi test (của cả nhóm) xanh.
- [ ] Chỉ sửa file thuộc quyền mình (mục 6); file chung chỉ **thêm** đúng chỗ được phép và đã ghi trong mô tả PR.
- [ ] Không commit `target/`, `data/`, `*.db`, `*.log`, `config/*.properties`, file IDE, file của agent,
      file `HUONG_DAN_*.md`.
- [ ] Không có mật khẩu, token, đường dẫn máy cá nhân trong code.
- [ ] Test mới không dùng cổng cố định, không cần mạng ngoài, không cần chương trình khác chạy sẵn, chạy nhanh.
- [ ] Mọi truy cập repository nằm trong `BusinessExecutor.submit(...)` (trừ test đơn luồng/công cụ dòng lệnh).
      Không gọi mạng/băm mật khẩu/`join()` bên trong executor.
- [ ] Javadoc tiếng Việt cho lớp/hàm public; thông báo cho người dùng bằng tiếng Việt có dấu.
- [ ] Mô tả PR có: mốc, cách kiểm tra, "Cách gắn vào ServerMain", ảnh hưởng tới demo 11/11.
- [ ] Đã giải thích cho Cao Công Hoè phần vừa làm và các câu hỏi vấn đáp liên quan.
- [ ] Đã liệt kê cho người dùng các điểm đang chờ người khác (mục 8) để nhắn cho Kiên/Hà Mạnh Quân.

**Trước 11/11/2026:** nếu còn nghi ngờ bất kỳ dòng nào ở trên → mở PR dạng **Draft**, ghi "chưa merge trước
buổi báo cáo 11/11".
