# MODULE COMMON

Mã dùng chung cho `server`, `client`, `settlement`, `tests`:

- Hằng số giao thức (`Protocol`): phiên bản, cổng mặc định, giới hạn thông điệp.
- DTO và mã lỗi của giao thức TCP/WebSocket (mục 7 kế hoạch).
- Interface RMI `SettlementService` và các DTO đi kèm (mục 12 kế hoạch).

Module này không chạy riêng; các module khác phụ thuộc vào nó qua Maven.
