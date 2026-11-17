// Client REST và lưu phiên đăng nhập.
//
// Mọi lệnh gọi trả Promise; lỗi luôn là ApiError có mã lỗi của server (ErrorCode) và thông báo tiếng Việt
// để hiển thị thẳng cho người dùng.

const STORAGE_KEY = "livebid.session";

let baseUrl = "/api";
let fallbackSession = null;
const listeners = new Set();

/** Đổi địa chỉ gốc của REST (dùng khi trang không do chính server phục vụ, ví dụ khi kiểm thử). */
export function configure(options) {
    if (options.baseUrl !== undefined) {
        baseUrl = options.baseUrl;
    }
}

function readStored() {
    try {
        const raw = sessionStorage.getItem(STORAGE_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch {
        return fallbackSession;
    }
}

function writeStored(value) {
    fallbackSession = value;
    try {
        if (value) {
            sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value));
        } else {
            sessionStorage.removeItem(STORAGE_KEY);
        }
    } catch {
        // sessionStorage bị chặn (chế độ riêng tư...): dùng bản trong bộ nhớ, mất khi tải lại trang.
    }
}

/** Phiên đăng nhập của tab này: token + thông tin người dùng, lưu trong sessionStorage. */
export const session = {
    get token() {
        return readStored()?.token ?? null;
    },
    get user() {
        return readStored()?.user ?? null;
    },
    save(loginResult) {
        writeStored({ token: loginResult.token, user: loginResult.user });
        listeners.forEach((fn) => fn(null));
    },
    /** {@code reason}: "expired" khi token bị server từ chối; để trống khi người dùng tự đăng xuất. */
    clear(reason) {
        writeStored(null);
        listeners.forEach((fn) => fn(reason ?? null));
    },
    onChange(listener) {
        listeners.add(listener);
    },
};

export class ApiError extends Error {
    constructor(status, code, message, details) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.code = code;
        this.details = details ?? null;
    }
}

async function request(method, path, body) {
    const headers = {};
    if (body !== undefined) {
        headers["Content-Type"] = "application/json";
    }
    if (session.token) {
        headers.Authorization = "Bearer " + session.token;
    }
    let response;
    try {
        response = await fetch(baseUrl + path, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw new ApiError(0, "NETWORK", "Không kết nối được tới server");
    }
    if (response.status === 204) {
        return null;
    }
    let data = null;
    try {
        data = await response.json();
    } catch {
        // body rỗng hoặc không phải JSON: dùng thông báo mặc định bên dưới
    }
    if (!response.ok) {
        const error = data?.error;
        if (response.status === 401 && error?.code === "UNAUTHORIZED" && session.token) {
            session.clear("expired");
        }
        throw new ApiError(response.status, error?.code ?? `HTTP_${response.status}`,
            error?.message ?? `Lỗi ${response.status}`, error?.details);
    }
    return data;
}

function query(params) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && value !== null && value !== "") {
            search.set(key, String(value));
        }
    }
    const text = search.toString();
    return text ? "?" + text : "";
}

export const api = {
    register: (username, password, displayName) => request("POST", "/register", { username, password, displayName }),
    login: (username, password) => request("POST", "/login", { username, password }),
    logout: () => request("POST", "/logout"),
    me: () => request("GET", "/me"),

    /** {@code status} rỗng = tất cả. Trả {items, page, pageSize, hasMore}. */
    listAuctions: ({ status, page = 1, pageSize = 20 } = {}) =>
        request("GET", "/auctions" + query({ status, page, pageSize })),
    getAuction: (id) => request("GET", `/auctions/${id}`),
    bidHistory: (id, { before, limit = 20 } = {}) => request("GET", `/auctions/${id}/bids` + query({ before, limit })),
    /** {@code filter}: "participated" hoặc "won". */
    myAuctions: (filter) => request("GET", "/me/auctions" + query({ filter })),
    stats: () => request("GET", "/stats"),

    createAuction: (draft) => request("POST", "/auctions", draft),
    updateAuction: (id, expectedVersion, draft) => request("PUT", `/auctions/${id}`, { expectedVersion, draft }),
};
