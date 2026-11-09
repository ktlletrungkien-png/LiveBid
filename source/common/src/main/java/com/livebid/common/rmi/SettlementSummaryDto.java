package com.livebid.common.rmi;

import java.io.Serializable;
import java.util.List;

/**
 * Thống kê do Settlement cộng dồn.
 *
 * @param totalAuctions   số phiên đã nhận biên bản
 * @param auctionsWithWinner số phiên có người thắng
 * @param totalValue      tổng giá thắng (VND)
 * @param topWinners      người thắng nhiều nhất, tối đa 5, sắp giảm dần theo {@code wins}
 */
public record SettlementSummaryDto(
        long totalAuctions,
        long auctionsWithWinner,
        long totalValue,
        List<WinnerStat> topWinners) implements Serializable {

    private static final long serialVersionUID = 1L;

    public record WinnerStat(long userId, String displayName, int wins, long totalSpent) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
