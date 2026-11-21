// LiveBid – lớp gọi REST API (/api/*) và WebSocket (/ws) tới máy chủ.
// Mọi yêu cầu đều dùng cùng host với trang (location.host) và gửi Bearer token từ sessionStorage.
"use strict";

const TOKEN_KEY = "livebid.token";
const USER_KEY = "livebid.user";

const WS_PROTOCOL_VERSION = 1;
const WS_PING_INTERVAL_MS = 15_000;
const WS_REQUEST_TIMEOUT_MS = 10_000;
const WS_MAX_BACKOFF_MS = 10_000;

class ApiError extends Error {
    constructor(code, message, details) {
        super(message);
        this.code = code;
        this.details = details ?? null;
    }
}

/* ====================== Gọi REST API ====================== */

async function request(method, path, body) {
    const headers = { Accept: "application/json" };
    const token = sessionStorage.getItem(TOKEN_KEY);
    if (token) headers.Authorization = "Bearer " + token;
    if (body !== undefined) headers["Content-Type"] = "application/json";

    let response;
    try {
        response = await fetch(`${location.protocol}//${location.host}${path}`, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw new ApiError("NETWORK", "Không kết nối được máy chủ. Vui lòng thử lại.");
    }
    if (response.status === 204) return null;

    let data = null;
    try {
        data = await response.json();
    } catch {
        // Thân phản hồi rỗng hoặc không phải JSON
    }
    if (!response.ok) {
        const err = data && data.error;
        throw new ApiError(
            (err && err.code) || "HTTP_" + response.status,
            (err && err.message) || "Có lỗi xảy ra, vui lòng thử lại.",
            err && err.details
        );
    }
    return data;
}

function query(params) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value !== null && value !== undefined && value !== "") {
            search.set(key, String(value));
        }
    }
    const text = search.toString();
    return text ? "?" + text : "";
}

function saveToken(res) {
    if (res && res.token) {
        sessionStorage.setItem(TOKEN_KEY, res.token);
    }
    return res;
}

const api = {
    login(username, password) {
        return request("POST", "/api/login", { username, password }).then(saveToken);
    },

    register(username, password, displayName) {
        return request("POST", "/api/register", { username, password, displayName }).then(saveToken);
    },

    logout() {
        const clear = () => {
            sessionStorage.removeItem(TOKEN_KEY);
            sessionStorage.removeItem(USER_KEY);
        };
        return request("POST", "/api/logout").finally(clear);
    },

    me() {
        return request("GET", "/api/me");
    },

    listAuctions(status, page, pageSize) {
        const filterStatus = status && status !== "ALL" ? status : null;
        return request("GET", "/api/auctions" + query({ status: filterStatus, page, pageSize }));
    },

    getAuction(id) {
        return request("GET", "/api/auctions/" + encodeURIComponent(id));
    },

    getBidHistory(id, beforeBidId, limit) {
        return request("GET", "/api/auctions/" + encodeURIComponent(id) + "/bids" + query({ before: beforeBidId, limit }));
    },

    myAuctions(filter) {
        return request("GET", "/api/me/auctions" + query({ filter }));
    },

    createAuction(draft) {
        return request("POST", "/api/auctions", draft);
    },

    updateAuction(id, expectedVersion, draft) {
        return request("PUT", "/api/auctions/" + encodeURIComponent(id), { expectedVersion, draft });
    },

    cancelAuction(id) {
        return request("DELETE", "/api/auctions/" + encodeURIComponent(id));
    },

    pendingAuctions() {
        return request("GET", "/api/auctions/pending");
    },

    approveAuction(id) {
        return request("POST", "/api/auctions/" + encodeURIComponent(id) + "/approve");
    },

    rejectAuction(id, reason) {
        return request("POST", "/api/auctions/" + encodeURIComponent(id) + "/reject", { reason });
    },

    stats() {
        return request("GET", "/api/stats");
    },
};

/* ====================== Kết nối WebSocket (/ws) ====================== */

function newRequestId() {
    if (globalThis.crypto && typeof crypto.randomUUID === "function") {
        return crypto.randomUUID();
    }
    return "req-" + Date.now() + "-" + Math.random().toString(16).slice(2);
}

