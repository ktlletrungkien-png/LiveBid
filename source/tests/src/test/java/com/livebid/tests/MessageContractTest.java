package com.livebid.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.payload.PlaceBidPayload;
import com.livebid.common.protocol.ErrorCode;
import com.livebid.common.protocol.JsonCodec;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageKind;
import com.livebid.common.protocol.MessageType;
import com.livebid.common.protocol.ProtocolException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Kiểm tra định dạng thông điệp dùng chung (phong bì, mã hóa một dòng, lỗi giao thức). */
class MessageContractTest {

    @Test
    void requestRoundTrip() throws Exception {
        Message req = Message.request(MessageType.PLACE_BID, "abc-1", new PlaceBidPayload(1, 280_000));
        String json = JsonCodec.encode(req);

        assertEquals("{\"protocolVersion\":1,\"kind\":\"REQUEST\",\"type\":\"PLACE_BID\",\"requestId\":\"abc-1\","
                + "\"payload\":{\"auctionId\":1,\"amount\":280000}}", json);

        Message back = JsonCodec.decode(json);
        assertEquals(MessageType.PLACE_BID, back.typeEnum());
        assertEquals(new PlaceBidPayload(1, 280_000), back.payloadAs(PlaceBidPayload.class));
    }

    @Test
    void encodedMessageIsAlwaysOneLine() {
        AuctionView view = new AuctionView(1, "Bàn phím\ncơ", "dòng 1\r\ndòng 2", AuctionStatus.ACTIVE,
                200_000, 20_000, 200_000, false, null, null, 0, 1, 0, 1, 0);
        String json = JsonCodec.encode(Message.event(MessageType.AUCTION_UPDATED, view));
        assertFalse(json.contains("\n"));
        assertFalse(json.contains("\r"));
        assertTrue(json.contains("Bàn phím"), "UTF-8 tiếng Việt giữ nguyên, không thoát \\uXXXX");
    }

    @Test
    void failureCarriesCodeAndDetails() throws Exception {
        Message req = Message.request(MessageType.PLACE_BID, "abc-2", new PlaceBidPayload(1, 270_000));
        Message res = Message.failure(req, ErrorCode.BID_TOO_LOW, "Giá tối thiểu hiện tại là 280000đ",
                Map.of("minimumAllowed", 280_000L));

        Message back = JsonCodec.decode(JsonCodec.encode(res));
        assertEquals(MessageKind.RESPONSE, back.kind());
        assertEquals("abc-2", back.requestId());
        assertFalse(back.isSuccess());
        assertEquals(ErrorCode.BID_TOO_LOW, back.error().code());
        assertEquals(280_000L, back.error().details().get("minimumAllowed").getAsLong());
        assertNull(back.payload());
    }

    @Test
    void invalidJsonIsRejected() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> JsonCodec.decode("{not json"));
        assertEquals(ErrorCode.INVALID_MESSAGE, e.code());
    }

    @Test
    void wrongVersionIsRejected() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> JsonCodec.decode(
                "{\"protocolVersion\":2,\"kind\":\"REQUEST\",\"type\":\"PING\",\"requestId\":\"x\"}"));
        assertEquals(ErrorCode.UNSUPPORTED_VERSION, e.code());
    }

    @Test
    void requestWithoutRequestIdIsRejected() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> JsonCodec.decode(
                "{\"protocolVersion\":1,\"kind\":\"REQUEST\",\"type\":\"PING\"}"));
        assertEquals(ErrorCode.INVALID_MESSAGE, e.code());
    }

    @Test
    void unknownTypeParsesButHasNoEnum() throws Exception {
        Message m = JsonCodec.decode(
                "{\"protocolVersion\":1,\"kind\":\"REQUEST\",\"type\":\"HACK\",\"requestId\":\"x\"}");
        assertNull(m.typeEnum());
    }

    @Test
    void wrongPayloadShapeIsRejected() throws Exception {
        Message m = JsonCodec.decode("{\"protocolVersion\":1,\"kind\":\"REQUEST\",\"type\":\"PLACE_BID\","
                + "\"requestId\":\"x\",\"payload\":{\"auctionId\":\"abc\",\"amount\":1}}");
        ProtocolException e = assertThrows(ProtocolException.class, () -> m.payloadAs(PlaceBidPayload.class));
        assertEquals(ErrorCode.INVALID_MESSAGE, e.code());
    }
}
