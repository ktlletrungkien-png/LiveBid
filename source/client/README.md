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
└── src/main/java/com/livebid/client/
    └── ClientMain.java
```

## 📝 GHI CHÚ

- Server phải chạy trước khi client kết nối.
- Nếu mạng chặn broadcast (Wi-Fi công cộng, mạng trường), dùng ô nhập IP thủ công.
