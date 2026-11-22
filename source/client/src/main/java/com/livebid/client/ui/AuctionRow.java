package com.livebid.client.ui;

import com.livebid.client.room.MoneyFormat;
import com.livebid.common.dto.AuctionView;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Một dòng dạng thẻ cho "Phiên của tôi" và hàng chờ duyệt của quản trị (giống {@code .card.item} trên web):
 * bên trái là nhãn, tên, mô tả ngắn; bên phải là giá và các nút thao tác.
 */
final class AuctionRow extends UiTheme.Card {

    private static final long serialVersionUID = 1L;

    /**
     * @param extraTag nhãn phụ cạnh nhãn trạng thái (ví dụ "Bạn đã thắng"); {@code null} thì bỏ qua
     * @param details  các dòng chữ phụ dưới tên (thời gian, người đăng...)
     * @param actions  hàng nút thao tác; {@code null} thì không có
     * @param open     bấm vào tên phiên để mở chi tiết; {@code null} thì chỉ là chữ
     */
    AuctionRow(AuctionView auction, JComponent extraTag, java.util.List<String> details, String warning,
            JComponent actions, Consumer<AuctionView> open) {
        super(UiTheme.CANVAS, UiTheme.HAIRLINE, 12);
        setLayout(new BorderLayout(24, 0));
        setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel tags = new JPanel();
        tags.setOpaque(false);
        tags.setLayout(new BoxLayout(tags, BoxLayout.X_AXIS));
        tags.setAlignmentX(LEFT_ALIGNMENT);
        tags.add(UiTheme.statusTag(auction.status()));
        if (extraTag != null) {
            tags.add(Box.createHorizontalStrut(6));
            tags.add(extraTag);
        }
        tags.setMaximumSize(new Dimension(Integer.MAX_VALUE, tags.getPreferredSize().height));
        left.add(tags);
        left.add(Box.createVerticalStrut(8));

        JLabel title = new JLabel(auction.title());
        title.setFont(UiTheme.sans(Font.BOLD, 16f));
        title.setForeground(open == null ? UiTheme.INK : UiTheme.INK);
        title.setAlignmentX(LEFT_ALIGNMENT);
        if (open != null) {
            title.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            title.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    open.accept(auction);
                }
            });
        }
        left.add(title);

        for (String line : details) {
            left.add(Box.createVerticalStrut(2));
            JLabel label = UiTheme.muted(line);
            label.setAlignmentX(LEFT_ALIGNMENT);
            left.add(label);
        }
        if (warning != null && !warning.isBlank()) {
            left.add(Box.createVerticalStrut(6));
            JLabel reason = new JLabel(warning);
            reason.setFont(UiTheme.sans(Font.PLAIN, 13f));
            reason.setForeground(UiTheme.RUBY);
            reason.setAlignmentX(LEFT_ALIGNMENT);
            left.add(reason);
        }
        add(left, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        JLabel caption = UiTheme.muted(auction.hasBids() ? "Giá hiện tại" : "Giá khởi điểm");
        caption.setAlignmentX(RIGHT_ALIGNMENT);
        JLabel price = UiTheme.money(MoneyFormat.format(auction.currentPrice()), 20f);
        price.setAlignmentX(RIGHT_ALIGNMENT);
        right.add(caption);
        right.add(price);
        if (actions != null) {
            right.add(Box.createVerticalStrut(10));
            actions.setAlignmentX(RIGHT_ALIGNMENT);
            right.add(actions);
        }
        right.setPreferredSize(new Dimension(Math.max(190, right.getPreferredSize().width),
                right.getPreferredSize().height));
        add(right, BorderLayout.EAST);
    }

    /** Hàng nút căn phải. */
    static JPanel buttons(JComponent... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setOpaque(false);
        for (JComponent component : components) {
            panel.add(component);
        }
        return panel;
    }
}
