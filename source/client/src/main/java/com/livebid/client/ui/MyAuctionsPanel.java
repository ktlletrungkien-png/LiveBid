package com.livebid.client.ui;

import com.google.gson.reflect.TypeToken;
import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.MyAuctionFilter;
import com.livebid.common.dto.UserView;
import com.livebid.common.payload.MyAuctionsPayload;
import com.livebid.common.protocol.MessageType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.LongConsumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Màn “Phiên của tôi”: các phiên người dùng đã đặt giá hoặc đã thắng.
 *
 * <p>Dữ liệu lấy qua {@code GET_MY_AUCTIONS} với bộ lọc {@link MyAuctionFilter}. Server trả
 * {@code List<AuctionView>} (không phân trang) nên phải dùng {@code TypeToken} khi đọc payload.
 * Bấm đúp một dòng để mở phòng đấu giá qua callback, giống {@link AuctionListPanel}.
 */
final class MyAuctionsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Type LIST_OF_AUCTIONS = new TypeToken<List<AuctionView>>() {
    }.getType();

    private final ServerConnection connection;
    private final UserView user;
    private final LongConsumer openAuction;

    private final AuctionTableModel model = new AuctionTableModel();
    private final JTable table = new JTable(model);
    private final JComboBox<FilterChoice> filterBox = new JComboBox<>(FilterChoice.values());
    private final JButton refreshButton = new JButton("Làm mới");
    private final JLabel messageLabel = new JLabel(" ");

    private boolean loading;

    MyAuctionsPanel(ServerConnection connection, UserView user, LongConsumer openAuction) {
        this.connection = connection;
        this.user = user;
        this.openAuction = openAuction;

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        UiTheme.table(table);
        table.setToolTipText("Bấm đúp vào một phiên để vào phòng đấu giá");
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });

        add(buildHeader(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> load());
        filterBox.addActionListener(e -> load());
    }

    /** Tải lại danh sách theo bộ lọc đang chọn; gọi khi mở tab hoặc quay lại từ phòng. */
    void refresh() {
        load();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));

        JLabel title = new JLabel("Phiên của tôi");
        UiTheme.title(title, 24f);
        header.add(title, BorderLayout.NORTH);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolbar.add(new JLabel("Xem:"));
        toolbar.add(filterBox);
        toolbar.add(refreshButton);
        header.add(toolbar, BorderLayout.CENTER);

        return header;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(messageLabel, BorderLayout.WEST);
        footer.add(new JLabel("Người dùng: " + user.displayName()), BorderLayout.EAST);
        return footer;
    }

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        FilterChoice choice = (FilterChoice) filterBox.getSelectedItem();
        MyAuctionFilter filter = choice == null ? MyAuctionFilter.PARTICIPATED : choice.filter;
        setMessage("Đang tải danh sách phiên...");
        updateControls();

        connection.<List<AuctionView>>call(MessageType.GET_MY_AUCTIONS,
                        new MyAuctionsPayload(filter), LIST_OF_AUCTIONS)
                .whenComplete((auctions, error) -> SwingUtilities.invokeLater(() -> {
                    loading = false;
                    updateControls();
                    if (error != null) {
                        setMessage("Không tải được danh sách: " + MainFrame.describeError(error));
                        return;
                    }
                    model.setRows(auctions);
                    if (model.getRowCount() == 0) {
                        setMessage(emptyMessage(filter));
                    } else {
                        setMessage(model.getRowCount() + " phiên.");
                    }
                }));
    }

    private static String emptyMessage(MyAuctionFilter filter) {
        return filter == MyAuctionFilter.WON
                ? "Bạn chưa thắng phiên nào."
                : "Bạn chưa đặt giá ở phiên nào.";
    }

    private void openSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            setMessage("Hãy chọn một phiên rồi bấm đúp.");
            return;
        }
        AuctionView auction = model.rowAt(table.convertRowIndexToModel(row));
        if (auction != null) {
            openAuction.accept(auction.auctionId());
        }
    }

    private void updateControls() {
        filterBox.setEnabled(!loading);
        refreshButton.setEnabled(!loading);
    }

    private void setMessage(String message) {
        messageLabel.setText(message == null || message.isBlank() ? " " : message);
    }

    /** Lựa chọn trong combo, kèm nhãn tiếng Việt. */
    private enum FilterChoice {
        PARTICIPATED("Đã tham gia", MyAuctionFilter.PARTICIPATED),
        WON("Đã thắng", MyAuctionFilter.WON);

        private final String label;
        private final MyAuctionFilter filter;

        FilterChoice(String label, MyAuctionFilter filter) {
            this.label = label;
            this.filter = filter;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
