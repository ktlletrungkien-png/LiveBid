package com.livebid.client.ui;

import com.google.gson.reflect.TypeToken;
import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.AuctionSort;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.PageResult;
import com.livebid.common.dto.UserView;
import com.livebid.common.payload.ListAuctionsPayload;
import com.livebid.common.protocol.MessageType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * "Phiên đấu giá": lưới thẻ, bộ lọc dạng pill và phân trang, giống trang {@code #/auctions} của web.
 *
 * <p>Dữ liệu lấy qua {@code LIST_AUCTIONS}. Yêu cầu chạy trên luồng mạng của {@link ServerConnection}; mọi
 * cập nhật giao diện quay về EDT. Đồng hồ trên các thẻ chạy mỗi giây theo giờ server (lệch giờ được tính
 * từ {@code serverTime} của dữ liệu vừa tải). Ô "Sắp xếp" chọn thứ tự (mặc định: theo nhóm ưu tiên); đổi
 * bộ lọc hoặc thứ tự thì quay về trang 1. Bộ lọc, ô sắp xếp và nút phân trang bị khóa trong lúc tải.
 */
final class AuctionListPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int PAGE_SIZE = 12;

    private static final Type PAGE_OF_AUCTIONS = new TypeToken<PageResult<AuctionView>>() {
    }.getType();

    private final ServerConnection connection;
    private final UserView user;
    private final LongConsumer openAuction;
    private final Consumer<AuctionView> editAuction;

    private final CardGrid grid = new CardGrid();
    private final JLabel totalLabel = UiTheme.muted(" ");
    private final JLabel messageLabel = UiTheme.muted(" ");
    private final JLabel pageLabel = UiTheme.muted("Trang 1");
    private final JButton previousButton = UiTheme.secondary("‹ Trước");
    private final JButton nextButton = UiTheme.secondary("Sau ›");
    private final JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JComboBox<SortOption> sortBox = new JComboBox<>(SortOption.values());
    private final Timer clock = new Timer(1_000, e -> tickCards());

    private StatusFilter filter = StatusFilter.ALL;
    private SortOption sort = SortOption.DEFAULT;
    private int page = 1;
    private boolean hasMore;
    private boolean loading;
    private long clockOffset;

    AuctionListPanel(ServerConnection connection, UserView user, LongConsumer openAuction,
            Consumer<AuctionView> editAuction) {
        this.connection = connection;
        this.user = user;
        this.openAuction = openAuction;
        this.editAuction = editAuction;

        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 28, 16, 28));
        setOpaque(false);

        add(buildHeader(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(grid, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        add(buildFooter(), BorderLayout.SOUTH);

        previousButton.addActionListener(e -> load(Math.max(1, page - 1)));
        nextButton.addActionListener(e -> load(page + 1));
        updatePager();
    }

    /** Tải lại trang đang xem; gọi khi mở màn hình hoặc quay lại từ phòng đấu giá. */
    void refresh() {
        load(page);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);

        JPanel titleBlock = new JPanel(new BorderLayout(0, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(UiTheme.heading("Phiên đấu giá"), BorderLayout.NORTH);
        titleBlock.add(totalLabel, BorderLayout.SOUTH);
        header.add(titleBlock, BorderLayout.NORTH);

        filterBar.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (StatusFilter option : StatusFilter.values()) {
            JToggleButton pill = UiTheme.pillToggle(option.label);
            pill.setSelected(option == filter);
            pill.addActionListener(e -> {
                if (loading) {
                    pill.setSelected(false);
                    selectCurrentFilter();
                    return;
                }
                filter = option;
                load(1);
            });
            group.add(pill);
            filterBar.add(pill);
        }
        JPanel controls = new JPanel(new BorderLayout(12, 0));
        controls.setOpaque(false);
        controls.add(filterBar, BorderLayout.CENTER);
        controls.add(buildSortControl(), BorderLayout.EAST);
        header.add(controls, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildSortControl() {
        sortBox.setSelectedItem(sort);
        sortBox.setFont(UiTheme.sans(java.awt.Font.PLAIN, 13f));
        sortBox.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Object text = value instanceof SortOption option ? option.label : value;
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        sortBox.addActionListener(e -> {
            SortOption chosen = (SortOption) sortBox.getSelectedItem();
            if (chosen == null || chosen == sort) {
                return;
            }
            if (loading) {
                sortBox.setSelectedItem(sort);
                return;
            }
            sort = chosen;
            load(1);
        });

        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        wrap.setOpaque(false);
        wrap.add(UiTheme.muted("Sắp xếp:"));
        wrap.add(sortBox);
        return wrap;
    }

    private void selectCurrentFilter() {
        StatusFilter[] all = StatusFilter.values();
        for (int i = 0; i < all.length; i++) {
            ((JToggleButton) filterBar.getComponent(i)).setSelected(all[i] == filter);
        }
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.add(messageLabel, BorderLayout.WEST);

        JPanel pager = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        pager.setOpaque(false);
        pager.add(previousButton);
        pager.add(pageLabel);
        pager.add(nextButton);
        footer.add(pager, BorderLayout.EAST);
        return footer;
    }

    private void load(int requestedPage) {
        if (loading) {
            return;
        }
        loading = true;
        messageLabel.setText("Đang tải danh sách phiên...");
        updatePager();

        AuctionStatus status = filter.status;
        connection.<PageResult<AuctionView>>call(MessageType.LIST_AUCTIONS,
                        new ListAuctionsPayload(status, sort.sort, requestedPage, PAGE_SIZE), PAGE_OF_AUCTIONS)
                .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                    loading = false;
                    if (error != null) {
                        messageLabel.setText("Không tải được danh sách: " + MainFrame.describeError(error));
                        updatePager();
                        return;
                    }
                    show(result);
                }));
    }

    private void show(PageResult<AuctionView> result) {
        grid.removeAll();
        List<AuctionView> items = result == null ? List.of() : result.items();
        hasMore = result != null && result.hasMore();
        if (result != null) {
            page = Math.max(1, result.page());
        }
        if (!items.isEmpty()) {
            clockOffset = items.get(0).serverTime() - System.currentTimeMillis();
        }
        for (AuctionView auction : items) {
            grid.add(new AuctionCard(auction, a -> openAuction.accept(a.auctionId()),
                    editAuction, user.role()));
        }
        if (items.isEmpty()) {
            messageLabel.setText("Chưa có phiên nào trong mục này.");
            totalLabel.setText("0 phiên");
        } else {
            messageLabel.setText(" ");
            totalLabel.setText(items.size() + " phiên ở trang này");
        }
        grid.revalidate();
        grid.repaint();
        updatePager();
        tickCards();
        if (!clock.isRunning()) {
            clock.start();
        }
    }

    private void tickCards() {
        long serverNow = System.currentTimeMillis() + clockOffset;
        for (java.awt.Component component : grid.getComponents()) {
            if (component instanceof AuctionCard card) {
                card.tick(serverNow);
            }
        }
    }

    /** Dừng đồng hồ khi màn hình bị gỡ. */
    void close() {
        clock.stop();
    }

    private void updatePager() {
        pageLabel.setText("Trang " + page);
        previousButton.setEnabled(!loading && page > 1);
        nextButton.setEnabled(!loading && hasMore);
        for (java.awt.Component component : filterBar.getComponents()) {
            component.setEnabled(!loading);
        }
        sortBox.setEnabled(!loading);
    }

    /** Lựa chọn trong ô "Sắp xếp". */
    private enum SortOption {
        DEFAULT("Mặc định (ưu tiên)", AuctionSort.DEFAULT),
        ENDS_SOONEST("Kết thúc sớm nhất", AuctionSort.ENDS_SOONEST),
        ENDS_LATEST("Kết thúc muộn nhất", AuctionSort.ENDS_LATEST),
        STARTS_SOONEST("Bắt đầu sớm nhất", AuctionSort.STARTS_SOONEST),
        STARTS_LATEST("Bắt đầu muộn nhất", AuctionSort.STARTS_LATEST);

        private final String label;
        private final AuctionSort sort;

        SortOption(String label, AuctionSort sort) {
            this.label = label;
            this.sort = sort;
        }
    }

    /** Bộ lọc trạng thái; {@code null} nghĩa là tất cả. */
    private enum StatusFilter {
        ALL("Tất cả", null),
        ACTIVE("Đang diễn ra", AuctionStatus.ACTIVE),
        SCHEDULED("Sắp diễn ra", AuctionStatus.SCHEDULED),
        FINISHED("Đã kết thúc", AuctionStatus.FINISHED);

        private final String label;
        private final AuctionStatus status;

        StatusFilter(String label, AuctionStatus status) {
            this.label = label;
            this.status = status;
        }
    }
}
