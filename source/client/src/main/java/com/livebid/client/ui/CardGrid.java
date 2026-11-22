package com.livebid.client.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * Lưới thẻ tự chia cột theo bề rộng cửa sổ (giống {@code .grid} trên web): mỗi thẻ rộng ít nhất
 * {@value #MIN_CARD_WIDTH}px, các thẻ trong cùng hàng cao bằng nhau. Đặt trong {@code JScrollPane}.
 */
final class CardGrid extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    private static final int MIN_CARD_WIDTH = 290;
    private static final int GAP = 20;

    CardGrid() {
        super(null);
        setOpaque(false);
        setLayout(new GridLayoutManager());
    }

    private static final class GridLayoutManager implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        private static int columns(int width) {
            return Math.max(1, (width + GAP) / (MIN_CARD_WIDTH + GAP));
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int width = parent.getWidth() > 0 ? parent.getWidth() : 900;
            return new Dimension(width, layoutRows(parent, width, false));
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(MIN_CARD_WIDTH, 0);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            layoutRows(parent, parent.getWidth() - in.left - in.right, true);
        }

        private int layoutRows(Container parent, int width, boolean apply) {
            Insets in = parent.getInsets();
            int count = parent.getComponentCount();
            int cols = columns(width);
            int cardWidth = (width - (cols - 1) * GAP) / cols;
            int y = in.top;
            for (int start = 0; start < count; start += cols) {
                int rowHeight = 0;
                int end = Math.min(count, start + cols);
                for (int i = start; i < end; i++) {
                    rowHeight = Math.max(rowHeight, parent.getComponent(i).getPreferredSize().height);
                }
                if (apply) {
                    for (int i = start; i < end; i++) {
                        int x = in.left + (i - start) * (cardWidth + GAP);
                        parent.getComponent(i).setBounds(x, y, cardWidth, rowHeight);
                    }
                }
                y += rowHeight + GAP;
            }
            return count == 0 ? in.top + in.bottom : y - GAP + in.bottom;
        }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(visibleRect.height - 40, 40);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
