# MODULE CLIENT

## 🎯 MỤC TIÊU

Desktop client (Swing) cho người dùng và quản trị viên:

- Tự tìm server trong LAN bằng UDP broadcast, hoặc nhập IP thủ công.
- Kết nối TCP tới server, đăng nhập/đăng ký, xem danh sách phiên.
- Xem “Phiên của tôi”: các phiên đã tham gia hoặc đã thắng.
- Vào phòng đấu giá, đặt giá, nhận cập nhật real-time, đồng hồ đếm ngược.
- Nhận thông báo multicast "phiên sắp bắt đầu".
- Màn hình quản trị: tạo/sửa phiên chưa bắt đầu.
- Mất kết nối thì tự thử lại, đăng nhập lại và vào lại phòng đang mở.

## ⚙️ CÔNG NGHỆ SỬ DỤNG

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 |
| Giao diện | Swing (JDK) |
| Giao thức | TCP (JSON một dòng), UDP broadcast, UDP multicast |
| JSON | Gson |

## 🚀 HƯỚNG DẪN CHẠY

```bash
cd source
mvn -q install -DskipTests          # nếu chưa build
mvn -q -pl client exec:java
```

Cấu hình: sao chép `source/config/client.properties.example` thành `source/config/client.properties`. Để trống `server.host` để tự tìm server.

## 📦 CẤU TRÚC

```
client/
├── README.md
├── pom.xml
└── src/
    ├── main/java/com/livebid/client/
    │   ├── ClientMain.java            # điểm khởi động, look-and-feel hệ thống
    │   ├── ClientConfig.java          # đọc config/client.properties
    │   ├── net/
    │   │   ├── ConnectionState.java   # CONNECTING / CONNECTED / DISCONNECTED
    │   │   ├── RemoteErrorException.java # RESPONSE success=false (mang ErrorInfo)
    │   │   ├── ServerConnection.java  # TCP: ghép RESPONSE theo requestId, EVENT, PING
    │   │   ├── UdpDiscoveryClient.java# tìm server bằng UDP broadcast
    │   │   ├── MulticastListener.java # nhận "phiên sắp bắt đầu" qua multicast
    │   │   └── ReconnectPolicy.java   # chờ 1, 2, 4, 8 rồi 10 giây, tối đa 8 lần
    │   ├── room/
    │   │   ├── MoneyFormat.java       # đọc/định dạng tiền VND
    │   │   ├── RoomState.java         # trạng thái phòng, version, đếm ngược theo giờ server
    │   │   └── AuctionForm.java       # kiểm tra form tạo/sửa phiên → AuctionDraft
    │   └── ui/
    │       ├── MainFrame.java         # CardLayout + thanh trạng thái + phân phối sự kiện
    │       ├── ConnectPanel.java      # "Tìm server" / nhập IP / "Kết nối"
    │       ├── LoginPanel.java        # tab Đăng nhập / Đăng ký
    │       ├── AuctionHubPanel.java   # tab "Tất cả phiên" + "Phiên của tôi"
    │       ├── AuctionListPanel.java  # bảng phiên, lọc, phân trang, mở phòng, nút ADMIN
    │       ├── AuctionTableModel.java # model bảng dùng chung
    │       ├── MyAuctionsPanel.java   # phiên đã tham gia / đã thắng
    │       ├── AuctionFormDialog.java # form tạo/sửa phiên (JSpinner ngày giờ)
    │       ├── RoomPanel.java         # phòng đấu giá, đếm ngược, đặt giá, lịch sử
    │       └── TimeFormat.java        # định dạng ngày giờ và đếm ngược
    └── test/java/com/livebid/client/
        ├── net/
        │   ├── ServerConnectionTest.java  # 8 tình huống, ServerSocket(0) giả
        │   ├── UdpDiscoveryClientTest.java# responder giả trên loopback
        │   ├── MulticastListenerTest.java # socket loopback, gửi unicast thay multicast
        │   └── ReconnectPolicyTest.java   # dãy thời gian chờ, dừng sau 8 lần
        └── room/
            ├── MoneyFormatTest.java   # các dạng nhập tiền, tràn số
            ├── RoomStateTest.java     # version, offset giờ server, giá tối thiểu
            └── AuctionFormTest.java   # tên/mô tả/giá/thời gian của form
```

## 🖥️ MÀN HÌNH HIỆN CÓ (M4–M6)

| Màn hình | Việc làm được |
|---|---|
| Kết nối | Tìm server bằng UDP broadcast (chạy nền), hoặc nhập IP/cổng thủ công; báo lỗi tiếng Việt khi không kết nối được |
| Đăng nhập | Tab Đăng nhập / Đăng ký qua TCP; lỗi nghiệp vụ hiện nguyên câu của server |
| Tất cả phiên | Bảng tên/giá hiện tại/trạng thái/bắt đầu/kết thúc; lọc trạng thái; làm mới; phân trang 20 phiên; bấm đúp để vào phòng |
| Phiên của tôi | Lọc “Đã tham gia” / “Đã thắng” qua `GET_MY_AUCTIONS`; bấm đúp để vào phòng |
| Quản trị (ADMIN) | Nút “Tạo phiên” và “Sửa phiên” trên tab danh sách; form nhập tên, mô tả, giá, bước giá và ngày giờ; sửa chỉ bật với phiên Sắp bắt đầu |
| Phòng đấu giá | Giá khởi điểm hoặc giá đã đặt, bước giá, người dẫn đầu, người thắng; đồng hồ đếm ngược theo giờ server; lịch sử 20 bid mới nhất; ô đặt giá (ADMIN không thấy) |
| Thanh trạng thái | Trạng thái kết nối, người dùng, nút “Kết nối lại”, thông báo multicast "phiên «…» sắp bắt đầu lúc HH:mm" (tự ẩn sau 5 giây) |

