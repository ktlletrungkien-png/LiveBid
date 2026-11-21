// LiveBid – lớp truy cập dữ liệu.
//
// USE_MOCK = true  : trả dữ liệu mẫu (Promise), không cần máy chủ.
// USE_MOCK = false : gọi fetch tới /api/... trên cùng host với trang (location.host),
//                    kèm "Authorization: Bearer <token>" lấy từ sessionStorage.
// Lỗi từ máy chủ có dạng {"error":{"code","message","details"}} và được ném lại thành ApiError;
// giao diện hiển thị error.message (tiếng Việt).
//
// Đặt giá và sự kiện real-time đi qua WebSocket nên nằm ở đối tượng `socket` riêng (chưa nối thật),
// đối tượng `api` chỉ chứa các hàm REST.
"use strict";

const USE_MOCK = true;

const TOKEN_KEY = "livebid.token";
const USER_KEY = "livebid.user";

class ApiError extends Error {
    constructor(code, message, details) {
        super(message);
        this.code = code;
        this.details = details ?? null;
    }
}

/* ====================== Gọi API thật ====================== */

async function request(method, path, body) {
    const headers = { Accept: "application/json" };
    const token = sessionStorage.getItem(TOKEN_KEY);
    if (token) headers.Authorization = "Bearer " + token;
    if (body !== undefined) headers["Content-Type"] = "application/json";

    let response;
    try {
        response = await fetch(location.protocol + "//" + location.host + path, {
            method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw new ApiError("NETWORK", "Không kết nối được máy chủ. Vui lòng thử lại.");
    }
    if (response.status === 204) return null;

    let data = null;
    try { data = await response.json(); } catch { /* thân phản hồi rỗng hoặc không phải JSON */ }
    if (!response.ok) {
        const err = data && data.error;
        throw new ApiError((err && err.code) || "HTTP_" + response.status,
            (err && err.message) || "Có lỗi xảy ra, vui lòng thử lại.", err && err.details);
    }
    return data;
}

function query(params) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value !== null && value !== undefined && value !== "") search.set(key, value);
    }
    const text = search.toString();
    return text ? "?" + text : "";
}

/* ====================== Dữ liệu mẫu ====================== */

const MIN = 60 * 1000;
const HOUR = 60 * MIN;
const DAY = 24 * HOUR;
const moneyText = (x) => new Intl.NumberFormat("vi-VN").format(x) + "đ";

const db = {
    bidSeq: 0,
    nextAuctionId: 100,
    nextUserId: 10,
    statsDown: true, // lần gọi thống kê đầu tiên báo "chưa chạy", lần sau (Thử lại) mới có số liệu
    users: [
        { id: 1, username: "admin", password: "admin12345", displayName: "Ban quản trị", role: "ADMIN" },
        { id: 2, username: "nguyenvana", password: "matkhau123", displayName: "Nguyễn Văn A", role: "USER" },
        { id: 3, username: "tranthib", password: "matkhau123", displayName: "Trần Thị B", role: "USER" },
        { id: 4, username: "lethic", password: "matkhau123", displayName: "Lê Thị C", role: "USER" },
    ],
    auctions: [],
    bids: [],
};

function userById(id) { return db.users.find((u) => u.id === id); }

function seedAuction(id, ownerId, status, title, description, startingPrice, minIncrement, startOffset, endOffset, extra = {}) {
    const owner = userById(ownerId);
    const now = Date.now();
    db.auctions.push({
        id, title, description, startingPrice, minIncrement, status,
        startsAt: now + startOffset, endsAt: now + endOffset,
        version: 1, extensions: 0, createdAt: now - (200 - id) * MIN,
        ownerId, ownerDisplayName: owner.displayName, ownerRole: owner.role,
        rejectReason: null, ...extra,
    });
}

function seedBid(auctionId, bidderId, amount, minutesAgo) {
    const bidder = userById(bidderId);
    db.bids.push({
        id: ++db.bidSeq, auctionId, bidderId, bidderName: bidder.displayName,
        amount, at: Date.now() - minutesAgo * MIN,
    });
}

