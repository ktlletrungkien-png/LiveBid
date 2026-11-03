# MODULE SETTLEMENT

Dịch vụ RMI chạy như một tiến trình riêng. Khi một phiên đấu giá kết thúc, server gọi
`SettlementService.recordResult(...)` để lập biên bản và cộng dồn thống kê; `getSummary()`
cung cấp số liệu cho `/api/stats` và màn hình quản trị.

Settlement **không** nằm trên đường găng của việc đặt giá: nếu dịch vụ này tắt, đấu giá vẫn chạy
và kết quả vẫn hợp lệ; server sẽ gửi bù khi Settlement chạy lại.

## Chạy

```bash
cd source
mvn -q install -DskipTests
mvn -q -pl settlement exec:java
```

- RMI Registry: cổng `1099`, tên `SettlementService`.
- Khi chạy trên máy khác server, đặt `-Djava.rmi.server.hostname=<IP LAN>` và mở cổng RMI trong firewall.
