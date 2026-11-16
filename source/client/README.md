# MODULE CLIENT

## 🎯 MỤC TIÊU

Desktop client (Swing) cho người dùng và quản trị viên:

- Tự tìm server trong LAN bằng UDP broadcast, hoặc nhập IP thủ công.
- Kết nối TCP tới server, đăng nhập/đăng ký, xem danh sách phiên.
- Vào phòng đấu giá, đặt giá, nhận cập nhật real-time, đồng hồ đếm ngược.
- Nhận thông báo multicast "phiên sắp bắt đầu".
- Màn hình quản trị: tạo/sửa phiên, xem thống kê.

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
    │   │   └── UdpDiscoveryClient.java# tìm server bằng UDP broadcast
    │   └── ui/
    │       ├── MainFrame.java         # CardLayout + thanh trạng thái
    │       ├── ConnectPanel.java      # "Tìm server" / nhập IP / "Kết nối"
    │       └── LoginPanel.java        # tab Đăng nhập / Đăng ký
    └── test/java/com/livebid/client/net/
        ├── ServerConnectionTest.java  # 6 tình huống, ServerSocket(0) giả
        └── UdpDiscoveryClientTest.java# responder giả trên loopback
```

## 🖥️ MÀN HÌNH HIỆN CÓ (M4)

| Màn hình | Việc làm được |
|---|---|
| Kết nối | Tìm server bằng UDP broadcast (chạy nền), hoặc nhập IP/cổng thủ công; báo lỗi tiếng Việt khi không kết nối được |
| Đăng nhập | Tab Đăng nhập / Đăng ký qua TCP; lỗi nghiệp vụ hiện nguyên câu của server |
| Sau đăng nhập | Màn hình tạm "Đã đăng nhập: &lt;tên&gt;" — danh sách phiên và phòng đấu giá thuộc M5 |

Thanh trạng thái dưới cùng luôn hiện trạng thái kết nối và người dùng. Mất kết nối thì tự quay về màn hình Kết nối.
Mọi việc mạng chạy ngoài EDT (`SwingWorker` / callback của `CompletableFuture`), cập nhật giao diện qua `SwingUtilities.invokeLater`.

## 🧪 KIỂM THỬ

```bash
cd source
mvn -o -pl client test        # ServerConnectionTest + UdpDiscoveryClientTest
```

Test dùng cổng ngẫu nhiên trên loopback, không cần server thật, không cần mạng ngoài.

## 📝 GHI CHÚ

- Server phải chạy trước khi client kết nối.
- Nếu mạng chặn broadcast (Wi-Fi công cộng, mạng trường), dùng ô nhập IP thủ công.
- `ServerConnection` không phụ thuộc Swing: listener chạy trên luồng mạng, tầng giao diện tự chuyển về EDT.
- Mất kết nối: mọi yêu cầu đang chờ lỗi `IOException("Mất kết nối tới server")`, trạng thái chuyển `DISCONNECTED`.

