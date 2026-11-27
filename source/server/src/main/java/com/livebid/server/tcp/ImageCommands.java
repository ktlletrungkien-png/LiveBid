package com.livebid.server.tcp;

import static com.livebid.server.tcp.CommandSupport.requireLogin;

import com.livebid.common.dto.ServerInfo;
import com.livebid.common.dto.UploadTicket;
import com.livebid.common.protocol.Message;
import com.livebid.common.protocol.MessageType;
import com.livebid.server.core.ClientChannel;
import com.livebid.server.image.UploadTickets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;

/**
 * SERVER_INFO và REQUEST_UPLOAD_TICKET: cho client desktop dùng ảnh qua HTTP. Ảnh không đi qua TCP để
 * file lớn không chặn lệnh đặt giá trên cùng kết nối.
 */
public final class ImageCommands {

    private final UploadTickets tickets;
    private final IntSupplier httpPort;

    /** @param httpPort cổng HTTP thực tế; chỉ được gọi khi có yêu cầu, nên HTTP phải đã khởi động */
    public ImageCommands(UploadTickets tickets, IntSupplier httpPort) {
        this.tickets = Objects.requireNonNull(tickets);
        this.httpPort = Objects.requireNonNull(httpPort);
    }

    public void registerTo(MessageRouter router) {
        router.register(MessageType.SERVER_INFO, this::serverInfo);
        router.register(MessageType.REQUEST_UPLOAD_TICKET, this::requestUploadTicket);
    }

    private CompletableFuture<ServerInfo> serverInfo(ClientChannel session, Message request) {
        return CompletableFuture.completedFuture(new ServerInfo(httpPort.getAsInt()));
    }

    private CompletableFuture<UploadTicket> requestUploadTicket(ClientChannel session, Message request) {
        return CompletableFuture.completedFuture(tickets.issue(requireLogin(session)));
    }
}
