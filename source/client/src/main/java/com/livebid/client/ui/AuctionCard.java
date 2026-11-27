package com.livebid.client.ui;

import com.livebid.client.image.ClientImages;
import com.livebid.client.room.MoneyFormat;
import com.livebid.common.dto.AuctionStatus;
import com.livebid.common.dto.AuctionView;
import com.livebid.common.dto.Role;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Thẻ một phiên trong lưới "Phiên đấu giá" (giống {@code .card.auction} trên web): ảnh đại diện, nhãn trạng thái, tên,
 * ban tổ chức hoặc người đăng, giá, đồng hồ đếm ngược và nút vào phòng.
 *
 * <p>Đồng hồ do {@link #tick(long)} cập nhật; người gọi truyền giờ server ước tính. Dưới 10 phút thì đồng hồ
 * chuyển sang màu đỏ.
 */
final class AuctionCard extends UiTheme.Card {

    private static final long serialVersionUID = 1L;

    private static final long URGENT_MILLIS = 10 * 60_000L;

    /** Chiều cao khung ảnh trên thẻ (thẻ rộng tối thiểu ~290px nên gần tỉ lệ 4:3 sau khi trừ lề). */
    private static final int THUMB_HEIGHT = 170;

    private final AuctionView auction;
    private final JLabel timerLabel = new JLabel();

    /**
     * @param edit   nút "Sửa phiên" (chỉ ADMIN, phiên SCHEDULED); {@code null} thì không hiện
     * @param images nguồn ảnh đại diện
     */
    AuctionCard(AuctionView auction, Consumer<AuctionView> open, Consumer<AuctionView> edit, Role role,
            ClientImages images) {
        super(UiTheme.CANVAS, UiTheme.HAIRLINE, 12);
        this.auction = auction;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        ImageThumb thumb = new ImageThumb(images, 250, THUMB_HEIGHT);
        thumb.show(auction.imageId());
        add(thumb);
        add(Box.createVerticalStrut(14));

        JPanel tagRow = row(new BorderLayout());
        tagRow.add(UiTheme.statusTag(auction.status()), BorderLayout.WEST);
        add(tagRow);
        add(Box.createVerticalStrut(12));

        JLabel title = new JLabel(auction.title());
        title.setFont(UiTheme.sans(Font.BOLD, 16f));
        title.setForeground(UiTheme.INK);
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(4));

        JLabel owner = UiTheme.muted(ownerName(auction));
        owner.setAlignmentX(LEFT_ALIGNMENT);
        add(owner);
        add(Box.createVerticalStrut(14));

        JLabel priceCaption = UiTheme.muted(auction.hasBids() ? "Giá hiện tại" : "Giá khởi điểm");
        priceCaption.setAlignmentX(LEFT_ALIGNMENT);
        add(priceCaption);
        JLabel price = UiTheme.money(MoneyFormat.format(auction.currentPrice()), 26f);
        price.setAlignmentX(LEFT_ALIGNMENT);
        add(price);
        add(Box.createVerticalStrut(12));

        JPanel meta = row(new BorderLayout());
        meta.add(UiTheme.muted(captionFor(auction.status())), BorderLayout.WEST);
        timerLabel.setFont(UiTheme.money(14f));
        meta.add(timerLabel, BorderLayout.EAST);
        add(meta);
        add(Box.createVerticalStrut(16));

        boolean live = auction.status() == AuctionStatus.ACTIVE;
        JButton enter = live ? UiTheme.primary("Vào phòng đấu giá") : UiTheme.secondary("Xem chi tiết");
        fullWidth(enter);
        enter.addActionListener(e -> open.accept(auction));
        add(enter);

        if (edit != null && role == Role.ADMIN && auction.status() == AuctionStatus.SCHEDULED) {
            add(Box.createVerticalStrut(8));
            JButton editButton = UiTheme.secondary("Sửa phiên");
            fullWidth(editButton);
            editButton.addActionListener(e -> edit.accept(auction));
            add(editButton);
        }
        tick(System.currentTimeMillis());
    }

    private static void fullWidth(JButton button) {
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
    }

    private static JPanel row(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return panel;
    }

    static String ownerName(AuctionView a) {
        return a.ownerRole() == Role.ADMIN ? "Ban tổ chức" : a.ownerDisplayName();
    }

    private static String captionFor(AuctionStatus status) {
        return switch (status) {
            case ACTIVE -> "Còn lại";
            case SCHEDULED -> "Bắt đầu sau";
            default -> "Kết thúc lúc";
        };
    }

    /** Cập nhật đồng hồ theo giờ server ước tính {@code serverNow} (epoch ms). */
    void tick(long serverNow) {
        if (auction.status() == AuctionStatus.ACTIVE || auction.status() == AuctionStatus.SCHEDULED) {
            long target = auction.status() == AuctionStatus.ACTIVE ? auction.endsAt() : auction.startsAt();
            long remaining = target - serverNow;
            timerLabel.setText(countdown(remaining));
            timerLabel.setForeground(remaining > 0 && remaining < URGENT_MILLIS ? UiTheme.RUBY : UiTheme.INK);
        } else {
            timerLabel.setText(TimeFormat.dateTime(auction.endsAt()));
            timerLabel.setForeground(UiTheme.INK_SECONDARY);
        }
    }

    /** {@code HH:mm:ss}, thêm "N ngày" phía trước khi còn từ một ngày trở lên. */
    static String countdown(long millis) {
        long total = Math.max(0, millis) / 1_000;
        long days = total / 86_400;
        String clock = String.format(Locale.ROOT, "%02d:%02d:%02d",
                (total % 86_400) / 3_600, (total % 3_600) / 60, total % 60);
        return days > 0 ? days + " ngày " + clock : clock;
    }
}
