package com.livebid.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.metal.MetalButtonUI;

/**
 * Vẽ {@link JButton} theo giao diện LiveBid: bo góc, nền sáng viền mảnh; nút chính
 * ({@link UiTheme#primary}) nền xanh rêu chữ trắng. Di chuột qua thì đổi màu như trang web.
 *
 * <p>Chỉ áp cho {@code JButton} thường. Các nút "con" do Swing tự tạo (mũi tên của combo box, thanh cuộn,
 * spinner) là lớp con của {@code JButton} và tự vẽ, nên vẫn dùng {@link MetalButtonUI}.
 */
public final class RoundButtonUI extends BasicButtonUI {

    private static final RoundButtonUI SHARED = new RoundButtonUI();
    private static final int ARC = 10;
    private static final int MIN_HEIGHT = 34;

    /** Swing gọi qua UIManager ("ButtonUI"). */
    public static ComponentUI createUI(JComponent c) {
        return c.getClass() == JButton.class ? SHARED : MetalButtonUI.createUI(c);
    }

    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        if (b.getBorder() == null || b.getBorder() instanceof UIResource) {
            b.setBorder(new BorderUIResource(new EmptyBorder(7, 16, 7, 16)));
        }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        if (b.isContentAreaFilled()) {
            paintBackground(g, b);
        }
        super.paint(g, c);
    }

    private static void paintBackground(Graphics g, AbstractButton b) {
        ButtonModel model = b.getModel();
        boolean primary = isPrimary(b);
        boolean active = model.isRollover() || model.isPressed();
        Color fill;
        Color line;
        if (!b.isEnabled()) {
            fill = primary ? blend(UiTheme.PRIMARY, UiTheme.BG, 0.45f) : UiTheme.SURFACE;
            line = primary ? fill : UiTheme.BORDER;
        } else if (primary) {
            fill = active ? UiTheme.PRIMARY_HOVER : UiTheme.PRIMARY;
            line = fill;
        } else {
            fill = model.isPressed() ? UiTheme.SURFACE_2 : UiTheme.SURFACE;
            line = active ? UiTheme.PRIMARY : UiTheme.BORDER;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = b.getWidth();
        int h = b.getHeight();
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
        g2.setColor(line);
        g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
        if (b.isFocusOwner() && b.isFocusPainted()) {
            // Viền vàng đồng khi chọn bằng bàn phím, giống :focus-visible trên web.
            g2.setColor(UiTheme.GOLD);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, ARC, ARC);
        }
        g2.dispose();
    }

    @Override
    protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
        boolean primary = isPrimary(b);
        Color color;
        if (!b.isEnabled()) {
            color = primary ? new Color(255, 255, 255, 210) : UiTheme.MUTED;
        } else if (!primary && b.isContentAreaFilled() && b.getModel().isRollover()) {
            // Nút trong suốt (ví dụ nút tài khoản trên nền tối) giữ màu chữ của nó khi di chuột.
            color = UiTheme.PRIMARY;
        } else {
            color = b.getForeground();
        }
        FontMetrics metrics = b.getFontMetrics(b.getFont());
        g.setColor(color);
        BasicGraphicsUtils.drawStringUnderlineCharAt(b, (Graphics2D) g, text, b.getDisplayedMnemonicIndex(),
                textRect.x + getTextShiftOffset(), textRect.y + metrics.getAscent() + getTextShiftOffset());
    }

    @Override
    protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
        // Viền focus đã vẽ trong paintBackground.
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        Dimension size = super.getPreferredSize(c);
        if (size != null) {
            size.height = Math.max(size.height, MIN_HEIGHT);
        }
        return size;
    }

    private static boolean isPrimary(AbstractButton b) {
        return Boolean.TRUE.equals(b.getClientProperty(UiTheme.PRIMARY_BUTTON));
    }

    private static Color blend(Color a, Color b, float ratio) {
        float r = 1f - ratio;
        return new Color(
                Math.round(a.getRed() * r + b.getRed() * ratio),
                Math.round(a.getGreen() * r + b.getGreen() * ratio),
                Math.round(a.getBlue() * r + b.getBlue() * ratio));
    }
}
