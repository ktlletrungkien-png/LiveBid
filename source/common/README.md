# MODULE COMMON

Mã dùng chung cho `server`, `client`, `settlement`, `tests`. Module này không chạy riêng; các module khác phụ thuộc vào nó qua Maven.

> Đây là **hợp đồng giữa các thành viên**. Muốn đổi tên trường, thêm/bớt lệnh hay mã lỗi thì mở Pull Request riêng và báo cả nhóm, vì client, server và web đều phụ thuộc.

## 📦 CẤU TRÚC

```
common/src/main/java/com/livebid/common/
├── Protocol.java            # Phiên bản giao thức, cổng mặc định, giới hạn 64 KiB
├── protocol/                # Phong bì thông điệp TCP/WebSocket
│   ├── Message.java         # protocolVersion, kind, type, requestId, payload, success, error
│   ├── MessageKind.java     # REQUEST / RESPONSE / EVENT
│   ├── MessageType.java     # Tên lệnh và sự kiện
│   ├── ErrorCode.java       # 15 mã lỗi (+ mã HTTP tương ứng cho REST)
│   ├── ErrorInfo.java       # { code, message, details }
│   ├── JsonCodec.java       # Gson dùng chung: encode (JSON một dòng) / decode (có kiểm tra phong bì)
│   └── ProtocolException.java
├── payload/                 # Dữ liệu của từng REQUEST (LoginPayload, PlaceBidPayload, ...)
├── dto/                     # Dữ liệu server trả về (AuctionView, BidView, BidReceipt, RoomSnapshot, ...)
├── udp/                     # Gói UDP: DiscoveryReply, AuctionAnnouncement
└── rmi/                     # Interface RMI SettlementService + AuctionResultDto, SettlementSummaryDto
```

## 🔗 CÁCH DÙNG

Gửi một yêu cầu (client):

```java
Message req = Message.request(MessageType.PLACE_BID, new PlaceBidPayload(1, 280_000));
out.write(JsonCodec.encode(req) + "\n");      // TCP: tự thêm '\n'; WebSocket: gửi nguyên chuỗi
```

Đọc và trả lời (server):

```java
Message req = JsonCodec.decode(line);          // ném ProtocolException nếu JSON lỗi / sai phiên bản
PlaceBidPayload p = req.payloadAs(PlaceBidPayload.class);
Message ok  = Message.success(req, receipt);
Message err = Message.failure(req, ErrorCode.BID_TOO_LOW, "Giá tối thiểu hiện tại là 300000đ",
                              Map.of("minimumAllowed", 300_000L));
```

## Bảng lệnh → payload → kết quả

| Lệnh | Payload gửi lên | Payload trả về khi thành công |
|---|---|---|
| `PING` | — | `{ "serverTime": ... }` |
| `AUTH` (chỉ WebSocket) | `AuthPayload` | `UserView` |
| `REGISTER` | `RegisterPayload` | `LoginResult` |
| `LOGIN` | `LoginPayload` | `LoginResult` |
| `LOGOUT` | — | — |
| `LIST_AUCTIONS` | `ListAuctionsPayload` | `PageResult<AuctionView>` |
| `GET_AUCTION` | `AuctionIdPayload` | `AuctionView` |
| `JOIN_AUCTION` | `AuctionIdPayload` | `RoomSnapshot` |
| `LEAVE_AUCTION` | `AuctionIdPayload` | — |
| `PLACE_BID` | `PlaceBidPayload` | `BidReceipt` |
| `GET_BID_HISTORY` | `BidHistoryPayload` | `List<BidView>` |
| `GET_MY_AUCTIONS` | `MyAuctionsPayload` | `List<AuctionView>` |
| `CREATE_AUCTION` | `AuctionDraft` | `AuctionView` |
| `UPDATE_AUCTION` | `UpdateAuctionPayload` | `AuctionView` |

Sự kiện `AUCTION_STARTED`, `AUCTION_UPDATED`, `AUCTION_EXTENDED`, `AUCTION_FINISHED` đều có payload là `AuctionView`.
