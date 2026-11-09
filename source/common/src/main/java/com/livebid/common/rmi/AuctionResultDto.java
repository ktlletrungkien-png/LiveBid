package com.livebid.common.rmi;

import java.io.Serializable;

/**
 * Kết quả một phiên gửi sang Settlement qua RMI.
 *
 * @param winnerUserId      null nếu không có bid hợp lệ ("Chưa có người thắng")
 * @param winnerDisplayName null nếu không có người thắng
 * @param finalPrice        giá thắng; bằng 0 nếu không có người thắng
 */
public record AuctionResultDto(
        long auctionId,
        String title,
        Long winnerUserId,
        String winnerDisplayName,
        long finalPrice,
        int bidCount,
        long startsAt,
        long finishedAt) implements Serializable {

    private static final long serialVersionUID = 1L;

    public boolean hasWinner() {
        return winnerUserId != null;
    }
}
