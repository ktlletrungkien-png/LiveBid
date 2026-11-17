// Kết nối WebSocket tới /ws: xác thực, gửi/nhận thông điệp theo requestId, giữ kết nối sống.
//
// Định dạng thông điệp giống TCP (xem README gốc):
//   {"protocolVersion":1,"kind":"REQUEST","type":"PLACE_BID","requestId":"...","payload":{...}}
//
// Hành vi:
//   - Mở kết nối xong tự gửi AUTH bằng token lấy từ REST /api/login; AUTH thành công mới coi là "online".
//   - Gửi PING mỗi 15 giây (server đóng kết nối im lặng quá 45 giây).
//   - Mất kết nối thì tự thử lại sau 1, 2, 4... tối đa 10 giây, rồi gửi lại AUTH và gọi onReady
//     để màn hình vào lại phòng và nhận snapshot mới.
//   - Mỗi yêu cầu có hạn chờ 10 giây; hết hạn hoặc mất kết nối giữa chừng thì Promise bị từ chối với mã
//     TIMEOUT / CONNECTION_LOST. Với PLACE_BID điều đó nghĩa là "chưa biết kết quả", không phải "thất bại".

import { ApiError } from "./api.js";
import { uuid } from "./util.js";

const PROTOCOL_VERSION = 1;
const PING_INTERVAL_MS = 15_000;
const REQUEST_TIMEOUT_MS = 10_000;
const MAX_BACKOFF_MS = 10_000;

export function defaultUrl() {
    return `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws`;
}

/**
 * Các trạng thái gửi cho onState: "connecting" | "online" | "offline" (đang chờ thử lại) |
 * "auth-failed" (token bị từ chối, không thử lại) | "closed" (do stop()).
 */
export class LiveSocket {
    #url;
    #token;
    #onEvent;
    #onState;
    #onReady;
    #WebSocketImpl;
    #ws = null;
    #state = "idle";
    #stopped = false;
    #attempt = 0;
    #pending = new Map();
    #pingTimer = null;
    #retryTimer = null;

    /**
     * @param url           địa chỉ /ws; mặc định lấy theo trang đang mở
     * @param token         token từ REST /api/login, gửi trong lệnh AUTH
     * @param onEvent       (type, payload) cho mỗi sự kiện AUCTION_*
     * @param onState       (state) khi trạng thái kết nối đổi
     * @param onReady       gọi sau mỗi lần AUTH thành công, kể cả sau khi tự kết nối lại
     * @param WebSocketImpl lớp WebSocket (thay được khi kiểm thử)
     */
    constructor({ url = defaultUrl(), token, onEvent, onState, onReady, WebSocketImpl = globalThis.WebSocket }) {
        this.#url = url;
        this.#token = token;
        this.#onEvent = onEvent;
        this.#onState = onState;
        this.#onReady = onReady;
        this.#WebSocketImpl = WebSocketImpl;
    }

    get state() {
        return this.#state;
    }

    start() {
        this.#stopped = false;
        this.#connect();
    }

    stop() {
        this.#stopped = true;
        clearTimeout(this.#retryTimer);
        this.#stopPing();
        const ws = this.#ws;
        this.#ws = null;
        this.#rejectAll(new ApiError(0, "CONNECTION_LOST", "Đã đóng kết nối"));
        if (ws) {
            try {
                ws.close(1000, "rời trang");
            } catch {
                // đã đóng
            }
        }
        this.#setState("closed");
    }

    /** Gửi một REQUEST và chờ RESPONSE cùng requestId. Chỉ gọi được khi đã online. */
    request(type, payload, timeoutMs = REQUEST_TIMEOUT_MS) {
        if (this.#state !== "online") {
            return Promise.reject(new ApiError(0, "NOT_CONNECTED", "Chưa kết nối tới server"));
        }
        return this.#send(type, payload, timeoutMs);
    }

    // ------------------------------------------------------------------ nội bộ

    #setState(state) {
        if (this.#state === state) {
            return;
        }
        this.#state = state;
        this.#onState?.(state);
    }

