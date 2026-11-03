// LiveBid web client (HTML + JS thuần).
// REST: đăng nhập, danh sách phiên, lịch sử. WebSocket: vào phòng, đặt giá, nhận sự kiện.
// Định dạng thông điệp WebSocket giống TCP (xem README gốc, mục Giao tiếp).

const API_BASE = `${location.protocol}//${location.host}/api`;
const WS_URL = `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws`;

// TODO (tuần 4): login(), loadAuctions(), connectWebSocket(), joinAuction(), placeBid()
console.log("LiveBid web client", { API_BASE, WS_URL });
