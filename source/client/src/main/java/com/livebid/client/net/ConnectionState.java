package com.livebid.client.net;

/** Trạng thái kết nối TCP của client, dùng cho thanh trạng thái và nút bấm trong giao diện. */
public enum ConnectionState {

    /** Đang mở socket tới server. */
    CONNECTING,

    /** Socket đã mở, đọc/ghi được. */
    CONNECTED,

    /** Chưa kết nối, hoặc kết nối vừa đứt. */
    DISCONNECTED
}