(function seed() {
    seedAuction(1, 3, "ACTIVE", "Đồng hồ cơ Seiko 5 Sports cổ", "Đồng hồ tự động Seiko 5 đời 1978, máy chạy tốt, dây da mới thay. Kèm hộp gốc.", 2000000, 100000, -2 * HOUR, 12 * MIN);
    seedAuction(2, 1, "ACTIVE", "Máy ảnh phim Canon AE-1", "Máy ảnh phim Canon AE-1 kèm ống kính 50mm f/1.8, đã bảo dưỡng, màn trập chính xác.", 4000000, 200000, -3 * HOUR, 47 * MIN);
    seedAuction(3, 4, "ACTIVE", "Tranh sơn dầu phố cổ Hà Nội", "Tranh sơn dầu 60x90 cm, vẽ tay, có chữ ký tác giả, đã đóng khung gỗ.", 8000000, 500000, -1 * HOUR, 3 * HOUR);
    seedAuction(4, 1, "ACTIVE", "Laptop ThinkPad X1 Carbon Gen 7", "Core i7, RAM 16GB, SSD 512GB, pin còn 85%. Máy đẹp, đầy đủ phụ kiện.", 12000000, 500000, -5 * HOUR, 1 * DAY);
    seedAuction(5, 3, "SCHEDULED", "Bàn phím cơ Keychron K2", "Bàn phím cơ không dây, switch Brown, đèn nền RGB, còn bảo hành 8 tháng.", 1200000, 50000, 26 * HOUR, 50 * HOUR);
    seedAuction(6, 1, "SCHEDULED", "Bộ ấm trà gốm Bát Tràng", "Bộ ấm chén men rạn thủ công gồm 1 ấm, 6 chén, 1 khay tre.", 800000, 50000, 5 * HOUR, 29 * HOUR);
    seedAuction(7, 2, "SCHEDULED", "Sách cổ Truyện Kiều bản 1925", "Bản in cũ, bìa còn nguyên, giấy ngả vàng theo thời gian, không rách.", 3000000, 100000, 2 * DAY, 3 * DAY);
    seedAuction(8, 4, "FINISHED", "Xe đạp fixed gear Thống Nhất", "Khung thép, bánh 700c, đã thay xích và lốp mới.", 3000000, 100000, -1 * DAY, -2 * HOUR);
    seedAuction(9, 1, "FINISHED", "Loa kéo karaoke cũ", "Loa kéo công suất 300W, kèm 1 micro không dây.", 1500000, 100000, -2 * DAY, -1 * DAY);
    seedAuction(10, 3, "FINISHED", "Bộ cọ vẽ chuyên nghiệp 24 cây", "Lông thú, cán gỗ, đựng trong hộp da.", 600000, 50000, -3 * DAY, -2 * DAY);
    seedAuction(11, 1, "FINISHED", "Máy pha cà phê Delonghi", "Máy pha espresso, đã dùng 1 năm, còn đầy đủ phụ kiện.", 2500000, 100000, -4 * DAY, -3 * DAY);
    seedAuction(12, 1, "ACTIVE", "Tai nghe Sony WH-1000XM4 (còn 2 phút)", "Phiên này sắp kết thúc: đặt giá trong 10 giây cuối để xem phiên được gia hạn thêm 30 giây.", 3000000, 100000, -1 * HOUR, 2 * MIN);
    seedAuction(13, 2, "PENDING_APPROVAL", "Gương cổ khung gỗ chạm", "Gương cổ khung gỗ chạm khắc, kích thước 50x70 cm. Đang chờ quản trị viên duyệt.", 1800000, 100000, 3 * HOUR, 27 * HOUR);
    seedAuction(14, 3, "PENDING_APPROVAL", "Đàn guitar classic Yamaha C40", "Đàn còn mới 95%, kèm bao và capo.", 1500000, 100000, 4 * HOUR, 28 * HOUR);
    seedAuction(15, 2, "REJECTED", "Điện thoại cũ không rõ nguồn gốc", "Mô tả quá sơ sài.", 500000, 50000, 2 * HOUR, 26 * HOUR, { rejectReason: "Mô tả chưa đủ thông tin và thiếu hình ảnh sản phẩm, vui lòng bổ sung rồi gửi lại." });

    // Phiên 1, 2: người dùng nguyenvana (id 2) đang tham gia
    seedBid(1, 4, 2000000, 50); seedBid(1, 2, 2100000, 40); seedBid(1, 4, 2200000, 30); seedBid(1, 2, 2300000, 20);
    seedBid(2, 2, 4000000, 60); seedBid(2, 3, 4200000, 35);
    seedBid(4, 4, 12000000, 90);
    // Phiên đã kết thúc
    seedBid(8, 3, 3000000, 1500); seedBid(8, 2, 3100000, 1400); seedBid(8, 4, 3300000, 300); seedBid(8, 2, 3500000, 150);
    seedBid(10, 2, 600000, 3000); seedBid(10, 4, 650000, 2900);
    seedBid(11, 3, 2500000, 4500); seedBid(11, 2, 2700000, 4400);
})();

