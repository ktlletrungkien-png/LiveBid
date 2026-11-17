package com.livebid.client.ui;

import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.UserView;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Màn hình sau đăng nhập: hai tab “Tất cả phiên” và “Phiên của tôi”.
 *
 * <p>Mỗi tab tự tải dữ liệu của mình; đổi tab thì tải lại tab vừa chọn để thấy thay đổi mới nhất (ví dụ
 * sau khi đặt giá hoặc sau khi ADMIN sửa phiên). Panel không gọi thẳng {@link MainFrame}: việc mở phòng,
 * tạo phiên và sửa phiên đều đi qua callback do {@code MainFrame} truyền vào.
 */
final class AuctionHubPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTabbedPane tabs = new JTabbedPane();
    private final AuctionListPanel listPanel;
    private final MyAuctionsPanel myAuctionsPanel;

    /**
     * @param createAuction mở form tạo phiên; {@code null} thì tab danh sách không hiện nút
     * @param editAuction   mở form sửa phiên đang chọn; {@code null} thì tab danh sách không hiện nút
     */
    AuctionHubPanel(ServerConnection connection, UserView user, LongConsumer openAuction,
            Runnable createAuction, Consumer<AuctionView> editAuction) {
        setLayout(new BorderLayout());

        listPanel = new AuctionListPanel(connection, user, openAuction, createAuction, editAuction);
        myAuctionsPanel = new MyAuctionsPanel(connection, user, openAuction);
        tabs.addTab("Tất cả phiên", listPanel);
        tabs.addTab("Phiên của tôi", myAuctionsPanel);
        tabs.addChangeListener(e -> refreshSelected());

        add(tabs, BorderLayout.CENTER);
    }

    /** Tải lại tab đang mở; gọi sau khi đăng nhập, sau khi tạo/sửa phiên hoặc quay lại từ phòng. */
    void refresh() {
        refreshSelected();
    }

    private void refreshSelected() {
        if (tabs.getSelectedComponent() == myAuctionsPanel) {
            myAuctionsPanel.refresh();
        } else {
            listPanel.refresh();
        }
    }
}
