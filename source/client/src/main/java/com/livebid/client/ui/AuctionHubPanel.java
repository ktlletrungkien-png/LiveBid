package com.livebid.client.ui;

import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.Role;
import com.livebid.common.dto.UserView;
import java.awt.CardLayout;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import javax.swing.JPanel;

/**
 * Khu vực chính sau đăng nhập: "Phiên đấu giá", "Phiên của tôi" và (chỉ ADMIN) "Quản trị".
 *
 * <p>Việc chọn màn hình do thanh điều hướng trên cùng của {@link MainFrame} làm (giống menu của web), nên
 * panel này không có tab riêng. Mỗi lần chuyển màn hình đều tải lại dữ liệu để thấy thay đổi mới nhất. Panel
 * không gọi thẳng {@code MainFrame}: mở phòng và sửa phiên đi qua callback.
 */
final class AuctionHubPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Các màn hình con. */
    enum View { LIST, MINE, ADMIN }

    private final CardLayout cards = new CardLayout();
    private final AuctionListPanel listPanel;
    private final MyAuctionsPanel myAuctionsPanel;
    private final AdminPanel adminPanel;

    private View current = View.LIST;

    AuctionHubPanel(ServerConnection connection, UserView user, LongConsumer openAuction,
            Consumer<AuctionView> editAuction) {
        setLayout(cards);
        setOpaque(false);

        listPanel = new AuctionListPanel(connection, user, openAuction, editAuction);
        myAuctionsPanel = new MyAuctionsPanel(connection, user, openAuction, editAuction);
        adminPanel = user.role() == Role.ADMIN ? new AdminPanel(connection, openAuction) : null;

        add(listPanel, View.LIST.name());
        add(myAuctionsPanel, View.MINE.name());
        if (adminPanel != null) {
            add(adminPanel, View.ADMIN.name());
        }
    }

    View current() {
        return current;
    }

    /** Hiện một màn hình rồi tải lại dữ liệu của nó. ADMIN mới có {@link View#ADMIN}. */
    void show(View view) {
        current = (view == View.ADMIN && adminPanel == null) ? View.LIST : view;
        cards.show(this, current.name());
        refresh();
    }

    /** Tải lại màn hình đang mở; gọi sau khi đăng nhập, tạo/sửa phiên hoặc quay lại từ phòng. */
    void refresh() {
        switch (current) {
            case MINE -> myAuctionsPanel.refresh();
            case ADMIN -> {
                if (adminPanel != null) {
                    adminPanel.refresh();
                }
            }
            default -> listPanel.refresh();
        }
    }

    /** Dừng đồng hồ của các thẻ khi hub bị gỡ. */
    void close() {
        listPanel.close();
    }
}