function bidsOf(auctionId) {
    return db.bids.filter((b) => b.auctionId === auctionId).sort((a, b) => b.id - a.id);
}

// Trạng thái theo thời gian + các trường tính toán; trả về bản sao để giao diện không sửa trực tiếp dữ liệu.
function view(a) {
    const now = Date.now();
    if (a.status === "SCHEDULED" && now >= a.startsAt) a.status = "ACTIVE";
    if (a.status === "ACTIVE" && now >= a.endsAt) a.status = "FINISHED";
    const bids = bidsOf(a.id);
    const top = bids[0] || null;
    const currentPrice = top ? top.amount : a.startingPrice;
    return {
        id: a.id, title: a.title, description: a.description,
        startingPrice: a.startingPrice, minIncrement: a.minIncrement,
        currentPrice, hasBids: !!top,
        minimumNextBid: top ? top.amount + a.minIncrement : a.startingPrice,
        status: a.status, startsAt: a.startsAt, endsAt: a.endsAt,
        serverTime: now, version: a.version,
        leaderDisplayName: top ? top.bidderName : null,
        ownerId: a.ownerId, ownerDisplayName: a.ownerDisplayName, ownerRole: a.ownerRole,
        rejectReason: a.rejectReason,
    };
}

function sessionUser() {
    const token = sessionStorage.getItem(TOKEN_KEY) || "";
    const user = token.startsWith("mock-") ? userById(Number(token.slice(5))) : null;
    if (!user) throw new ApiError("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục.");
    return user;
}

function requireAdmin() {
    const user = sessionUser();
    if (user.role !== "ADMIN") throw new ApiError("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
    return user;
}

function publicUser(u) { return { id: u.id, displayName: u.displayName, role: u.role }; }

function mock(fn, delay = 250) {
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            try { resolve(structuredClone(fn())); } catch (error) { reject(error); }
        }, delay);
    });
}

function findAuction(id) {
    const a = db.auctions.find((x) => x.id === Number(id));
    if (!a) throw new ApiError("NOT_FOUND", "Không tìm thấy phiên");
    return a;
}

function visibleTo(a, user) {
    return !(a.status === "PENDING_APPROVAL" || a.status === "REJECTED") || user.role === "ADMIN" || a.ownerId === user.id;
}

function checkDraft(draft, user) {
    const title = String(draft.title || "").trim();
    if (!title || title.length > 100) throw new ApiError("VALIDATION", "Tiêu đề phải từ 1 đến 100 ký tự.");
    for (const key of ["startingPrice", "minIncrement"]) {
        const n = draft[key];
        if (!Number.isInteger(n) || n <= 0 || n > 1000000000000) {
            throw new ApiError("VALIDATION", "Giá khởi điểm và bước giá phải là số nguyên dương, tối đa 1.000.000.000.000.");
        }
    }
    if (!(draft.endsAt > draft.startsAt)) throw new ApiError("VALIDATION", "Giờ kết thúc phải sau giờ bắt đầu.");
    if (user.role === "USER" && draft.startsAt < Date.now() + 30 * MIN) {
        throw new ApiError("VALIDATION", "Giờ bắt đầu phải cách hiện tại ít nhất 30 phút.");
    }
}

