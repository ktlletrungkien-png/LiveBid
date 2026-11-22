package com.livebid.client.ui;

import com.google.gson.reflect.TypeToken;
import com.livebid.client.net.ServerConnection;
import com.livebid.client.room.MoneyFormat;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.payload.AuctionIdPayload;
import com.livebid.common.payload.RejectAuctionPayload;
import com.livebid.common.protocol.MessageType;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.LongConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * "Quản trị" (chỉ ADMIN), giống trang {@code #/admin} của web: hàng chờ duyệt, cũ nhất trên cùng.
 *
 * <p>Mỗi phiên có nút <b>Duyệt</b> ({@code APPROVE_AUCTION}) và <b>Từ chối</b> ({@code REJECT_AUCTION} kèm
 * lý do bắt buộc 1–500 ký tự). Server là nơi quyết định: ví dụ giờ bắt đầu đã qua thì trả lỗi và câu thông
 * báo được hiện nguyên văn. Thống kê (Settlement) chưa có trong app, xem trên web.
 */
final class AdminPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int MAX_REASON = 500;

    private static final Type LIST_OF_AUCTIONS = new TypeToken<List<AuctionView>>() {
    }.getType();

    private final ServerConnection connection;
    private final LongConsumer openAuction;

    private final JPanel list = new JPanel();
    private final JLabel messageLabel = UiTheme.muted(" ");
    private final JLabel countLabel = UiTheme.muted(" ");
    private int loadSeq;

    AdminPanel(ServerConnection connection, LongConsumer openAuction) {
        this.connection = connection;
        this.openAuction = openAuction;

        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 28, 16, 28));
        setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.setOpaque(false);
        header.add(UiTheme.heading("Hàng chờ duyệt"), BorderLayout.NORTH);
        header.add(countLabel, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        add(messageLabel, BorderLayout.SOUTH);
    }

    /** Tải lại hàng chờ; gọi khi mở màn hình. */
    void refresh() {
        int seq = ++loadSeq;
        messageLabel.setText("Đang tải hàng chờ duyệt...");
        connection.<List<AuctionView>>call(MessageType.GET_PENDING_AUCTIONS, null, LIST_OF_AUCTIONS)
                .whenComplete((items, error) -> SwingUtilities.invokeLater(() -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    if (error != null) {
                        messageLabel.setText("Không tải được hàng chờ: " + MainFrame.describeError(error));
                        return;
                    }
                    render(items == null ? List.of() : items);
                }));
    }

    private void render(List<AuctionView> items) {
        list.removeAll();
        for (AuctionView auction : items) {
            list.add(row(auction));
            list.add(Box.createVerticalStrut(14));
        }
        list.revalidate();
        list.repaint();
        countLabel.setText(items.size() + " phiên đang chờ duyệt");
        messageLabel.setText(items.isEmpty() ? "Không có phiên nào đang chờ duyệt." : " ");
    }

    private JPanel row(AuctionView auction) {
        JButton approve = UiTheme.primary("Duyệt");
        JButton reject = UiTheme.secondary("Từ chối");
        approve.addActionListener(e -> approve(auction, approve, reject));
        reject.addActionListener(e -> reject(auction));
        AuctionRow row = new AuctionRow(auction, null, List.of(
                "Người đăng: " + auction.ownerDisplayName(),
                "Bước giá " + MoneyFormat.format(auction.minIncrement()),
                "Bắt đầu " + TimeFormat.dateTime(auction.startsAt())
                        + "   Kết thúc " + TimeFormat.dateTime(auction.endsAt())),
                null, AuctionRow.buttons(approve, reject), a -> openAuction.accept(a.auctionId()));
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private void approve(AuctionView auction, JButton approve, JButton reject) {
        approve.setEnabled(false);
        reject.setEnabled(false);
        connection.call(MessageType.APPROVE_AUCTION, new AuctionIdPayload(auction.auctionId()), (Type) Void.class)
                .whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        messageLabel.setText(MainFrame.describeError(error));
                        approve.setEnabled(true);
                        reject.setEnabled(true);
                        return;
                    }
                    messageLabel.setText("Đã duyệt phiên \"" + auction.title() + "\".");
                    refreshKeepingMessage();
                }));
    }

    private void reject(AuctionView auction) {
        String reason = askReason(auction);
        if (reason == null) {
            return;
        }
        connection.call(MessageType.REJECT_AUCTION, new RejectAuctionPayload(auction.auctionId(), reason),
                        (Type) Void.class)
                .whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        messageLabel.setText("Không từ chối được: " + MainFrame.describeError(error));
                        return;
                    }
                    messageLabel.setText("Đã từ chối phiên \"" + auction.title() + "\".");
                    refreshKeepingMessage();
                }));
    }

    /** Tải lại danh sách nhưng giữ câu thông báo vừa hiện. */
    private void refreshKeepingMessage() {
        String message = messageLabel.getText();
        int seq = ++loadSeq;
        connection.<List<AuctionView>>call(MessageType.GET_PENDING_AUCTIONS, null, LIST_OF_AUCTIONS)
                .whenComplete((items, error) -> SwingUtilities.invokeLater(() -> {
                    if (seq != loadSeq || error != null) {
                        return;
                    }
                    render(items == null ? List.of() : items);
                    messageLabel.setText(message);
                }));
    }

    /** Hộp nhập lý do từ chối; trả về lý do hợp lệ hoặc {@code null} nếu hủy. */
    private String askReason(AuctionView auction) {
        JTextArea area = new JTextArea(5, 34);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        UiTheme.field(area);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(380, 120));
        JLabel hint = UiTheme.muted("Lý do bắt buộc, 1–" + MAX_REASON + " ký tự. Người đăng sẽ thấy lý do này.");
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(hint, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, panel, "Từ chối \"" + auction.title() + "\"",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
            String reason = area.getText().strip();
            if (!reason.isEmpty() && reason.length() <= MAX_REASON) {
                return reason;
            }
            hint.setText("Hãy nhập lý do (1–" + MAX_REASON + " ký tự).");
            hint.setForeground(UiTheme.RUBY);
        }
    }
}
