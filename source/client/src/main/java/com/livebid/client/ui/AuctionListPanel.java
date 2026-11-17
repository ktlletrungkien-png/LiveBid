package com.livebid.client.ui;

import com.google.gson.reflect.TypeToken;
import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.PageResult;
import com.livebid.common.dto.Role;
import com.livebid.common.dto.UserView;
import com.livebid.common.payload.ListAuctionsPayload;
import com.livebid.common.protocol.MessageType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
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
 * Danh sách phiên đấu giá sau khi đăng nhập: bảng, bộ lọc trạng thái, phân trang và mở phòng bằng cách
 * bấm đúp.
 *
 * <p>Dữ liệu lấy qua {@code LIST_AUCTIONS} với {@code pageSize} 20. Yêu cầu chạy trên luồng mạng của
 * {@link ServerConnection}; mọi cập nhật bảng đều quay về EDT bằng {@link SwingUtilities#invokeLater}.
 * Nút và bộ lọc bị khóa trong lúc tải để tránh hai yêu cầu chồng nhau.
 */
final class AuctionListPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int PAGE_SIZE = 20;

    private static final Type PAGE_OF_AUCTIONS = new TypeToken<PageResult<AuctionView>>() {
    }.getType();

    private final ServerConnection connection;
    private final UserView user;
    private final LongConsumer openAuction;
    private final Runnable createAuction;
    private final Consumer<AuctionView> editAuction;

    private final AuctionTableModel model = new AuctionTableModel();
    private final JTable table = new JTable(model);
    private final JComboBox<StatusFilter> statusFilter = new JComboBox<>(StatusFilter.values());
    private final JButton refreshButton = new JButton("Làm mới");
    private final JButton createButton = new JButton("Tạo phiên");
    private final JButton editButton = new JButton("Sửa phiên");
    private final JButton previousButton = new JButton("‹ Trang trước");
    private final JButton nextButton = new JButton("Trang sau ›");
    private final JLabel pageLabel = new JLabel();
    private final JLabel messageLabel = new JLabel(" ");

    private int page = 1;
    private boolean hasMore;
    private boolean loading;

    AuctionListPanel(ServerConnection connection, UserView user, LongConsumer openAuction) {
        this(connection, user, openAuction, null, null);
    }

    /**
     * @param createAuction mở form tạo phiên; {@code null} thì không hiện nút (người dùng thường)
     * @param editAuction   mở form sửa phiên đang chọn; {@code null} thì không hiện nút
     */
    AuctionListPanel(ServerConnection connection, UserView user, LongConsumer openAuction,
            Runnable createAuction, Consumer<AuctionView> editAuction) {
        this.connection = connection;
        this.user = user;
        this.openAuction = openAuction;
        this.createAuction = createAuction;
        this.editAuction = editAuction;

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
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

        refreshButton.addActionListener(e -> load(page));
        previousButton.addActionListener(e -> load(Math.max(1, page - 1)));
        nextButton.addActionListener(e -> load(page + 1));
        statusFilter.addActionListener(e -> load(1));
        createButton.addActionListener(e -> {
            if (createAuction != null) {
                createAuction.run();
            }
        });
        editButton.addActionListener(e -> editSelected());
        table.getSelectionModel().addListSelectionListener(e -> updateEditButton());

        updatePager();
    }

    /** Tải lại trang đang xem; gọi sau khi đăng nhập hoặc khi quay lại từ phòng đấu giá. */
    void refresh() {
        load(page);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 6));

        JLabel title = new JLabel("Danh sách phiên đấu giá");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        header.add(title, BorderLayout.NORTH);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolbar.add(new JLabel("Trạng thái:"));
        toolbar.add(statusFilter);
        toolbar.add(refreshButton);
        toolbar.add(previousButton);
        toolbar.add(nextButton);
        toolbar.add(pageLabel);
        if (user.role() == Role.ADMIN && createAuction != null) {
            toolbar.add(createButton);
        }
        if (user.role() == Role.ADMIN && editAuction != null) {
            toolbar.add(editButton);
        }
        header.add(toolbar, BorderLayout.CENTER);

        return header;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(messageLabel, BorderLayout.WEST);
        JLabel hint = new JLabel("Người dùng: " + user.displayName());
        footer.add(hint, BorderLayout.EAST);
        return footer;
    }

    private void load(int requestedPage) {
        if (loading) {
            return;
        }
        loading = true;
        setMessage("Đang tải danh sách phiên...");
        updatePager();

        StatusFilter filter = (StatusFilter) statusFilter.getSelectedItem();
        AuctionStatus status = filter == null ? null : filter.status;
        connection.<PageResult<AuctionView>>call(MessageType.LIST_AUCTIONS,
                        new ListAuctionsPayload(status, requestedPage, PAGE_SIZE), PAGE_OF_AUCTIONS)
                .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                    loading = false;
                    if (error != null) {
                        setMessage("Không tải được danh sách: " + MainFrame.describeError(error));
                        updatePager();
                        return;
                    }
                    if (result == null) {
                        model.setRows(List.of());
                        hasMore = false;
                        updatePager();
                        setMessage("Server không trả về danh sách phiên.");
                        return;
                    }
                    page = Math.max(1, result.page());
                    hasMore = result.hasMore();
                    model.setRows(result.items());
                    updatePager();
                    if (model.getRowCount() == 0) {
                        setMessage("Không có phiên nào ở trang này.");
                    } else {
                        setMessage("Trang " + page + ": " + model.getRowCount() + " phiên.");
                    }
                }));
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

    private void editSelected() {
        AuctionView auction = selectedAuction();
        if (auction == null) {
            setMessage("Hãy chọn một phiên để sửa.");
            return;
        }
        if (auction.status() != AuctionStatus.SCHEDULED) {
            setMessage("Chỉ sửa được phiên chưa bắt đầu.");
            return;
        }
        if (editAuction != null) {
            editAuction.accept(auction);
        }
    }

    private AuctionView selectedAuction() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }

    private void updateEditButton() {
        AuctionView auction = selectedAuction();
        editButton.setEnabled(!loading && editAuction != null
                && auction != null && auction.status() == AuctionStatus.SCHEDULED);
        createButton.setEnabled(!loading && createAuction != null);
    }

    private void updatePager() {
        pageLabel.setText("Trang " + page);
        statusFilter.setEnabled(!loading);
        refreshButton.setEnabled(!loading);
        previousButton.setEnabled(!loading && page > 1);
        nextButton.setEnabled(!loading && hasMore);
        updateEditButton();
    }

    private void setMessage(String message) {
        messageLabel.setText(message == null || message.isBlank() ? " " : message);
    }

    /** Lọc trạng thái; {@code null} nghĩa là tất cả. */
    private enum StatusFilter {
        ALL("Tất cả", null),
        SCHEDULED("Sắp bắt đầu", AuctionStatus.SCHEDULED),
        ACTIVE("Đang diễn ra", AuctionStatus.ACTIVE),
        FINISHED("Đã kết thúc", AuctionStatus.FINISHED);

        private final String label;
        private final AuctionStatus status;

        StatusFilter(String label, AuctionStatus status) {
            this.label = label;
            this.status = status;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