const socket = {
    /**
     * Mở kết nối tới /ws, xác thực bằng token, tự vào phòng đấu giá và tự kết nối lại khi rớt mạng.
     * handlers: { onSnapshot(snapshot), onUpdate(auctionView, extended), onConnection(online) }
     */
    connect(auctionId, handlers) {
        const wsScheme = location.protocol === "https:" ? "wss" : "ws";
        const url = `${wsScheme}://${location.host}/ws`;
        const pending = new Map();

        let ws = null;
        let stopped = false;
        let online = false;
        let attempt = 0;
        let pingTimer = null;
        let retryTimer = null;

        function setOnline(value) {
            if (online === value) return;
            online = value;
            handlers.onConnection?.(value);
        }

        function stopPing() {
            clearInterval(pingTimer);
            pingTimer = null;
        }

        function rejectPending(error) {
            for (const entry of pending.values()) {
                clearTimeout(entry.timer);
                entry.reject(error);
            }
            pending.clear();
        }

        function sendRequest(type, payload, timeoutMs = WS_REQUEST_TIMEOUT_MS) {
            if (!ws || ws.readyState !== WebSocket.OPEN) {
                return Promise.reject(new ApiError("NOT_CONNECTED", "Chưa kết nối tới máy chủ đấu giá."));
            }
            const requestId = newRequestId();
            const frame = { protocolVersion: WS_PROTOCOL_VERSION, kind: "REQUEST", type, requestId };
            if (payload !== undefined) frame.payload = payload;

            return new Promise((resolve, reject) => {
                const timer = setTimeout(() => {
                    pending.delete(requestId);
                    reject(new ApiError("TIMEOUT", "Quá thời gian chờ phản hồi từ máy chủ."));
                }, timeoutMs);
                pending.set(requestId, { resolve, reject, timer });
                try {
                    ws.send(JSON.stringify(frame));
                } catch {
                    clearTimeout(timer);
                    pending.delete(requestId);
                    reject(new ApiError("CONNECTION_LOST", "Không gửi được dữ liệu tới máy chủ."));
                }
            });
        }

        function startPing() {
            stopPing();
            pingTimer = setInterval(() => {
                if (online) {
                    sendRequest("PING", undefined, 5_000).catch(() => {});
                }
            }, WS_PING_INTERVAL_MS);
        }

        function handleMessage(raw) {
            let msg;
            try {
                msg = JSON.parse(raw);
            } catch {
                return;
            }
            if (msg.kind === "RESPONSE") {
                const entry = pending.get(msg.requestId);
                if (!entry) return;
                pending.delete(msg.requestId);
                clearTimeout(entry.timer);
                if (msg.success) {
                    entry.resolve(msg.payload);
                } else {
                    const err = msg.error || {};
                    entry.reject(new ApiError(err.code || "ERROR", err.message || "Lỗi từ máy chủ.", err.details));
                }
            } else if (msg.kind === "EVENT") {
                handlers.onUpdate?.(msg.payload, msg.type === "AUCTION_EXTENDED");
            }
        }

        function scheduleRetry() {
            if (stopped) return;
            const delay = Math.min(1000 * (2 ** attempt), WS_MAX_BACKOFF_MS);
            attempt += 1;
            retryTimer = setTimeout(open, delay);
        }

        function open() {
            if (stopped) return;
            const token = sessionStorage.getItem(TOKEN_KEY);
            if (!token) {
                setOnline(false);
                return;
            }
            let currentWs;
            try {
                currentWs = new WebSocket(url);
            } catch {
                scheduleRetry();
                return;
            }
            ws = currentWs;

            currentWs.onopen = async () => {
                try {
                    await sendRequest("AUTH", { token });
                    const snapshot = await sendRequest("JOIN_AUCTION", { auctionId });
                    if (stopped || ws !== currentWs) return;
                    attempt = 0;
                    setOnline(true);
                    startPing();
                    handlers.onSnapshot?.(snapshot);
                } catch (err) {
                    if (err && err.code === "UNAUTHORIZED") {
                        stopped = true;
                    }
                    try {
                        currentWs.close();
                    } catch {
                        // Đã đóng
                    }
                }
            };

            currentWs.onmessage = (event) => handleMessage(event.data);
            currentWs.onclose = () => {
                if (ws !== currentWs) return;
                ws = null;
                stopPing();
                setOnline(false);
                rejectPending(new ApiError("CONNECTION_LOST", "Mất kết nối tới máy chủ."));
                scheduleRetry();
            };
        }

        open();

        return {
            placeBid(amount) {
                if (!online) {
                    return Promise.reject(new ApiError("OFFLINE", "Đang kết nối lại..."));
                }
                return sendRequest("PLACE_BID", { auctionId, amount });
            },
            close() {
                stopped = true;
                clearTimeout(retryTimer);
                stopPing();
                rejectPending(new ApiError("CLOSED", "Đã rời phòng đấu giá."));
                const current = ws;
                ws = null;
                if (current) {
                    if (current.readyState === WebSocket.OPEN) {
                        try {
                            current.send(JSON.stringify({
                                protocolVersion: WS_PROTOCOL_VERSION,
                                kind: "REQUEST",
                                type: "LEAVE_AUCTION",
                                requestId: newRequestId(),
                                payload: { auctionId },
                            }));
                        } catch {
                            // Bỏ qua khi đang đóng
                        }
                    }
                    try {
                        current.close(1000, "rời trang");
                    } catch {
                        // Đã đóng
                    }
                }
            },
        };
    },
};
