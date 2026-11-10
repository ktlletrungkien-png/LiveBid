# MODULE SETTLEMENT

Dịch vụ RMI chạy như một tiến trình riêng. Khi một phiên đấu giá kết thúc, server gọi
`SettlementService.recordResult(...)` để lập biên bản và cộng dồn thống kê; `getSummary()`
cung cấp số liệu cho `/api/stats` và màn hình quản trị.

Settlement **không** nằm trên đường găng của việc đặt giá: nếu dịch vụ này tắt, đấu giá vẫn chạy
và kết quả vẫn hợp lệ; server sẽ gửi bù khi Settlement chạy lại.

## 📦 CẤU TRÚC

```
settlement/src/main/java/com/livebid/settlement/
├── SettlementMain.java          # export RMI, tạo/dùng lại Registry, giữ tiến trình sống
└── SettlementServiceImpl.java   # ghi biên bản + cộng dồn thống kê (idempotent theo auctionId)

settlement/src/test/java/com/livebid/settlement/
├── SettlementServiceImplTest.java  # nghiệp vụ: ghi biên bản, idempotency, khôi phục, top 5
└── RmiRoundTripTest.java           # gọi thật qua RMI Registry trên cổng ngẫu nhiên
```

## 🚀 HƯỚNG DẪN CHẠY

```bash
cd source
mvn -q install -DskipTests
mvn -pl settlement exec:java
```

Khi sẵn sàng, log in ra:

```
Settlement sẵn sàng: rmi://localhost:1099/SettlementService, biên bản ghi tại <...>/settlement/output
```

Bấm `Ctrl+C` để dừng. Chạy trên máy khác server thì phải đặt IP LAN của máy chạy Settlement để
server gọi tới đúng:

```bash
mvn -pl settlement exec:java -Djava.rmi.server.hostname=192.168.1.10
```

và mở cổng RMI (1099 cùng cổng động của đối tượng export) trong firewall.

## ⚙️ CẤU HÌNH

Truyền bằng system property (`-Dkey=value`):

| Khóa | Mặc định | Ý nghĩa |
|---|---|---|
| `settlement.port` | `1099` (`Protocol.DEFAULT_RMI_PORT`) | Cổng RMI Registry |
| `settlement.outputDir` | `settlement/output` | Thư mục ghi biên bản |
| `java.rmi.server.hostname` | — | IP máy này mà server/client khác phải gọi tới (bắt buộc khi chạy khác máy) |

Tên đăng ký trong Registry là `SettlementService` (`Protocol.RMI_SETTLEMENT_NAME`). Nếu cổng đã có
Registry từ lần chạy trước, chương trình dùng lại thay vì báo lỗi.

## 📄 KẾT QUẢ GHI RA

| Tệp | Nội dung |
|---|---|
| `results.jsonl` | Mỗi dòng một `AuctionResultDto` dạng JSON; dùng để nạp lại khi khởi động |
| `bien-ban-phien-<auctionId>.txt` | Biên bản dễ đọc: tên phiên, người thắng, giá thắng, số lượt đặt, giờ bắt đầu/kết thúc (giờ Việt Nam) |

Quy tắc quan trọng:

- **Idempotent theo `auctionId`**: server có thể gửi lại cùng một kết quả khi mạng chập chờn; lần gửi
  lại không cộng thống kê hai lần và không ghi thêm dòng vào `results.jsonl`.
- **Ghi file trước, cập nhật bộ nhớ sau**: nếu ghi đĩa lỗi, `recordResult` ném `RemoteException` để
  server thử lại; bộ nhớ chỉ đổi khi dữ liệu đã nằm an toàn trên đĩa.
- Khởi động lại trên cùng thư mục sẽ nạp lại `results.jsonl` và dựng lại thống kê.

## 📊 `getSummary()`

- `totalAuctions`: số phiên đã nhận biên bản.
- `auctionsWithWinner`: số phiên có người thắng (`winnerUserId != null`).
- `totalValue`: tổng giá thắng của các phiên có người thắng.
- `topWinners`: tối đa 5 người, sắp theo số trận thắng giảm dần, rồi tổng tiền giảm dần, rồi `userId`
  tăng dần; tên hiển thị lấy từ lần thắng mới nhất.

## 🧪 KIỂM THỬ

```bash
cd source
mvn -q -pl settlement -am test
```

Test dùng thư mục tạm (`@TempDir`) và cổng ngẫu nhiên, không cần mạng ngoài và không đụng cổng 1099.

## 🔌 GẮN VÀO SERVER

Module này **không cần sửa `ServerMain`**: nó là tiến trình riêng, chỉ cần chạy Settlement trước (hoặc
sau) server. Phía server gọi RMI nằm ở `server/settlement/SettlementClient` (mốc M3, Kiên gắn khi làm
K3).
