package com.livebid.common;

/**
 * Hằng số giao thức dùng chung cho server, client và tests.
 * Xem mục 7 (TCP), 9 (UDP), 11 (WebSocket), 12 (RMI) trong kế hoạch.
 */
public final class Protocol {

    public static final int PROTOCOL_VERSION = 1;

    /** Giới hạn kích thước một thông điệp TCP (JSON một dòng, kết thúc bằng '\n'). */
    public static final int MAX_MESSAGE_BYTES = 64 * 1024;

    public static final int DEFAULT_TCP_PORT = 5000;
    public static final int DEFAULT_UDP_DISCOVERY_PORT = 5001;
    public static final int DEFAULT_HTTP_PORT = 8080;
    public static final String DEFAULT_MULTICAST_GROUP = "239.255.42.99";
    public static final int DEFAULT_MULTICAST_PORT = 5002;
    public static final int DEFAULT_RMI_PORT = 1099;

    public static final String DISCOVERY_REQUEST = "LIVEBID_DISCOVER";
    public static final String RMI_SETTLEMENT_NAME = "SettlementService";

    private Protocol() {
    }
}
