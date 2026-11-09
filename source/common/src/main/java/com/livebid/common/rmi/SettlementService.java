package com.livebid.common.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Dịch vụ lập biên bản, chạy ở tiến trình riêng, đăng ký trong RMI Registry (cổng 1099) với tên
 * {@code Protocol.RMI_SETTLEMENT_NAME}.
 *
 * <p>Không nằm trên đường găng của bid: server ghi vào {@code settlement_queue} trước rồi mới gọi,
 * lỗi thì thử lại sau.
 */
public interface SettlementService extends Remote {

    /**
     * Ghi biên bản một phiên đã kết thúc. Phải <b>idempotent</b>: gọi lại cùng {@code auctionId}
     * (do server retry) không được cộng thống kê hai lần.
     */
    void recordResult(AuctionResultDto result) throws RemoteException;

    /** Thống kê cộng dồn, dùng cho REST /api/stats và màn hình quản trị. */
    SettlementSummaryDto getSummary() throws RemoteException;
}