    #connect() {
        this.#setState("connecting");
        let ws;
        try {
            ws = new this.#WebSocketImpl(this.#url);
        } catch {
            this.#scheduleRetry();
            return;
        }
        this.#ws = ws;
        ws.onopen = () => {
            this.#send("AUTH", { token: this.#token }, REQUEST_TIMEOUT_MS)
                .then(() => {
                    this.#attempt = 0;
                    this.#setState("online");
                    this.#startPing();
                    this.#onReady?.();
                })
                .catch((error) => {
                    if (error.code === "UNAUTHORIZED") {
                        this.#stopped = true;
                        this.#setState("auth-failed");
                    }
                    ws.close();
                });
        };
        ws.onmessage = (event) => this.#handle(event.data);
        ws.onerror = () => {
            // onclose luôn đến ngay sau; xử lý ở đó
        };
        ws.onclose = () => {
            if (this.#ws !== ws) {
                return; // kết nối cũ đã bị thay thế
            }
            this.#ws = null;
            this.#stopPing();
            this.#rejectAll(new ApiError(0, "CONNECTION_LOST", "Mất kết nối tới server"));
            if (!this.#stopped) {
                this.#setState("offline");
                this.#scheduleRetry();
            }
        };
    }

    #scheduleRetry() {
        if (this.#stopped) {
            return;
        }
        this.#setState("offline");
        const delay = Math.min(1000 * 2 ** this.#attempt, MAX_BACKOFF_MS);
        this.#attempt += 1;
        this.#retryTimer = setTimeout(() => this.#connect(), delay);
    }

    #send(type, payload, timeoutMs) {
        const ws = this.#ws;
        if (!ws || ws.readyState !== 1) {
            return Promise.reject(new ApiError(0, "NOT_CONNECTED", "Chưa kết nối tới server"));
        }
        const requestId = uuid();
        const message = { protocolVersion: PROTOCOL_VERSION, kind: "REQUEST", type, requestId };
        if (payload !== undefined) {
            message.payload = payload;
        }
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                this.#pending.delete(requestId);
                reject(new ApiError(0, "TIMEOUT", "Quá thời gian chờ phản hồi từ server"));
            }, timeoutMs);
            this.#pending.set(requestId, { resolve, reject, timer });
            try {
                ws.send(JSON.stringify(message));
            } catch (error) {
                clearTimeout(timer);
                this.#pending.delete(requestId);
                reject(new ApiError(0, "CONNECTION_LOST", "Không gửi được: " + error.message));
            }
        });
    }

    #handle(text) {
        let message;
        try {
            message = JSON.parse(text);
        } catch {
            console.warn("Bỏ qua thông điệp không phải JSON từ server");
            return;
        }
        if (message.kind === "RESPONSE") {
            const entry = this.#pending.get(message.requestId);
            if (!entry) {
                return; // phản hồi muộn của yêu cầu đã hết hạn, hoặc lỗi không gắn requestId
            }
            this.#pending.delete(message.requestId);
            clearTimeout(entry.timer);
            if (message.success) {
                entry.resolve(message.payload);
            } else {
                const error = message.error ?? {};
                entry.reject(new ApiError(0, error.code ?? "ERROR", error.message ?? "Lỗi từ server", error.details));
            }
        } else if (message.kind === "EVENT") {
            this.#onEvent?.(message.type, message.payload);
        }
    }

    #rejectAll(error) {
        for (const entry of this.#pending.values()) {
            clearTimeout(entry.timer);
            entry.reject(error);
        }
        this.#pending.clear();
    }

    #startPing() {
        this.#stopPing();
        this.#pingTimer = setInterval(() => {
            if (this.#state === "online") {
                this.#send("PING", undefined, 5_000).catch(() => {
                    // mất PING sẽ được onclose xử lý
                });
            }
        }, PING_INTERVAL_MS);
    }

    #stopPing() {
        clearInterval(this.#pingTimer);
        this.#pingTimer = null;
    }
}