// Áp một lượt đặt giá lên phiên (dùng chung cho người dùng và "người khác" giả lập).
function applyBid(a, bidderId, bidderName, amount) {
    const v = view(a);
    if (v.status !== "ACTIVE") {
        throw new ApiError("AUCTION_NOT_ACTIVE", v.status === "FINISHED" ? "Phiên đã kết thúc." : "Phiên chưa bắt đầu.");
    }
    if (amount < v.minimumNextBid) {
        throw new ApiError("BID_TOO_LOW", "Giá tối thiểu hiện tại là " + moneyText(v.minimumNextBid),
            { minimumNextBid: v.minimumNextBid });
    }
    const bid = { id: ++db.bidSeq, auctionId: a.id, bidderId, bidderName, amount, at: Date.now() };
    db.bids.push(bid);
    let extended = false;
    if (a.endsAt - Date.now() <= 10000 && a.extensions < 10) { // gia hạn 30 giây, tối đa 10 lần
        a.endsAt += 30000;
        a.extensions++;
        extended = true;
    }
    a.version++;
    return { bid: structuredClone(bid), extended };
}

/* ====================== Đối tượng api ====================== */

const api = {
    // POST /api/login  {username, password} -> {token, user:{id,displayName,role}}
    login(username, password) {
        if (!USE_MOCK) {
            return request("POST", "/api/login", { username, password }).then((res) => {
                sessionStorage.setItem(TOKEN_KEY, res.token);
                return res;
            });
        }
        return mock(() => {
            const user = db.users.find((u) => u.username === username && u.password === password);
            if (!user) throw new ApiError("BAD_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng.");
            const token = "mock-" + user.id;
            sessionStorage.setItem(TOKEN_KEY, token);
            return { token, user: publicUser(user) };
        });
    },

    // POST /api/register  {username, password, displayName} -> {token, user}; luôn tạo vai trò USER
    register(username, password, displayName) {
        if (!USE_MOCK) {
            return request("POST", "/api/register", { username, password, displayName }).then((res) => {
                sessionStorage.setItem(TOKEN_KEY, res.token);
                return res;
            });
        }
        return mock(() => {
            if (db.users.some((u) => u.username === username)) {
                throw new ApiError("USERNAME_TAKEN", "Tên đăng nhập đã được sử dụng.");
            }
            const user = { id: db.nextUserId++, username, password, displayName, role: "USER" };
            db.users.push(user);
            const token = "mock-" + user.id;
            sessionStorage.setItem(TOKEN_KEY, token);
            return { token, user: publicUser(user) };
        });
    },

    // POST /api/logout
    logout() {
        const done = () => { sessionStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(USER_KEY); };
        if (!USE_MOCK) return request("POST", "/api/logout").finally(done);
        return mock(() => { done(); return null; }, 50);
    },

    // GET /api/me -> {id, displayName, role}
    me() {
        if (!USE_MOCK) return request("GET", "/api/me");
        return mock(() => publicUser(sessionUser()), 50);
    },

    // GET /api/auctions?status=ACTIVE|SCHEDULED|FINISHED (bỏ trống = tất cả)&page=1&pageSize=6
    //   -> {items:[...], total, page, pageSize}; không bao giờ trả PENDING_APPROVAL / REJECTED
    listAuctions(status, page, pageSize) {
        if (!USE_MOCK) return request("GET", "/api/auctions" + query({ status, page, pageSize }));
        return mock(() => {
            const order = { ACTIVE: 0, SCHEDULED: 1, FINISHED: 2 };
            const all = db.auctions.map(view)
                .filter((a) => a.status in order && (!status || status === "ALL" || a.status === status))
                .sort((a, b) => order[a.status] - order[b.status]
                    || (a.status === "FINISHED" ? b.endsAt - a.endsAt : (a.status === "ACTIVE" ? a.endsAt - b.endsAt : a.startsAt - b.startsAt)));
            const start = (page - 1) * pageSize;
            return { items: all.slice(start, start + pageSize), total: all.length, page, pageSize };
        });
    },

    // GET /api/auctions/{id}  (PENDING_APPROVAL / REJECTED chỉ chủ phiên và ADMIN xem được, người khác nhận 404)
    getAuction(id) {
        if (!USE_MOCK) return request("GET", "/api/auctions/" + encodeURIComponent(id));
        return mock(() => {
            const a = findAuction(id);
            if (!visibleTo(a, sessionUser())) throw new ApiError("NOT_FOUND", "Không tìm thấy phiên");
            return view(a);
        });
    },

    // GET /api/auctions/{id}/bids?beforeBidId=&limit=20 -> [{id, bidderName, amount, at}] mới nhất trước
    getBidHistory(id, beforeBidId, limit) {
        if (!USE_MOCK) return request("GET", "/api/auctions/" + encodeURIComponent(id) + "/bids" + query({ beforeBidId, limit }));
        return mock(() => bidsOf(Number(id))
            .filter((b) => !beforeBidId || b.id < beforeBidId)
            .slice(0, limit || 20)
            .map(({ id: bidId, bidderName, amount, at }) => ({ id: bidId, bidderName, amount, at })));
    },

    // GET /api/me/auctions?filter=participated|won|created
    myAuctions(filter) {
        if (!USE_MOCK) return request("GET", "/api/me/auctions" + query({ filter }));
        return mock(() => {
            const user = sessionUser();
            const mine = (a) => db.bids.some((b) => b.auctionId === a.id && b.bidderId === user.id);
            if (filter === "created") {
                return db.auctions.filter((a) => a.ownerId === user.id).sort((a, b) => b.createdAt - a.createdAt).map(view);
            }
            const list = db.auctions.map(view).filter((a) => ["ACTIVE", "FINISHED"].includes(a.status) || mine(db.auctions.find((x) => x.id === a.id)));
            if (filter === "won") return list.filter((a) => a.status === "FINISHED" && a.hasBids && a.leaderDisplayName === user.displayName);
            return list.filter((a) => mine(db.auctions.find((x) => x.id === a.id))).sort((a, b) => b.endsAt - a.endsAt);
        });
    },

    // POST /api/auctions  {title, description, startingPrice, minIncrement, startsAt, endsAt} (epoch ms)
    //   USER -> PENDING_APPROVAL (tối đa 5 phiên chờ duyệt); ADMIN -> SCHEDULED
    createAuction(draft) {
        if (!USE_MOCK) return request("POST", "/api/auctions", draft);
        return mock(() => {
            const user = sessionUser();
            checkDraft(draft, user);
            if (user.role === "USER" && db.auctions.filter((a) => a.ownerId === user.id && a.status === "PENDING_APPROVAL").length >= 5) {
                throw new ApiError("TOO_MANY_PENDING", "Bạn đã có tối đa 5 phiên đang chờ duyệt");
            }
            const id = db.nextAuctionId++;
            db.auctions.push({
                id, title: draft.title.trim(), description: (draft.description || "").trim(),
                startingPrice: draft.startingPrice, minIncrement: draft.minIncrement,
                status: user.role === "ADMIN" ? "SCHEDULED" : "PENDING_APPROVAL",
                startsAt: draft.startsAt, endsAt: draft.endsAt, version: 1, extensions: 0, createdAt: Date.now(),
                ownerId: user.id, ownerDisplayName: user.displayName, ownerRole: user.role, rejectReason: null,
            });
            return view(db.auctions[db.auctions.length - 1]);
        });
    },

    // PUT /api/auctions/{id}  {expectedVersion, ...draft}  (chỉ chủ phiên, chỉ khi PENDING_APPROVAL)
    updateAuction(id, expectedVersion, draft) {
        if (!USE_MOCK) return request("PUT", "/api/auctions/" + encodeURIComponent(id), { expectedVersion, ...draft });
        return mock(() => {
            const user = sessionUser();
            const a = findAuction(id);
            if (a.ownerId !== user.id || a.status !== "PENDING_APPROVAL") {
                throw new ApiError("FORBIDDEN", "Chỉ sửa được phiên đang chờ duyệt do bạn đăng.");
            }
            if (a.version !== expectedVersion) throw new ApiError("VERSION_CONFLICT", "Phiên vừa được thay đổi, hãy tải lại trang.");
            checkDraft(draft, user);
            Object.assign(a, {
                title: draft.title.trim(), description: (draft.description || "").trim(),
                startingPrice: draft.startingPrice, minIncrement: draft.minIncrement,
                startsAt: draft.startsAt, endsAt: draft.endsAt, version: a.version + 1,
            });
            return view(a);
        });
    },

    // DELETE /api/auctions/{id}  (chủ phiên hủy phiên đang chờ duyệt)
    cancelAuction(id) {
        if (!USE_MOCK) return request("DELETE", "/api/auctions/" + encodeURIComponent(id));
        return mock(() => {
            const user = sessionUser();
            const a = findAuction(id);
            if (a.ownerId !== user.id || a.status !== "PENDING_APPROVAL") {
                throw new ApiError("FORBIDDEN", "Chỉ hủy được phiên đang chờ duyệt do bạn đăng.");
            }
            db.auctions.splice(db.auctions.indexOf(a), 1);
            return null;
        });
    },

    // GET /api/auctions/pending  (ADMIN) -> phiên PENDING_APPROVAL, cũ nhất trước
    pendingAuctions() {
        if (!USE_MOCK) return request("GET", "/api/auctions/pending");
        return mock(() => {
            requireAdmin();
            return db.auctions.filter((a) => a.status === "PENDING_APPROVAL").sort((a, b) => a.createdAt - b.createdAt).map(view);
        });
    },

    // POST /api/auctions/{id}/approve  (ADMIN) -> phiên SCHEDULED
    approveAuction(id) {
        if (!USE_MOCK) return request("POST", "/api/auctions/" + encodeURIComponent(id) + "/approve");
        return mock(() => {
            requireAdmin();
            const a = findAuction(id);
            if (a.status !== "PENDING_APPROVAL") throw new ApiError("INVALID_STATE", "Phiên này không còn ở hàng chờ duyệt.");
            if (a.startsAt <= Date.now()) {
                throw new ApiError("START_PASSED", "Giờ bắt đầu đã qua, hãy từ chối hoặc yêu cầu người đăng gửi lại");
            }
            a.status = "SCHEDULED";
            a.version++;
            return view(a);
        });
    },

    // POST /api/auctions/{id}/reject  {reason}  (ADMIN) -> phiên REJECTED
    rejectAuction(id, reason) {
        if (!USE_MOCK) return request("POST", "/api/auctions/" + encodeURIComponent(id) + "/reject", { reason });
        return mock(() => {
            requireAdmin();
            const a = findAuction(id);
            if (a.status !== "PENDING_APPROVAL") throw new ApiError("INVALID_STATE", "Phiên này không còn ở hàng chờ duyệt.");
            if (!reason || reason.length > 500) throw new ApiError("VALIDATION", "Lý do từ chối phải từ 1 đến 500 ký tự.");
            a.status = "REJECTED";
            a.rejectReason = reason;
            a.version++;
            return view(a);
        });
    },

    // GET /api/stats (ADMIN) -> {totalAuctions, auctionsWithWinner, totalValue, topWinners:[{displayName,count,total}]}
    //   Dịch vụ thống kê chưa chạy: máy chủ trả lỗi code "STATS_UNAVAILABLE" (giao diện không coi là lỗi nghiêm trọng).
    stats() {
        if (!USE_MOCK) return request("GET", "/api/stats");
        return mock(() => {
            requireAdmin();
            if (db.statsDown) {
                db.statsDown = false; // lần sau (bấm "Thử lại") sẽ có dữ liệu
                throw new ApiError("STATS_UNAVAILABLE", "Dịch vụ thống kê chưa chạy");
            }
            const finished = db.auctions.map(view).filter((a) => a.status === "FINISHED");
            const won = finished.filter((a) => a.hasBids);
            const byWinner = new Map();
            for (const a of won) {
                const row = byWinner.get(a.leaderDisplayName) || { displayName: a.leaderDisplayName, count: 0, total: 0 };
                row.count++;
                row.total += a.currentPrice;
                byWinner.set(a.leaderDisplayName, row);
            }
            return {
                totalAuctions: db.auctions.filter((a) => !["PENDING_APPROVAL", "REJECTED"].includes(a.status)).length,
                auctionsWithWinner: won.length,
                totalValue: won.reduce((sum, a) => sum + a.currentPrice, 0),
                topWinners: [...byWinner.values()].sort((a, b) => b.total - a.total).slice(0, 5),
            };
        });
    },
};

