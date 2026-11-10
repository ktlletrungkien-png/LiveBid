package com.livebid.server.core;

import com.livebid.common.protocol.Message;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kênh nào (TCP hoặc WebSocket) đang ở phòng đấu giá nào. Chỉ dùng từ luồng executor nghiệp vụ,
 * nên dùng {@code HashMap} thường, không cần đồng bộ.
 *
 * <p>Gửi sự kiện chỉ là bỏ vào hàng đợi gửi của từng kênh ({@link ClientChannel#send} không chặn), nên
 * một client chậm không làm chậm cả phòng.
 */
final class RoomManager {

    /** auctionId → (channelId → kênh). LinkedHashMap để gửi theo thứ tự vào phòng. */
    private final Map<Long, Map<String, ClientChannel>> members = new HashMap<>();
    /** channelId → các phòng đang ở; để rời hết khi ngắt kết nối. */
    private final Map<String, Set<Long>> roomsOfChannel = new HashMap<>();

    void join(ClientChannel channel, long auctionId) {
        members.computeIfAbsent(auctionId, id -> new LinkedHashMap<>()).put(channel.id(), channel);
        roomsOfChannel.computeIfAbsent(channel.id(), id -> new HashSet<>()).add(auctionId);
    }

    void leave(ClientChannel channel, long auctionId) {
        removeMember(channel.id(), auctionId);
        Set<Long> rooms = roomsOfChannel.get(channel.id());
        if (rooms != null) {
            rooms.remove(auctionId);
            if (rooms.isEmpty()) {
                roomsOfChannel.remove(channel.id());
            }
        }
    }

    void leaveAll(ClientChannel channel) {
        Set<Long> rooms = roomsOfChannel.remove(channel.id());
        if (rooms != null) {
            rooms.forEach(auctionId -> removeMember(channel.id(), auctionId));
        }
    }

    boolean isInRoom(ClientChannel channel, long auctionId) {
        Map<String, ClientChannel> room = members.get(auctionId);
        return channel != null && room != null && room.containsKey(channel.id());
    }

    int memberCount(long auctionId) {
        Map<String, ClientChannel> room = members.get(auctionId);
        return room == null ? 0 : room.size();
    }

    /** Gửi cho mọi kênh trong phòng; kênh đã đóng thì bỏ khỏi phòng. */
    void broadcast(long auctionId, Message message) {
        Map<String, ClientChannel> room = members.get(auctionId);
        if (room == null) {
            return;
        }
        for (ClientChannel channel : List.copyOf(room.values())) {
            if (channel.isOpen()) {
                channel.send(message);
            } else {
                leaveAll(channel);
            }
        }
    }

    private void removeMember(String channelId, long auctionId) {
        Map<String, ClientChannel> room = members.get(auctionId);
        if (room != null) {
            room.remove(channelId);
            if (room.isEmpty()) {
                members.remove(auctionId);
            }
        }
    }
}
