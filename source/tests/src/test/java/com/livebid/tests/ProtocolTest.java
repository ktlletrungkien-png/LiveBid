package com.livebid.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.livebid.common.Protocol;
import org.junit.jupiter.api.Test;

/** Test khởi tạo: bảo đảm module tests build và chạy được. Các ca T01–T16 sẽ thêm dần. */
class ProtocolTest {

    @Test
    void protocolVersionIsOne() {
        assertEquals(1, Protocol.PROTOCOL_VERSION);
    }

    @Test
    void maxMessageIs64KiB() {
        assertEquals(65536, Protocol.MAX_MESSAGE_BYTES);
    }
}