/* ====================== WebSocket giả lập (phòng đấu giá) ====================== */
// Thật sự sẽ là WebSocket tới /ws (xem README, mục Giao tiếp); ở bước này chỉ có bản giả lập.
//   handlers.onUpdate(auction, bid|null, extended)  : có bid mới / đồng bộ lại sau khi nối lại
//   handlers.onConnection(online)                   : trạng thái kết nối
// connect() trả về {placeBid(amount) -> Promise, simulateDrop(), close()}.

const SIMULATED_BIDDERS = [
    { id: 101, name: "Hoàng Văn E" }, { id: 102, name: "Vũ Thị F" }, { id: 103, name: "Đặng Minh G" },
];

const socket = {
    connect(auctionId, handlers) {
        if (!USE_MOCK) {
            setTimeout(() => handlers.onConnection(false), 0);
            return {
                placeBid: () => Promise.reject(new ApiError("NO_SOCKET", "Chưa kết nối được máy chủ đấu giá.")),
                simulateDrop() {}, close() {},
            };
        }
        const user = JSON.parse(sessionStorage.getItem(USER_KEY) || "null");
        const a = findAuction(auctionId);
        let open = true;
        let online = false;
        let simTimer = null;

        const emit = (bid, extended) => { if (online && open) handlers.onUpdate(view(a), bid, extended); };
        setTimeout(() => { if (open) { online = true; handlers.onConnection(true); } }, 400);

        const scheduleSimulation = () => {
            simTimer = setTimeout(() => {
                if (!open) return;
                const v = view(a);
                if (online && v.status === "ACTIVE") {
                    const who = SIMULATED_BIDDERS.filter((b) => b.name !== v.leaderDisplayName)[Math.floor(Math.random() * 2)];
                    try {
                        const r = applyBid(a, who.id, who.name, v.minimumNextBid + (Math.random() < 0.4 ? v.minIncrement : 0));
                        emit(r.bid, r.extended);
                    } catch { /* phiên vừa kết thúc */ }
                }
                scheduleSimulation();
            }, 6000 + Math.random() * 8000);
        };
        scheduleSimulation();

        return {
            placeBid(amount) {
                return new Promise((resolve, reject) => {
                    setTimeout(() => {
                        if (!online) { reject(new ApiError("OFFLINE", "Đang kết nối lại...")); return; }
                        try {
                            const r = applyBid(a, user.id, user.displayName, amount);
                            resolve();
                            emit(r.bid, r.extended);
                        } catch (error) { reject(error); }
                    }, 500);
                });
            },
            simulateDrop() {
                if (!online) return;
                online = false;
                handlers.onConnection(false);
                setTimeout(() => {
                    if (!open) return;
                    online = true;
                    handlers.onConnection(true);
                    handlers.onUpdate(view(a), null, false);
                }, 4000);
            },
            close() { open = false; clearTimeout(simTimer); },
        };
    },
};
