package com.livebid.common.udp;

/**
 * Gói UDP server trả lời khi nhận chuỗi {@code Protocol.DISCOVERY_REQUEST} ("LIVEBID_DISCOVER")
 * trên cổng 5001. Client lấy IP từ địa chỉ nguồn của gói nhận được, không đặt IP trong gói.
 *
 * <pre>{"name":"LiveBid","tcpPort":5000,"httpPort":8080}</pre>
 */
public record DiscoveryReply(String name, int tcpPort, int httpPort) {
}
