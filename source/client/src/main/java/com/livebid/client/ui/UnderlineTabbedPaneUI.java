package com.livebid.client.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 * Tab phẳng kiểu web: không khung, tab đang chọn chữ đậm màu và có gạch chân vàng đồng, một đường kẻ mảnh
 * ngăn hàng tab với nội dung. Dùng cho tab đặt ở trên (cách client đang dùng).
 */
public final class UnderlineTabbedPaneUI extends BasicTabbedPaneUI {

    private static final int UNDERLINE = 3;

    /** Swing gọi qua UIManager ("TabbedPaneUI"). */
    public static ComponentUI createUI(JComponent c) {
        return new UnderlineTabbedPaneUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabInsets = new Insets(8, 16, 8, 16);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabAreaInsets = new Insets(0, 4, 0, 4);
        contentBorderInsets = new Insets(1, 0, 0, 0);
    }

    @Override
    protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h,
                                      boolean selected) {
        // Tab trong suốt.
    }

    @Override
    protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h,
                                  boolean selected) {
        if (selected) {
            g.setColor(UiTheme.GOLD);
            g.fillRect(x + 10, y + h - UNDERLINE, Math.max(0, w - 20), UNDERLINE);
        }
    }

    @Override
    protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int index,
                                       Rectangle iconRect, Rectangle textRect, boolean selected) {
        if (tabPane.hasFocus() && selected && textRect != null) {
            g.setColor(UiTheme.BORDER);
            g.drawRect(textRect.x - 4, textRect.y - 2, textRect.width + 8, textRect.height + 4);
        }
    }

    @Override
    protected void paintText(Graphics g, int placement, Font font, FontMetrics metrics, int index, String title,
                             Rectangle textRect, boolean selected) {
        Color color = !tabPane.isEnabledAt(index) || !selected ? UiTheme.MUTED : UiTheme.TEXT;
        g.setFont(font);
        g.setColor(color);
        BasicGraphicsUtils.drawStringUnderlineCharAt(tabPane, (Graphics2D) g, title,
                tabPane.getDisplayedMnemonicIndexAt(index), textRect.x, textRect.y + metrics.getAscent());
    }

    @Override
    protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
        Insets insets = tabPane.getInsets();
        int y = insets.top + calculateTabAreaHeight(placement, runCount, maxTabHeight);
        g.setColor(UiTheme.BORDER);
        g.fillRect(insets.left, y - 1, tabPane.getWidth() - insets.left - insets.right, 1);
    }

    @Override
    protected int getTabLabelShiftX(int placement, int index, boolean selected) {
        return 0;
    }

    @Override
    protected int getTabLabelShiftY(int placement, int index, boolean selected) {
        return 0;
    }
}
