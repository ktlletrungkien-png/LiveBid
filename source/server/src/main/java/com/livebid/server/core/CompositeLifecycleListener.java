package com.livebid.server.core;

import com.livebid.common.dto.AuctionView;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gộp nhiều {@link AuctionLifecycleListener} thành một, vì {@code AuctionServiceImpl} chỉ nhận một listener.
 *
 * <p>Mỗi listener được gọi tuần tự trên luồng executor nghiệp vụ. Một listener ném lỗi thì ghi log rồi vẫn gọi
 * các listener còn lại, để lỗi UDP không làm mất việc xếp hàng gửi kết quả sang Settlement (và ngược lại).
 */
public final class CompositeLifecycleListener implements AuctionLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(CompositeLifecycleListener.class);

    private final List<AuctionLifecycleListener> listeners;

    public CompositeLifecycleListener(List<? extends AuctionLifecycleListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    @Override
    public void auctionStartingSoon(AuctionView auction) {
        for (AuctionLifecycleListener listener : listeners) {
            try {
                listener.auctionStartingSoon(auction);
            } catch (RuntimeException e) {
                log.warn("{} lỗi ở auctionStartingSoon: {}", listener.getClass().getSimpleName(), e.toString());
            }
        }
    }

    @Override
    public void auctionFinished(AuctionView auction) {
        for (AuctionLifecycleListener listener : listeners) {
            try {
                listener.auctionFinished(auction);
            } catch (RuntimeException e) {
                log.warn("{} lỗi ở auctionFinished: {}", listener.getClass().getSimpleName(), e.toString());
            }
        }
    }
}
