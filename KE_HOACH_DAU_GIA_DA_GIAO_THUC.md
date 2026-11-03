# LiveBid — Hệ thống đấu giá thời gian thực đa giao thức

> Kế hoạch cho bài tập lớn môn Lập trình mạng. Tài liệu này là đặc tả và lộ trình, chưa phải hướng dẫn chạy một ứng dụng đã có. Tên lớp, cổng, đường dẫn là quy ước đề xuất; chỉnh lại khi bắt tay làm.
>
> Kế hoạch đã được đối chiếu với [INSTRUCTION.md](https://github.com/hungdn1701/jnp-project-starter/blob/main/INSTRUCTION.md) của giảng viên (repo `jnp-project-starter`). Khi có mâu thuẫn, **INSTRUCTION.md được ưu tiên** (xem mục 1.3).

## 1. Tổng quan

Nhiều người dùng tham gia các phiên đấu giá và đặt giá theo thời gian thực. Người dùng có thể vào bằng **ứng dụng desktop** (giao thức TCP tự thiết kế) hoặc **trình duyệt** (WebSocket). Mọi đường vào đều đi qua **một nơi duy nhất quyết định chấp nhận giá**.

Ví dụ: bàn phím cơ, giá khởi điểm 200.000đ, bước giá 20.000đ, kéo dài 5 phút. A đặt 200.000đ, B đặt 240.000đ, A đặt 260.000đ. Hết giờ không ai vượt, A thắng với 260.000đ.

### 1.1. Mục tiêu học tập

| Kiến thức của môn | Được dùng ở đâu | Lý do |
|---|---|---|
| TCP socket | Giao thức đặt giá chính (desktop ↔ server) | Cần tin cậy, đúng thứ tự, tự thiết kế giao thức |
| UDP broadcast | Client tự tìm server trong LAN | Chưa biết IP nên chưa thể mở TCP |
| UDP multicast | Thông báo "phiên sắp bắt đầu" | Một-nhiều, nhẹ, mất gói không gây hậu quả |
| Web service (REST) | Tra cứu phiên, lịch sử, thống kê; admin tạo phiên | Request/response, dễ test bằng Postman/curl |
| WebSocket | Trang web xem phòng và đặt giá real-time | Trình duyệt không mở được TCP thô; cần kênh hai chiều |
| RMI | Server gọi `SettlementService` khi phiên kết thúc | Gọi hàm từ xa; việc này nằm ngoài đường găng của bid |
| Đa luồng, đồng bộ | Hàng đợi nghiệp vụ, hàng đợi gửi từng client | Nhiều người đặt giá cùng lúc |

Khi bảo vệ, điểm ăn tiền là giải thích được **vì sao** mỗi giao thức nằm đúng chỗ của nó, không phải số lượng công nghệ.

### 1.2. Phạm vi

- Hai vai trò: `ADMIN` và `USER`.
- Một server trung tâm, nhiều client; dữ liệu lưu ở server (SQLite).
- Mô phỏng đấu giá, chưa có thanh toán thật, giao hàng, ảnh sản phẩm.
- Tiền tệ VND, số nguyên (`long`).
- Demo trên một máy hoặc nhiều máy Windows trong cùng mạng LAN.
- Chạy **local** (đúng yêu cầu môn: local hoặc Docker). Không dùng Docker làm cách chạy chính vì broadcast/multicast thường không đi qua mạng Docker.
- Nhóm tối đa 3 sinh viên.

### 1.3. Yêu cầu của môn học (INSTRUCTION.md)

INSTRUCTION.md không bắt buộc giao thức hay ngôn ngữ cụ thể; nó quy định **cấu trúc repo, tài liệu, Git và cách chấm**. Các điểm phải tuân thủ:

| Yêu cầu | Cách kế hoạch đáp ứng |
|---|---|
| Tạo repo từ `jnp-project-starter`, giữ cấu trúc; **không sửa/xóa `INSTRUCTION.md`** | Fork/clone ngay tuần 1; cấu trúc ở mục 3.2 |
| Mã nguồn nằm trong `source/`, có `source/client/`, `source/server/` (mỗi thư mục có `README.md`), module khác tùy ý | `common`, `settlement`, `web`, `tests` cũng đặt trong `source/` |
| Hình ảnh, sơ đồ, dataset nằm trong `statics/` | Sơ đồ kiến trúc, luồng đặt giá, ERD, dữ liệu demo |
| `README.md` gốc chứa **toàn bộ** mô tả hệ thống, giao tiếp, hướng dẫn build/run, thông tin nhóm, chủ đề | Dàn ý ở mục 22.1; không tách tài liệu giao thức ra `docs/` |
| Thư viện ngoài phải liệt kê ở mục "Công nghệ sử dụng" kèm cách cài | Javalin, Gson, SQLite JDBC, JUnit (mục 2) |
| Chạy được local hoặc Docker; chỉ chấm repo có README đầy đủ và **chạy được** | Chạy local trên Windows; test lại theo đúng README ở tuần 6 |
| Commit thường xuyên, message rõ ràng; **lịch sử commit thể hiện đóng góp của tất cả thành viên** | Mỗi người tự commit phần mình (mục 15.2, 15.3) |
| Trước hạn: README đầy đủ, merge về `main` hoặc tạo release, test toàn hệ thống | Tuần 6 (mục 15) |
| Hạn nộp | Theo thông báo LMS/email |

Tiêu chí chấm điểm:

| Hạng mục | Tỷ trọng | Hình thức | Trọng tâm cho LiveBid |
|---|---|---|---|
| Ý tưởng và chức năng chính | 40% | Nhóm | Đấu giá real-time chạy đúng; mỗi giao thức đặt đúng chỗ |
| Tổ chức mã nguồn & cấu trúc repo | 20% | **Cá nhân** | Đúng cấu trúc, code sạch, commit của chính mình |
| Tài liệu & hướng dẫn chạy | 15% | Nhóm | Người lạ làm theo README là chạy được |
| Trình bày & báo cáo | 25% | **Cá nhân** | Demo, giải thích, vấn đáp phần code mình phụ trách |

Quy trình kiểm tra của giảng viên: đọc `README.md` gốc và cấu trúc repo → chạy demo theo README → vấn đáp **từng thành viên** về phần code họ phụ trách. Vì 45% điểm là cá nhân, mỗi người phải nắm chắc phần mình và lý do thiết kế.

## 2. Công nghệ và môi trường

| Thành phần | Đề xuất | Ghi chú |
|---|---|---|
| Ngôn ngữ | Java 21 | Dùng chung server, client, gateway |
| TCP/UDP/RMI | `java.net`, `java.rmi` (có sẵn JDK) | Không cần thư viện ngoài |
| REST + WebSocket | Javalin (chạy trên Jetty) | JDK 21 không có WebSocket **server**, nên cần thư viện; Javalin gộp cả hai trong một cổng HTTP |
| JSON | Gson | |
| CSDL | SQLite qua JDBC | File DB nằm trên máy server |
| Desktop client | Swing (có sẵn JDK) hoặc JavaFX | Chọn theo mức quen của nhóm; Swing đỡ phải đóng gói JavaFX |
| Web client | HTML + JS thuần | Không cần framework |
| Build | Maven | Khóa phiên bản trong `pom.xml` |
| Kiểm thử | JUnit + client TCP không giao diện | |

Nên chốt cùng một bộ phiên bản cho cả nhóm ngay từ tuần 1. Toàn bộ bảng trên (kèm phiên bản và cách cài JDK/Maven) được chép vào mục **"Công nghệ sử dụng"** của `README.md` gốc, theo yêu cầu của INSTRUCTION.md. Maven tự tải thư viện, nên hướng dẫn cài chỉ cần JDK 21 và Maven.

## 3. Kiến trúc

```text
 Trình duyệt ──WebSocket──► ┐
 Postman/curl ──REST──────► ├─► [HTTP/WS Gateway] ─┐
                                                   │ gọi cùng một AuctionService
 Desktop client ──TCP (JSON\n)────────────────────┼──► [AUCTION SERVER] ──JDBC──► SQLite
                                                   │     single-thread executor
 Desktop client ◄──UDP broadcast (tìm server)──────┤          │
 Mọi client     ◄──UDP multicast (sắp bắt đầu)─────┘          └──RMI──► [Settlement Service]
```

Có thể gộp Gateway vào cùng tiến trình với Auction Server (đơn giản hơn); điều bắt buộc là mọi thao tác ghi đều đi qua cùng một `AuctionService` và cùng một executor.

### 3.1. Thành phần server

| Thành phần | Nhiệm vụ |
|---|---|
| `TcpServer` | Mở cổng TCP, nhận kết nối |
| `ClientSession` | Đọc thông điệp, giữ trạng thái đăng nhập, hàng đợi gửi riêng |
| `MessageRouter` | Kiểm tra cấu trúc, chuyển yêu cầu tới chức năng |
| `AuthService` | Đăng ký, đăng nhập, phân quyền |
| `AuctionService` | Tạo phiên, đặt giá, đổi trạng thái, chốt người thắng |
| `RoomManager` | Theo dõi client nào đang xem phòng nào (cả TCP và WebSocket) |
| `AuctionScheduler` | Đưa tác vụ mở/đóng phiên vào executor |
| `UdpDiscoveryResponder` | Trả lời broadcast "server ở đâu" |
| `UdpAnnouncer` | Gửi multicast "phiên sắp bắt đầu" |
| `HttpGateway` | REST + WebSocket (Javalin) |
| `SettlementClient` | Gọi RMI sang Settlement, có hàng đợi retry |
| `Repository` | Truy vấn, ghi dữ liệu |

### 3.2. Cấu trúc repository (theo INSTRUCTION.md)

```text
<repo>/                        # tạo từ jnp-project-starter
├── README.md                  # Thông tin nhóm, chủ đề, công nghệ, kiến trúc, giao thức, build/run, demo
├── INSTRUCTION.md             # Của giảng viên — KHÔNG sửa, KHÔNG xóa
├── statics/                   # Hình ảnh, sơ đồ, dataset
│   ├── diagram.png            # Sơ đồ kiến trúc
│   ├── bid_flow.png           # Luồng đặt giá
│   ├── erd.png                # Mô hình dữ liệu
│   └── dataset_sample.csv     # Dữ liệu demo (không chứa thông tin thật)
└── source/                    # Toàn bộ mã nguồn
    ├── .gitignore
    ├── pom.xml                # Maven cha, khai báo các module
    ├── common/                # DTO, mã lỗi, JSON, interface RMI dùng chung
    ├── server/                # README.md — TCP, nghiệp vụ, gateway REST/WS, UDP, repository
    ├── client/                # README.md — Desktop client (Swing/JavaFX)
    ├── settlement/            # README.md — Dịch vụ RMI
    ├── web/                   # README.md — index.html, app.js, style.css
    ├── tests/                 # Client kiểm thử không giao diện, test tải
    └── config/                # Cấu hình mẫu, không chứa mật khẩu
```

- `source/client/README.md` và `source/server/README.md` là bắt buộc; các module khác nên có README ngắn (chức năng, cách chạy riêng, cổng dùng).
- Không có thư mục `docs/` riêng: mô tả giao thức TCP/WebSocket, endpoint REST, interface RMI viết trong `README.md` gốc (hoặc tóm tắt ở gốc và chi tiết trong README module, có liên kết); hình đặt trong `statics/`.
- `source/.gitignore` loại trừ `data/`, log, cấu hình cá nhân, `target/`, file IDE.

## 4. Chức năng

### 4.1. Tài khoản và phân quyền

- Đăng ký (tên đăng nhập, tên hiển thị, mật khẩu), đăng nhập, đăng xuất.
- Tên đăng nhập: chữ Latin, số, gạch dưới, 3–30 ký tự, không trùng. Mật khẩu tối thiểu 8 ký tự. Tên hiển thị hỗ trợ tiếng Việt.
- Đăng ký công khai luôn tạo `USER`. Tài khoản `ADMIN` tạo bằng công cụ riêng trên server, nhập mật khẩu khi chạy, không để mật khẩu mặc định trong mã nguồn.
- Server kiểm tra quyền ở **mọi** yêu cầu, kể cả khi giao diện đã ẩn nút.
- Một kết nối chỉ đại diện cho một tài khoản; đóng kết nối thì xóa xác thực và rời phòng.
- Phiên web dùng token do server cấp sau khi đăng nhập (lưu trong bộ nhớ ở bản đầu).

### 4.2. Quản lý phiên (ADMIN)

- Nhập: tên sản phẩm, mô tả, giá khởi điểm, bước giá, giờ bắt đầu, giờ kết thúc.
- Giá khởi điểm và bước giá > 0; giờ kết thúc sau giờ bắt đầu.
- Chỉ sửa khi phiên còn `SCHEDULED`. Không xóa phiên đã có lịch sử.
- Tự mở và tự đóng theo lịch.

### 4.3. Danh sách và phòng đấu giá

- Danh sách phiên, lọc theo `SCHEDULED` / `ACTIVE` / `FINISHED`.
- Phòng hiển thị: mô tả, giá khởi điểm, bước giá, giá hiện tại, người dẫn đầu, đồng hồ đếm ngược, lịch sử gần đây.
- Đặt giá, nhận kết quả chấp nhận hoặc từ chối kèm lý do; cập nhật trực tiếp khi có bid hợp lệ hoặc phiên kết thúc.
- Trước bid đầu tiên, giao diện phân biệt "Giá khởi điểm" với "Giá đã được đặt".

### 4.4. Gia hạn chống bắn tỉa (anti-sniping)

- Nếu có bid hợp lệ khi còn **≤ 10 giây**, cộng thêm **30 giây** vào `endsAt`.
- Tham số cấu hình được. Có thể giới hạn tối đa số lần gia hạn (ví dụ 10) để phiên không kéo dài vô hạn.
- Mỗi lần gia hạn tăng `version` và phát sự kiện cho mọi client.

### 4.5. Lịch sử và kết quả

- Lưu người đặt, giá, thời gian server chấp nhận.
- Lịch sử phân trang, tối đa 50 lượt/trang.
- Xem phiên đã tham gia và đã thắng.
- Phiên không có bid hợp lệ: hiển thị "Chưa có người thắng".

### 4.6. Mất kết nối

- Hiển thị trạng thái kết nối; khóa nút đặt giá khi mất mạng.
- Cho phép kết nối lại, đăng nhập lại, vào lại phòng và nhận snapshot mới nhất.
- Chưa nhận phản hồi thì hiển thị "Chưa xác định kết quả", không tự kết luận thất bại.

### 4.7. Tìm server bằng UDP

- Client gửi broadcast "ai là server?"; server trả lời IP và cổng TCP.
- Luôn có ô nhập IP thủ công, vì Wi-Fi công cộng/mạng trường có thể chặn broadcast.

### 4.8. Báo cáo kết thúc phiên bằng RMI

- Khi phiên chốt, server gọi `SettlementService.recordResult(...)` để lập biên bản và thống kê.
- Nếu RMI lỗi, kết quả đấu giá **vẫn hợp lệ**; tác vụ được xếp hàng và gửi lại sau.

## 5. Quy tắc nghiệp vụ

### 5.1. Giá hợp lệ

- Bid đầu tiên: `giá đặt >= giá khởi điểm`.
- Các bid sau: `giá đặt >= giá hiện tại + bước giá`.
- Bước giá là mức tăng tối thiểu, không bắt buộc bội số. Ví dụ hiện tại 260.000đ, bước 20.000đ: 280.000đ và 285.000đ hợp lệ, 270.000đ không.
- Dùng `long` / `INTEGER`, không dùng `double`. Giới hạn mỗi mức giá tối đa 1.000.000.000.000đ và kiểm tra tràn số khi cộng.
- Người đang dẫn đầu vẫn được tăng giá. `ADMIN` không đặt giá.

### 5.2. Thời gian và trạng thái

```text
SCHEDULED ── đến startsAt ──> ACTIVE ── đến endsAt ──> FINISHED
```

- Server là nguồn thời gian. Client chỉ đếm ngược ước tính.
- Bid hợp lệ về thời gian khi `startsAt <= giờ server xử lý < endsAt`.
- Mỗi lần xử lý bid đều kiểm tra mốc thời gian, không phụ thuộc scheduler đã chạy hay chưa.
- Lưu thời gian dạng epoch milliseconds UTC.
- Một phiên chỉ chốt kết quả một lần.

## 6. Thiết kế dữ liệu

### 6.1. `users`

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | INTEGER PK | |
| `username` | TEXT UNIQUE | Chữ thường |
| `display_name` | TEXT | |
| `password_hash`, `password_salt`, `password_iterations` | TEXT/TEXT/INTEGER | PBKDF2 với salt riêng |
| `role` | TEXT | `USER` / `ADMIN` |
| `created_at` | INTEGER | |

### 6.2. `auctions`

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | INTEGER PK | |
| `title`, `description` | TEXT | |
| `starting_price`, `min_increment` | INTEGER | |
| `current_price` | INTEGER | Khởi tạo bằng giá khởi điểm |
| `current_bidder_id` | INTEGER NULL | NULL nếu chưa có bid |
| `winner_id` | INTEGER NULL | Chỉ gán khi chốt |
| `starts_at`, `ends_at` | INTEGER | UTC ms |
| `extension_count` | INTEGER | Số lần đã gia hạn |
| `status` | TEXT | `SCHEDULED` / `ACTIVE` / `FINISHED` |
| `version` | INTEGER | Tăng sau mỗi thay đổi |
| `created_by`, `created_at` | INTEGER | |

### 6.3. `bids`

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | INTEGER PK | |
| `auction_id`, `user_id` | INTEGER FK | `user_id` lấy từ session, không tin client |
| `amount` | INTEGER | |
| `accepted_at` | INTEGER | Giờ server chấp nhận |
| `request_id` | TEXT | Mã yêu cầu |

### 6.4. `settlement_queue`

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | INTEGER PK | |
| `auction_id` | INTEGER UNIQUE | Mỗi phiên một bản |
| `status` | TEXT | `PENDING` / `DONE` |
| `attempts`, `last_attempt_at` | INTEGER | Cho retry |

Chỉ mục: `bids(auction_id, id)`, `bids(user_id, auction_id)`. Bật `PRAGMA foreign_keys = ON`. Không xóa cứng dữ liệu đã được tham chiếu.

## 7. Giao thức TCP (desktop client)

### 7.1. Đóng khung

TCP là luồng byte, không giữ ranh giới thông điệp.

- UTF-8, mỗi thông điệp là một JSON gọn trên một dòng, kết thúc bằng `\n`.
- Tối đa 64 KiB mỗi thông điệp. Giới hạn bộ đệm ngay khi đọc, không dùng `readLine()` không giới hạn.
- Bộ đọc xử lý được thông điệp bị chia nhỏ và nhiều thông điệp tới cùng lúc.

### 7.2. Dạng thông điệp

Yêu cầu:

```json
{"protocolVersion":1,"kind":"REQUEST","type":"PLACE_BID","requestId":"2e8d7fd0-6ea3-4dd9-a940-f5847fa2e83c","payload":{"auctionId":1,"amount":280000}}
```

Phản hồi thành công hoặc bị từ chối (hai kết quả thay thế nhau):

```json
{"protocolVersion":1,"kind":"RESPONSE","type":"PLACE_BID","requestId":"2e8d7fd0-...","success":true,"payload":{"bidId":15,"acceptedAmount":280000,"version":8}}
{"protocolVersion":1,"kind":"RESPONSE","type":"PLACE_BID","requestId":"2e8d7fd0-...","success":false,"error":{"code":"BID_TOO_LOW","message":"Giá tối thiểu hiện tại là 300000đ","details":{"minimumAllowed":300000}}}
```

Sự kiện:

```json
{"protocolVersion":1,"kind":"EVENT","type":"AUCTION_UPDATED","payload":{"auctionId":1,"version":8,"status":"ACTIVE","currentPrice":280000,"leaderDisplayName":"Người dùng A","serverTime":1793080800000,"endsAt":1793081100000}}
```

### 7.3. Các yêu cầu

| Loại | Dữ liệu | Quyền |
|---|---|---|
| `REGISTER`, `LOGIN` | username, password (+ displayName) | Chưa đăng nhập |
| `LOGOUT` | — | Đã đăng nhập |
| `LIST_AUCTIONS` | status, phân trang | Đã đăng nhập |
| `GET_AUCTION` | auctionId | Đã đăng nhập |
| `JOIN_AUCTION` / `LEAVE_AUCTION` | auctionId | Đã đăng nhập; join trả snapshot đầy đủ |
| `PLACE_BID` | auctionId, amount | `USER`, đang trong phòng |
| `GET_BID_HISTORY` | auctionId, beforeBidId, limit | Đã đăng nhập |
| `GET_MY_AUCTIONS` | lọc đã tham gia/đã thắng | Đã đăng nhập |
| `CREATE_AUCTION`, `UPDATE_AUCTION` | thông tin phiên (+ expectedVersion) | `ADMIN` |
| `PING` | — | Trả `serverTime` |

Sự kiện: `AUCTION_STARTED`, `AUCTION_UPDATED`, `AUCTION_EXTENDED`, `AUCTION_FINISHED`.

### 7.4. Mã lỗi

`INVALID_MESSAGE`, `UNSUPPORTED_VERSION`, `UNAUTHORIZED`, `FORBIDDEN`, `VALIDATION_ERROR`, `USERNAME_TAKEN`, `INVALID_CREDENTIALS`, `AUCTION_NOT_FOUND`, `AUCTION_NOT_STARTED`, `AUCTION_FINISHED`, `NOT_IN_ROOM`, `BID_TOO_LOW`, `VERSION_CONFLICT`, `SERVER_BUSY`, `INTERNAL_ERROR`.

Danh tính người đặt luôn lấy từ session, không tin `userId` hay `role` do client gửi.

### 7.5. Chống gửi trùng (bản đơn giản)

- Mỗi yêu cầu có `requestId` (UUID).
- Server giữ cache trong bộ nhớ các `requestId` đã xử lý thành công gần đây (theo tài khoản). Gửi lại cùng mã thì trả biên nhận cũ, không thêm bid.
- Bản đầu không bảo đảm chống trùng bền vững qua restart server; ghi rõ đây là hạn chế đã biết.

## 8. Xử lý đồng thời

- Mỗi kết nối có tác vụ **đọc** riêng.
- Mọi thao tác nghiệp vụ và truy cập DB đi qua **một `single-thread executor`**. Đây là nơi quyết định thứ tự xử lý.
- Scheduler chỉ đưa tác vụ vào executor, không tự sửa dữ liệu.
- Đăng ký phòng, lấy snapshot, đăng xuất cũng đi qua executor để không tranh chấp với sự kiện.
- Băm mật khẩu chạy ở worker riêng có giới hạn để không chặn executor.
- Mỗi client có **hàng đợi gửi có giới hạn** và một tác vụ ghi duy nhất. Executor chỉ đẩy vào hàng đợi, không chờ ghi socket. Hàng đợi đầy thì ngắt client chậm.
- REST và WebSocket gọi `AuctionService` bằng cách gửi tác vụ vào cùng executor; không có đường ghi nào đi vòng.

Luồng đặt giá:

```text
Nhận PLACE_BID, kiểm tra định dạng
  -> đưa vào hàng đợi nghiệp vụ
  -> xác thực session, quyền, kiểm tra requestId trùng
  -> lấy giờ server; kiểm tra phòng, thời gian, mức giá
  -> mở transaction: thêm bid + cập nhật giá/người dẫn đầu/version
  -> (nếu trong 10 giây cuối) gia hạn endsAt, tăng extension_count
  -> commit
  -> gửi biên nhận cho người đặt
  -> đẩy sự kiện vào hàng đợi gửi của mọi client trong phòng
```

Ví dụ A và B cùng đặt 280.000đ khi giá hiện tại 260.000đ, bước 20.000đ: tác vụ vào trước được chấp nhận; tác vụ sau thấy giá đã là 280.000đ và bị từ chối với mức tối thiểu mới 300.000đ.

Chỉ báo thành công và phát sự kiện **sau khi commit**. Nếu giữ bản sao trạng thái trong bộ nhớ, chỉ cập nhật sau commit.

### 8.1. Đồng bộ giao diện

- Client bỏ qua cập nhật có `version` nhỏ hơn hoặc bằng bản đang hiển thị.
- Biên nhận đặt giá không được ghi đè trạng thái phòng mới hơn.
- Đồng hồ client đếm lùi theo `endsAt` và `serverTime`, tự đồng bộ lại khi có sự kiện.

### 8.2. Khởi động lại server

1. Mở DB, chạy migration.
2. Chốt các phiên đã quá hạn trước khi nhận yêu cầu mới.
3. Giữ nguyên `endsAt` đã lưu, không tự gia hạn vì server tắt.
4. Nạp lại `settlement_queue` các bản `PENDING`.
5. Khởi động scheduler, mở cổng TCP và HTTP.

## 9. UDP

### 9.1. Tìm server (broadcast)

- Server lắng nghe UDP cổng `5001`.
- Client gửi gói `LIVEBID_DISCOVER` tới địa chỉ broadcast (`DatagramSocket.setBroadcast(true)`), thử lại vài lần có timeout.
- Server trả gói JSON: `{ "name":"LiveBid", "tcpPort":5000, "httpPort":8080 }`. Client lấy IP từ gói nhận được.
- Nếu không tìm thấy, giao diện cho nhập IP thủ công.

### 9.2. Thông báo sắp bắt đầu (multicast)

- Nhóm multicast `239.255.42.99`, cổng `5002` (có thể đổi).
- Khi một phiên còn 5 phút nữa bắt đầu, server gửi `{ "auctionId":.., "title":.., "startsAt":.. }`.
- Client chỉ hiển thị thông báo. **Không** dùng UDP cho bất cứ điều gì ảnh hưởng đến đúng/sai của bid.
- Mất gói là chấp nhận được; dữ liệu chuẩn luôn lấy bằng TCP/REST.

Lưu ý Windows: có thể phải mở UDP 5001, 5002 trong firewall (hồ sơ Private).

## 10. REST (web service)

Cổng HTTP `8080`, JSON. Xác thực bằng token trong header `Authorization: Bearer ...`.

| Phương thức | Đường dẫn | Chức năng | Quyền |
|---|---|---|---|
| POST | `/api/register`, `/api/login` | Đăng ký, đăng nhập (nhận token) | Công khai |
| GET | `/api/auctions?status=ACTIVE&page=1` | Danh sách phiên | Đã đăng nhập |
| GET | `/api/auctions/{id}` | Chi tiết phiên | Đã đăng nhập |
| GET | `/api/auctions/{id}/bids?before=..&limit=..` | Lịch sử bid | Đã đăng nhập |
| GET | `/api/me/auctions?filter=won` | Phiên của tôi | Đã đăng nhập |
| GET | `/api/stats` | Thống kê (số phiên, tổng giá trị, top người thắng) | Đã đăng nhập |
| POST | `/api/auctions` | Tạo phiên | `ADMIN` |
| PUT | `/api/auctions/{id}` | Sửa phiên chưa bắt đầu | `ADMIN` |

Mã HTTP: 200/201 thành công, 400 dữ liệu sai, 401 chưa đăng nhập, 403 không đủ quyền, 404 không thấy, 409 xung đột phiên bản.

Việc đặt giá thực hiện qua TCP hoặc WebSocket. REST dùng để tra cứu và quản trị, giúp dễ giải thích sự khác nhau giữa request/response và kênh hai chiều.

## 11. WebSocket (trình duyệt)

Đường dẫn `ws://<server>:8080/ws`. Dùng cùng định dạng JSON như TCP, khác ở chỗ mỗi frame WebSocket là một thông điệp nên **không cần tự đóng khung**.

- Kết nối xong, client gửi `AUTH` kèm token (lấy từ REST `/api/login`), rồi `JOIN_AUCTION`, `PLACE_BID`, `LEAVE_AUCTION` như ở mục 7.
- Server đẩy xuống các sự kiện giống TCP.
- Gateway chỉ chuyển yêu cầu vào `AuctionService` và đăng ký `RoomManager`, không tự xử lý nghiệp vụ.
- Trang web: danh sách phiên, phòng đấu giá, đồng hồ đếm ngược, lịch sử bid, thông báo khi bị vượt giá.

Điểm giải thích khi bảo vệ: WebSocket chạy trên TCP, bắt đầu bằng HTTP upgrade, sau đó là kênh hai chiều để server chủ động đẩy dữ liệu.

## 12. RMI (Settlement)

Interface dùng chung ở module `common`:

```java
public interface SettlementService extends Remote {
    void recordResult(AuctionResultDto result) throws RemoteException;
    SettlementSummaryDto getSummary() throws RemoteException;
}
```

- `settlement` chạy như một tiến trình riêng, đăng ký vào RMI Registry cổng `1099`.
- Khi chốt phiên, `AuctionService` ghi vào `settlement_queue`, sau đó `SettlementClient` gọi `recordResult`. Thành công thì đánh dấu `DONE`; lỗi thì tăng `attempts` và thử lại định kỳ.
- Settlement lập biên bản (file văn bản/CSV) và cộng dồn thống kê; `getSummary` dùng cho REST `/api/stats` hoặc màn hình admin.
- Khi demo trên nhiều máy, đặt `java.rmi.server.hostname` đúng IP LAN của máy chạy Settlement và mở cổng RMI trong firewall.

Vì sao RMI hợp ở đây: việc này **không nằm trên đường găng của bid**. Nếu Settlement sập, đấu giá vẫn chạy và kết quả vẫn hợp lệ. Đây cũng là lý do không đặt ví/giữ tiền qua RMI ở bản đầu: giữ tiền ở một nơi rồi commit ở nơi khác là bài toán giao dịch phân tán, dễ sai nhất quán.

## 13. Yêu cầu phi chức năng và bảo mật

| Nội dung | Mục tiêu bản đầu |
|---|---|
| Quy mô | 20 client, 5 phiên hoạt động trong LAN |
| Cập nhật trực tiếp | Phần lớn dưới 1 giây ở tải demo; cần đo và ghi lại |
| Đúng dữ liệu | Không nhận giá sai bước, sau hạn, hoặc ghi trùng bid |
| Giao diện | Không đứng khi mạng chậm; luôn hiển thị lý do từ chối |
| Lưu trữ | Dữ liệu đã commit còn sau restart |
| Tiếng Việt | UTF-8 xuyên suốt |
| Cấu hình | IP, cổng, đường dẫn DB đọc từ file cấu hình |

Bảo mật tối thiểu:

- Mật khẩu lưu bằng PBKDF2 (ví dụ `PBKDF2WithHmacSHA256`), salt riêng, lưu tham số chi phí. Không lưu mật khẩu rõ, không dùng SHA-256 đơn lẻ.
- Dùng `PreparedStatement`. Kiểm tra độ dài và miền giá trị đầu vào.
- Không log mật khẩu.
- Giới hạn số kết nối, tốc độ gửi yêu cầu đăng nhập và đặt giá.
- TCP/WebSocket/HTTP ở bản đầu không mã hóa; chỉ dùng dữ liệu thử trong LAN tin cậy. Nếu có thời gian, thêm TLS (WSS và `SSLSocket`) như hướng mở rộng.

## 14. Giao diện

| Màn hình | Thành phần chính |
|---|---|
| Kết nối | Nút "Tìm server" (UDP), ô nhập IP thủ công, trạng thái |
| Đăng nhập/đăng ký | Tài khoản, mật khẩu, tên hiển thị |
| Danh sách phiên | Bộ lọc, bảng phiên, làm mới |
| Phòng đấu giá | Giá, người dẫn đầu, đồng hồ, ô đặt giá, lịch sử, thông báo |
| Lịch sử cá nhân | Phiên đã đặt, đã thắng |
| Quản trị | Tạo/sửa phiên, xem thống kê (từ RMI) |

Client chuyển giá nhập thành số nguyên trước khi gửi; server vẫn kiểm tra lại. Tác vụ mạng không chạy trên luồng giao diện (Swing: `SwingUtilities.invokeLater`; JavaFX: `Platform.runLater`).

## 15. Lộ trình 6 tuần

| Tuần | Công việc | Kết quả |
|---|---|---|
| 1 | Fork/clone `jnp-project-starter`, dựng cấu trúc `source/` + `statics/`, điền thông tin nhóm và chủ đề vào `README.md`; khung Maven, cấu hình, DTO, framing TCP, client dòng lệnh, SQLite, đăng ký/đăng nhập | Repo đúng cấu trúc; `PING` và đăng nhập chạy bằng client dòng lệnh |
| 2 | Nghiệp vụ đấu giá: phiên, vào/rời phòng, bid, timer, chốt người thắng, executor, test đồng thời | **Mốc quan trọng nhất:** luồng đấu giá đủ chạy bằng dòng lệnh |
| 3 | Desktop client (user + admin), UDP discovery, UDP multicast | Đã dùng TCP + UDP |
| 4 | REST + WebSocket gateway + trang web | Đã dùng 4 công nghệ |
| 5 | RMI Settlement + retry, anti-sniping, reconnect, sửa lỗi | Đủ cả 5 công nghệ |
| 6 | Test nhiều máy qua LAN, đo tải; hoàn thiện `README.md` (gốc + module) và hình trong `statics/`; một người ngoài nhóm làm theo README từ máy sạch; merge về `main` hoặc tạo release; slide, tập demo và vấn đáp | **Không thêm tính năng mới** |

### 15.1. Thứ tự cắt khi trễ

1. Cắt RMI Settlement (giữ phần ghi biên bản ngay trong server).
2. Cắt anti-sniping.
3. Cắt UDP multicast (giữ broadcast discovery).
4. Cắt trang web nếu vẫn kịp REST + WebSocket bằng công cụ test.

Không cắt: kiểm tra giá, kiểm tra thời gian, transaction, xử lý đồng thời.

### 15.2. Chia việc (ví dụ 3 người)

| Người | Phần việc |
|---|---|
| A | Lõi server (`source/server`, `source/common`): TCP, framing, executor, `AuctionService`, scheduler, test đồng thời (`source/tests`); `source/server/README.md` |
| B | CSDL, `AuthService`, REST, WebSocket gateway (`source/server`), trang web (`source/web`); phần REST/WebSocket trong `README.md` gốc |
| C | Desktop client (`source/client`), UDP, RMI Settlement (`source/settlement`), kịch bản demo; `source/client/README.md`, hình trong `statics/` |

`README.md` gốc là việc chung: mỗi người viết phần mô tả cho mảng mình phụ trách.

Làm một mình hoặc hai người: INSTRUCTION.md yêu cầu vẫn như nhóm 3 người, nên giữ đủ phần cốt lõi. Một mình: bỏ multicast, gộp gateway vào server, dùng Swing, ưu tiên TCP + UDP discovery + REST. Hai người: A giữ lõi và RMI, B giữ client, UDP, REST, web.

### 15.3. Quy tắc Git

- Một repo chung trên GitHub/GitLab, nhánh chính `main`; mỗi tính năng làm trên nhánh riêng (`feature/tcp-framing`, `feature/web-room`...) rồi merge.
- **Ai viết code thì người đó commit**, bằng tài khoản Git của chính mình (cấu hình `user.name`, `user.email` đúng). Không gom code của người khác để commit hộ: lịch sử commit là căn cứ chấm 20% điểm cá nhân.
- Commit nhỏ, thường xuyên, message rõ ràng, ví dụ `server: kiểm tra bước giá trong PLACE_BID`, `client: tự tìm server bằng UDP broadcast`.
- Không commit file build, DB, log, mật khẩu (đã chặn bằng `source/.gitignore`).
- Không ai sửa `INSTRUCTION.md`.

## 16. Kiểm thử

Dùng DB tạm và `Clock` thay thế được để kiểm tra mốc thời gian mà không phải chờ.

| Mã | Tình huống | Kết quả mong đợi |
|---|---|---|
| T01 | Đăng ký trùng tên, đăng nhập sai | Từ chối rõ lý do |
| T02 | `USER` gọi `CREATE_AUCTION` | `FORBIDDEN` |
| T03 | Bid trước giờ mở / đúng giờ mở | Trước bị từ chối; đúng giờ được xét giá |
| T04 | Bid thấp hơn mức tối thiểu hoặc dữ liệu sai | Từ chối, trạng thái không đổi |
| T05 | Hai client cùng đặt một giá hợp lệ (đồng bộ bằng `CountDownLatch`) | Chỉ một lượt được chấp nhận |
| T06 | 20+ luồng đặt giá ngẫu nhiên đồng thời | Chuỗi bid được chấp nhận luôn thỏa bước giá |
| T07 | Bid tại hoặc sau `endsAt` | Từ chối, kể cả scheduler chưa chạy |
| T08 | Bid trong 10 giây cuối | `endsAt` tăng đúng, phát `AUCTION_EXTENDED` |
| T09 | JSON bị chia nhiều lần đọc; nhiều JSON trong một lần đọc | Giải mã đúng từng thông điệp |
| T10 | JSON lỗi, thông điệp quá lớn | Từ chối hoặc ngắt kết nối, server vẫn chạy |
| T11 | Lỗi DB giữa thêm bid và cập nhật phiên | Rollback cả hai, không phát sự kiện thành công |
| T12 | Restart server khi phiên đang mở/đã quá hạn | Dữ liệu còn, chốt đúng kết quả |
| T13 | Tắt Settlement, chốt phiên, bật lại | Đấu giá bình thường; biên bản được gửi bù |
| T14 | Chặn broadcast hoặc không có server | Hiển thị lỗi, nhập IP thủ công vẫn dùng được |
| T15 | Trình duyệt và desktop cùng bid | Cả hai thấy cùng trạng thái |
| T16 | 20 client, 5 phiên trong LAN | Đo độ trễ, không sai giá hoặc người thắng |

## 17. Chạy thử trên Windows

Server:

```properties
server.host=0.0.0.0
server.tcpPort=5000
server.httpPort=8080
server.udpDiscoveryPort=5001
server.multicastGroup=239.255.42.99
server.multicastPort=5002
settlement.rmiHost=127.0.0.1
settlement.rmiPort=1099
database.path=./data/auction.db
network.maxMessageBytes=65536
network.heartbeatIntervalSeconds=15
network.idleTimeoutSeconds=45
```

- `0.0.0.0` chỉ dùng để bind; client không dùng địa chỉ này để kết nối.
- Cùng máy dùng `127.0.0.1`; khác máy dùng IPv4 LAN của server (lấy bằng `ipconfig`).
- Mở firewall cho mạng **Private** (PowerShell quyền admin), ví dụ:

```powershell
New-NetFirewallRule -DisplayName "LiveBid" -Direction Inbound -Protocol TCP -LocalPort 5000,8080,1099 -RemoteAddress LocalSubnet -Profile Private -Action Allow
New-NetFirewallRule -DisplayName "LiveBid UDP" -Direction Inbound -Protocol UDP -LocalPort 5001,5002 -RemoteAddress LocalSubnet -Profile Private -Action Allow
```

- Kiểm tra TCP từ máy khác: `Test-NetConnection -ComputerName <IP> -Port 5000`.
- Gỡ quy tắc sau demo: `Get-NetFirewallRule -DisplayName "LiveBid*" | Remove-NetFirewallRule`.

Lỗi thường gặp:

| Hiện tượng | Kiểm tra trước |
|---|---|
| `Connection refused` | Server đã chạy chưa, đúng IP/cổng/địa chỉ bind chưa |
| Timeout khi khác máy | Firewall, hồ sơ mạng, Wi-Fi cách ly thiết bị |
| UDP không tìm thấy server | Broadcast/multicast bị chặn; dùng nhập IP thủ công |
| RMI báo lỗi kết nối khi khác máy | `java.rmi.server.hostname`, cổng 1099 và cổng đối tượng |
| JSON lỗi khi tải cao | Đã xử lý ranh giới thông điệp và chỉ một tác vụ ghi mỗi socket chưa |
| Giá hiển thị bị lùi | Đã so sánh `version` chưa |
| Giao diện đứng | Có chờ mạng trên luồng giao diện không |

## 18. Tiêu chí nghiệm thu

Theo INSTRUCTION.md:

- [ ] Repo đúng cấu trúc: `README.md`, `INSTRUCTION.md` (nguyên bản), `statics/`, `source/` với `.gitignore`, `client/`, `server/`.
- [ ] `source/client/README.md`, `source/server/README.md` có nội dung.
- [ ] `README.md` gốc có thông tin nhóm, chủ đề, công nghệ sử dụng (kèm cài đặt), kiến trúc, giao thức, build/run, demo.
- [ ] Người ngoài nhóm build và chạy được chỉ bằng cách làm theo README.
- [ ] Lịch sử commit có đóng góp của tất cả thành viên.
- [ ] Code đã merge về `main` hoặc có release.

Chức năng:

- [ ] Build theo README trên Windows.
- [ ] Đăng ký, đăng nhập, phân quyền kiểm tra tại server.
- [ ] Admin tạo/sửa phiên hợp lệ.
- [ ] Desktop client (TCP) và trình duyệt (WebSocket) cùng vào một phòng, cùng nhận cập nhật.
- [ ] Kiểm tra đúng giá, bước giá, thời gian; hai bid đồng thời không làm sai trạng thái.
- [ ] Anti-sniping hoạt động và thông báo cho mọi client.
- [ ] Client tự tìm server bằng UDP; có phương án nhập IP thủ công.
- [ ] Nhận thông báo multicast trước giờ bắt đầu.
- [ ] REST trả đúng danh sách, lịch sử, thống kê, và từ chối đúng quyền.
- [ ] Settlement qua RMI nhận kết quả; sập Settlement không làm hỏng đấu giá.
- [ ] Dữ liệu còn sau restart; phiên quá hạn được chốt đúng.
- [ ] Demo thành công trên ít nhất hai máy qua LAN.
- [ ] Các ca T05–T08, T11–T13 đã chạy và có kết quả ghi lại.

## 19. Kịch bản demo (7–10 phút)

1. Chạy server và Settlement. Desktop client bấm "Tìm server" và kết nối tự động (UDP).
2. Admin tạo phiên bắt đầu sau 1 phút; mọi client nhận thông báo multicast.
3. Hai trình duyệt (WebSocket) và một desktop client (TCP) vào cùng phòng.
4. Bid đầu tiên bằng giá khởi điểm; các bên khác thấy giá đổi mà không làm mới.
5. Một người đặt giá quá thấp để minh họa kiểm tra nghiệp vụ.
6. Hai người đặt cùng mức giá gần như đồng thời: chỉ một người được chấp nhận.
7. Bid vào 10 giây cuối để thấy đồng hồ được gia hạn.
8. Hết giờ, mọi client thấy cùng người thắng; gọi `curl /api/auctions/1` xem kết quả qua REST.
9. Mở biên bản do Settlement (RMI) tạo.
10. (Tùy chọn) Tắt Settlement, chốt phiên khác để thấy hàng đợi retry, rồi bật lại.

## 20. Câu hỏi giảng viên hay hỏi

Vấn đáp diễn ra với **từng thành viên** về phần code họ phụ trách, nên mỗi người cần trả lời được các câu liên quan đến mảng mình và mở đúng đoạn code tương ứng.

- **Hai người đặt cùng lúc thì sao?** Mọi bid đi qua một executor đơn luồng; bid đến sau được kiểm tra lại với giá mới. Có test nhiều luồng chứng minh.
- **Vì sao WebSocket mà không dùng REST cho bid?** Server cần chủ động đẩy cập nhật; polling chậm và tốn tài nguyên.
- **Vì sao vẫn có TCP thô?** Tự thiết kế giao thức, thể hiện hiểu về luồng byte và framing; WebSocket đã đóng khung sẵn nên không thấy được phần này.
- **Vì sao UDP cho thông báo và tìm server?** Một-nhiều, nhẹ, không ảnh hưởng tính đúng; dữ liệu chuẩn vẫn lấy qua TCP.
- **Vì sao RMI cho Settlement?** Việc ngoài đường găng, lỗi thì retry; không dùng cho quyết định chấp nhận bid.
- **Server sập giữa chừng?** Dữ liệu đã commit còn nguyên; restart chốt phiên quá hạn; client vào lại nhận snapshot.
- **Client mất mạng sau khi gửi bid?** Hiển thị "chưa xác định kết quả"; vào lại phòng và kiểm tra lịch sử/biên nhận.

## 21. Hướng mở rộng

| Mở rộng | Kiến thức | Lưu ý |
|---|---|---|
| TLS cho TCP và WebSocket | `SSLSocket`, WSS | Cần chứng chỉ tự ký khi demo |
| Chat trong phòng | Sự kiện TCP/WebSocket thêm | Giới hạn độ dài và tốc độ |
| Ví mô phỏng | Ledger, giao dịch | Chỉ làm nếu giải quyết được nhất quán |
| Bid tự động | Quy tắc nghiệp vụ mới | Xử lý trong cùng executor |
| Docker Compose | Chạy server/settlement riêng | Multicast/broadcast có thể không đi qua mạng Docker |
| Ảnh sản phẩm | Truyền file qua TCP/HTTP | Không gửi đường dẫn cục bộ |

Chỉ chọn một hoặc hai mục sau khi các nội dung chính đã chạy ổn định.

## 22. Hồ sơ bàn giao

| Nội dung | Vị trí |
|---|---|
| Mã nguồn các module `common`, `server`, `settlement`, `client`, `web`, `tests` | `source/` |
| Migration CSDL | `source/server/` |
| Cấu hình mẫu | `source/config/` |
| Dữ liệu demo (không chứa thông tin thật) | `statics/dataset_sample.csv` |
| Sơ đồ kiến trúc, mô hình dữ liệu, luồng đặt giá, ảnh chụp giao diện | `statics/` |
| Mô tả hệ thống, giao thức, build/run, kết quả kiểm thử, hạn chế | `README.md` gốc |
| Hướng dẫn riêng từng module | `source/*/README.md` |
| Slide, kịch bản demo | Theo yêu cầu nộp trên LMS (không bắt buộc trong repo) |

### 22.1. Dàn ý `README.md` gốc

1. Tên dự án, logo (`statics/logo.png` nếu có).
2. Thông tin nhóm: họ tên, mã sinh viên, vai trò từng thành viên; chủ đề đã đăng ký.
3. Giới thiệu và chức năng chính.
4. Công nghệ sử dụng: Java 21, Maven, Javalin, Gson, SQLite JDBC, JUnit, Swing/JavaFX (kèm phiên bản và cách cài).
5. Kiến trúc: sơ đồ trong `statics/`, vai trò từng giao thức và lý do chọn (bảng mục 1.1).
6. Giao tiếp: định dạng thông điệp TCP/WebSocket, danh sách yêu cầu và mã lỗi, endpoint REST, interface RMI, gói UDP.
7. Cấu trúc thư mục.
8. Cài đặt, build, chạy: cấu hình, tạo admin, thứ tự khởi động (Settlement → Server → client/web), mở firewall.
9. Kịch bản demo.
10. Kiểm thử: kết quả các ca T01–T16, số liệu đo tải.
11. Hạn chế và hướng phát triển.

## 23. Tài liệu tham khảo

- [Java ServerSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/ServerSocket.html) và [Socket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/Socket.html)
- [DatagramSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/DatagramSocket.html) và [MulticastSocket](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/MulticastSocket.html)
- [Java RMI](https://docs.oracle.com/en/java/javase/21/docs/api/java.rmi/java/rmi/package-summary.html)
- [Javalin](https://javalin.io/documentation) (REST và WebSocket)
- [SQLite Transaction](https://www.sqlite.org/lang_transaction.html)
- [Microsoft New-NetFirewallRule](https://learn.microsoft.com/en-us/powershell/module/netsecurity/new-netfirewallrule) và [Test-NetConnection](https://learn.microsoft.com/en-us/powershell/module/nettcpip/test-netconnection)
