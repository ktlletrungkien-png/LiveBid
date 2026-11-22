package com.livebid.client.ui;

import com.google.gson.reflect.TypeToken;
import com.livebid.client.net.ServerConnection;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.MyAuctionFilter;
import com.livebid.common.dto.UserView;
import com.livebid.common.payload.AuctionIdPayload;
import com.livebid.common.payload.MyAuctionsPayload;
import com.livebid.common.protocol.MessageType;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * "Phiên của tôi", giống trang {@code #/mine} của web, hai tab:
 *
 * <ul>
 *   <li><b>Đã tham gia / Đã thắng</b>: phiên đã đặt giá (gộp {@code PARTICIPATED} và {@code WON}), phiên
 *       thắng có nhãn "Bạn đã thắng".</li>
 *   <li><b>Phiên tôi đăng</b> ({@code CREATED}): mọi trạng thái, mới nhất trước. Phiên "Chờ duyệt" có nút
 *       Sửa và Hủy phiên; phiên "Đã từ chối" hiện lý do và chỉ xem.</li>
 * </ul>
 *
 * <p>Server trả {@code List<AuctionView>} không phân trang nên đọc payload bằng {@code TypeToken}.
 */
final class MyAuctionsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Type LIST_OF_AUCTIONS = new TypeToken<List<AuctionView>>() {
    }.getType();

    private final ServerConnection connection;
    private final LongConsumer openAuction;
    private final Consumer<AuctionView> editPending;

    private final JPanel list = new JPanel();
    private final JLabel messageLabel = UiTheme.muted(" ");

    private boolean created;
    private boolean loading;
    private int loadSeq;

    MyAuctionsPanel(ServerConnection connection, UserView user, LongConsumer openAuction,
            Consumer<AuctionView> editPending) {
        this.connection = connection;
        this.openAuction = openAuction;
        this.editPending = editPending;

        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 28, 16, 28));
        setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        header.add(UiTheme.heading("Phiên của tôi"), BorderLayout.NORTH);
        header.add(buildTabs(), BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        add(messageLabel, BorderLayout.SOUTH);
    }

    /** Tải lại tab đang mở; gọi khi mở màn hình hoặc sau khi sửa/hủy phiên. */
    void refresh() {
        load();
    }

    private JPanel buildTabs() {
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabs.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        JToggleButton joined = UiTheme.pillToggle("Đã tham gia / Đã thắng");
        JToggleButton mine = UiTheme.pillToggle("Phiên tôi đăng");
        joined.setSelected(true);
        joined.addActionListener(e -> {
            created = false;
            load();
        });
        mine.addActionListener(e -> {
            created = true;
            load();
        });
        group.add(joined);
        group.add(mine);
        tabs.add(joined);
        tabs.add(mine);
        return tabs;
    }

    private void load() {
        int seq = ++loadSeq;
        loading = true;
        messageLabel.setText("Đang tải...");
        CompletableFuture<List<AuctionView>> main = fetch(created ? MyAuctionFilter.CREATED : MyAuctionFilter.PARTICIPATED);
        CompletableFuture<List<AuctionView>> won = created
                ? CompletableFuture.completedFuture(List.of()) : fetch(MyAuctionFilter.WON);
        main.thenCombine(won, (items, wonItems) -> new List<?>[] {items, wonItems})
                .whenComplete((pair, error) -> SwingUtilities.invokeLater(() -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    loading = false;
                    if (error != null) {
                        list.removeAll();
                        list.revalidate();
                        list.repaint();
                        messageLabel.setText("Không tải được danh sách: " + MainFrame.describeError(error));
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<AuctionView> items = (List<AuctionView>) pair[0];
                    @SuppressWarnings("unchecked")
                    List<AuctionView> wonItems = (List<AuctionView>) pair[1];
                    render(items, wonItems);
                }));
    }

    private CompletableFuture<List<AuctionView>> fetch(MyAuctionFilter filter) {
        CompletableFuture<List<AuctionView>> future = connection.<List<AuctionView>>call(
                MessageType.GET_MY_AUCTIONS, new MyAuctionsPayload(filter), LIST_OF_AUCTIONS);
        return future.thenApply(items -> items == null ? List.<AuctionView>of() : items);
    }

    private void render(List<AuctionView> items, List<AuctionView> wonItems) {
        list.removeAll();
        Set<Long> wonIds = new HashSet<>();
        for (AuctionView won : wonItems) {
            wonIds.add(won.auctionId());
        }
        List<AuctionView> rows = new ArrayList<>();
        if (created) {
            rows.addAll(items);
        } else {
            rows.addAll(wonItems);
            for (AuctionView item : items) {
                if (!wonIds.contains(item.auctionId())) {
                    rows.add(item);
                }
            }
        }
        for (AuctionView auction : rows) {
            list.add(created ? createdRow(auction) : joinedRow(auction, wonIds.contains(auction.auctionId())));
            list.add(Box.createVerticalStrut(14));
        }
        list.revalidate();
        list.repaint();
        messageLabel.setText(rows.isEmpty()
                ? (created ? "Bạn chưa đăng phiên nào." : "Bạn chưa tham gia phiên nào.") : " ");
    }

    private JPanel joinedRow(AuctionView auction, boolean won) {
        JButton enter = auction.status() == AuctionStatus.ACTIVE
                ? UiTheme.primary("Vào phòng") : UiTheme.secondary("Xem chi tiết");
        enter.addActionListener(e -> openAuction.accept(auction.auctionId()));
        return wrap(new AuctionRow(auction, won ? UiTheme.tag("Bạn đã thắng", UiTheme.Tone.WIN) : null,
                List.of(whenText(auction)), null, AuctionRow.buttons(enter), a -> openAuction.accept(a.auctionId())));
    }

    private JPanel createdRow(AuctionView auction) {
        JPanel actions;
        if (auction.status() == AuctionStatus.PENDING_APPROVAL) {
            JButton edit = UiTheme.secondary("Sửa");
            edit.addActionListener(e -> editPending.accept(auction));
            JButton cancel = UiTheme.danger("Hủy phiên");
            cancel.addActionListener(e -> cancelAuction(auction));
            actions = AuctionRow.buttons(edit, cancel);
        } else {
            JButton view = UiTheme.secondary("Xem chi tiết");
            view.addActionListener(e -> openAuction.accept(auction.auctionId()));
            actions = AuctionRow.buttons(view);
        }
        String warning = auction.status() == AuctionStatus.REJECTED
                ? "Lý do từ chối: " + (auction.rejectReason() == null ? "không có" : auction.rejectReason()) : null;
        return wrap(new AuctionRow(auction, null, List.of(whenText(auction)), warning, actions,
                a -> openAuction.accept(a.auctionId())));
    }

    private static JPanel wrap(AuctionRow row) {
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private static String whenText(AuctionView a) {
        return switch (a.status()) {
            case ACTIVE -> "Kết thúc " + TimeFormat.dateTime(a.endsAt());
            case FINISHED -> "Đã kết thúc " + TimeFormat.dateTime(a.endsAt());
            default -> "Bắt đầu " + TimeFormat.dateTime(a.startsAt());
        };
    }

    private void cancelAuction(AuctionView auction) {
        int choice = JOptionPane.showConfirmDialog(this,
                "Bạn chắc chắn muốn hủy phiên \"" + auction.title() + "\"?\nThao tác này không thể hoàn tác.",
                "Hủy phiên", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        messageLabel.setText("Đang hủy phiên...");
        connection.call(MessageType.CANCEL_AUCTION, new AuctionIdPayload(auction.auctionId()), (Type) Void.class)
                .whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        messageLabel.setText("Không hủy được phiên: " + MainFrame.describeError(error));
                    }
                    load();
                }));
    }
}