Trong phòng, sự kiện `AUCTION_UPDATED` / `AUCTION_EXTENDED` / `AUCTION_STARTED` / `AUCTION_FINISHED` cập nhật ngay
giá, người dẫn đầu, đồng hồ và lịch sử. Bị người khác vượt giá thì hiện "Bạn đã bị vượt giá". Đặt giá thấp hơn
mức tối thiểu thì server trả `BID_TOO_LOW` kèm `details.minimumAllowed`, client điền lại đúng số đó. Timeout
hoặc mất kết nối khi đặt giá hiện "Chưa xác định kết quả" và có nút "Tải lại lịch sử" để kiểm tra.

Mất kết nối khi đã đăng nhập thì giữ nguyên phiên và tự thử lại: chờ 1, 2, 4, 8 rồi 10 giây, tối đa 8 lần
(`ReconnectPolicy`), có nút “Kết nối lại” trên thanh trạng thái để thử ngay. Kết nối TCP xong thì `LOGIN` lại
bằng thông tin chỉ giữ trong bộ nhớ (`char[]`, xoá khi thoát), đang mở phòng thì `JOIN_AUCTION` lại để lấy
snapshot mới, đang ở danh sách thì tải lại tab. Chưa đăng nhập (hoặc đã dùng hết lượt thử) thì quay về màn hình
Kết nối như trước.

ADMIN sửa phiên bằng `UPDATE_AUCTION` kèm `version` đang hiển thị; nếu người khác đã sửa trước, server trả
`VERSION_CONFLICT` và client báo “Phiên đã bị thay đổi, hãy tải lại rồi sửa” rồi tải lại danh sách.

Mọi việc mạng chạy ngoài EDT (`SwingWorker` / callback của `CompletableFuture`), cập nhật giao diện qua `SwingUtilities.invokeLater`.

## 🧪 KIỂM THỬ

```bash
cd source
mvn -o -pl client test        # 42 test: ServerConnection, UdpDiscovery, MulticastListener, ReconnectPolicy, MoneyFormat, RoomState, AuctionForm
```

Test dùng cổng ngẫu nhiên trên loopback, không cần server thật, không cần mạng ngoài, độc lập thứ tự.

Đã chạy thật với server ở `127.0.0.1:5000` (17/11/2026): đăng nhập ADMIN, `CREATE_AUCTION`, `LIST_AUCTIONS`
(`PageResult<AuctionView>`), `JOIN_AUCTION` (`RoomSnapshot`), hai người `PLACE_BID`, nhận `AUCTION_UPDATED` với
giá mới, `GET_BID_HISTORY` (`List<BidView>` mới nhất trước), `BID_TOO_LOW` kèm `minimumAllowed`, `LEAVE_AUCTION`.

Lượt M6 cũng chạy thật cùng ngày: `CREATE_AUCTION` ra phiên Sắp bắt đầu, `UPDATE_AUCTION` (version 1 → 2, đổi
tên và giá), gửi lại `UPDATE_AUCTION` với version cũ nhận `VERSION_CONFLICT`, đăng ký người dùng mới, `JOIN_AUCTION`,
`PLACE_BID`, `GET_MY_AUCTIONS` (Đã tham gia có phiên vừa đặt, Đã thắng rỗng), rồi đóng kết nối, mở kết nối mới,
`LOGIN` và `JOIN_AUCTION` lại vẫn thấy bid cũ.

## 📝 GHI CHÚ

- Server phải chạy trước khi client kết nối.
- Nếu mạng chặn broadcast (Wi-Fi công cộng, mạng trường), dùng ô nhập IP thủ công.
- `ServerConnection` không phụ thuộc Swing: listener chạy trên luồng mạng, tầng giao diện tự chuyển về EDT.
- Mất kết nối: mọi yêu cầu đang chờ lỗi `IOException("Mất kết nối tới server")`, trạng thái chuyển `DISCONNECTED`.
- Multicast chỉ để hiển thị: mất gói không ảnh hưởng dữ liệu, vì trạng thái chuẩn luôn lấy lại qua TCP.
- `RoomState` bỏ qua bản có `version` nhỏ hơn hoặc bằng bản đang giữ, nên sự kiện về muộn không ghi đè trạng thái mới.
- `GET_MY_AUCTIONS` trả `List<AuctionView>` (không phân trang) và bắt buộc có filter, nên `MyAuctionsPanel` đọc
  bằng `TypeToken`; bộ lọc Đã tham gia / Đã thắng đổi là tải lại ngay.
- `AuctionView` không có thông tin người tạo: nút “Sửa phiên” dựa vào trạng thái `SCHEDULED` và `version` đang
  hiển thị, còn quyền ADMIN do server kiểm tra lại.


