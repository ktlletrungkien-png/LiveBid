package com.livebid.client.ui;

import com.livebid.common.dto.AuctionStatus;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.JTextComponent;

/**
 * Giao diện dùng chung của desktop client, <b>cùng bảng màu và phong cách với trang web</b>
 * ({@code source/web/style.css}, {@code DESIGN.md}): chàm {@code #533afd} cho nút chính, navy cho khối giá,
 * nút dạng pill, thẻ bo góc viền mảnh, tag trạng thái dạng pill.
 *
 * <p>Gọi {@link #install()} một lần trên EDT, trước khi tạo cửa sổ. Lớp này chỉ đổi cách hiển thị, không
 * chạm logic mạng hay trạng thái phòng.
 */
public final class UiTheme {

    // ---- bảng màu (khớp :root trong style.css) ----
    public static final Color PRIMARY = new Color(0x533AFD);
    public static final Color PRIMARY_DEEP = new Color(0x4434D4);
    public static final Color PRIMARY_PRESS = new Color(0x2E2B8C);
    public static final Color PRIMARY_SUBDUED = new Color(0xB9B9F9);
    public static final Color BRAND_DARK = new Color(0x1C1E54);
    public static final Color INK = new Color(0x0D253D);
    public static final Color INK_SECONDARY = new Color(0x273951);
    public static final Color INK_MUTE = new Color(0x64748D);
    public static final Color CANVAS = Color.WHITE;
    public static final Color CANVAS_SOFT = new Color(0xF6F9FC);
    public static final Color HAIRLINE = new Color(0xE3E8EE);
    public static final Color HAIRLINE_INPUT = new Color(0xA8C3DE);
    public static final Color RUBY = new Color(0xEA2261);
    public static final Color SUCCESS = new Color(0x15803D);
    public static final Color WARNING = new Color(0x9B6829);
    public static final Color ON_DARK_MUTE = new Color(255, 255, 255, 185);

    private static final String FONT_FAMILY = "Segoe UI";

    private UiTheme() {
    }

    // ---- cài đặt ----

    /** Đặt look-and-feel Metal với bảng màu và font của LiveBid. */
    public static void install() {
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (javax.swing.UnsupportedLookAndFeelException e) {
            return;
        }
        FontUIResource body = new FontUIResource(sans(Font.PLAIN, 14f));
        for (String key : new String[] {"Label.font", "Button.font", "TextField.font", "PasswordField.font",
                "TextArea.font", "ComboBox.font", "Table.font", "TableHeader.font", "Spinner.font",
                "FormattedTextField.font", "ToolTip.font", "OptionPane.messageFont", "OptionPane.buttonFont",
                "MenuItem.font", "PopupMenu.font", "TabbedPane.font", "TitledBorder.font"}) {
            UIManager.put(key, body);
        }
        color("Panel.background", CANVAS_SOFT);
        color("Viewport.background", CANVAS_SOFT);
        color("OptionPane.background", CANVAS);
        color("Label.foreground", INK);
        color("TextField.background", CANVAS);
        color("TextField.foreground", INK);
        color("TextField.caretForeground", INK);
        color("PasswordField.background", CANVAS);
        color("TextArea.background", CANVAS);
        color("TextArea.foreground", INK);
        color("TextField.selectionBackground", PRIMARY_SUBDUED);
        color("TextField.selectionForeground", INK);
        color("TextArea.selectionBackground", PRIMARY_SUBDUED);
        color("TextArea.selectionForeground", INK);
        color("Table.background", CANVAS);
        color("Table.foreground", INK);
        color("Table.gridColor", HAIRLINE);
        color("Table.selectionBackground", new Color(0xECEBFF));
        color("Table.selectionForeground", INK);
        color("PopupMenu.background", CANVAS);
        color("MenuItem.background", CANVAS);
        color("MenuItem.foreground", INK);
        color("MenuItem.selectionBackground", CANVAS_SOFT);
        color("MenuItem.selectionForeground", INK);
        color("ToolTip.background", BRAND_DARK);
        color("ToolTip.foreground", Color.WHITE);
        color("ScrollBar.thumb", new Color(0xD3DBE5));
        color("ScrollBar.track", CANVAS_SOFT);
        color("ScrollBar.thumbShadow", new Color(0xD3DBE5));
        color("ScrollBar.thumbHighlight", new Color(0xD3DBE5));
        color("ScrollBar.thumbDarkShadow", new Color(0xD3DBE5));
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("TextField.border", fieldBorder(false));
        UIManager.put("PasswordField.border", fieldBorder(false));
        UIManager.put("PopupMenu.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(HAIRLINE), new EmptyBorder(4, 4, 4, 4)));
        UIManager.put("MenuItem.border", new EmptyBorder(8, 12, 8, 24));
    }

    private static void color(String key, Color value) {
        UIManager.put(key, new ColorUIResource(value));
    }

    // ---- font ----

    public static Font sans(int style, float size) {
        return new Font(FONT_FAMILY, style, 14).deriveFont(style, size);
    }

    /** Font cho tiền và đồng hồ: số cùng độ rộng, không nhảy khi đếm ngược. */
    public static Font money(float size) {
        return sans(Font.PLAIN, size);
    }

    // ---- thành phần dùng chung ----

    /** Thẻ trắng bo góc 12px, viền mảnh, giống class {@code .card} trên web. */
    public static Card card(LayoutManager layout, int padding) {
        Card card = new Card(CANVAS, HAIRLINE, 12);
        card.setLayout(layout);
        card.setBorder(new EmptyBorder(padding, padding, padding, padding));
        return card;
    }

    /** Khối giá nền navy, giống {@code .card-featured}. */
    public static Card featuredCard(LayoutManager layout, int padding) {
        Card card = new Card(BRAND_DARK, BRAND_DARK, 12);
        card.setLayout(layout);
        card.setBorder(new EmptyBorder(padding, padding, padding, padding));
        return card;
    }

    public static JButton primary(String text) {
        return new PillButton(text, PillButton.Kind.PRIMARY);
    }

    public static JButton secondary(String text) {
        return new PillButton(text, PillButton.Kind.SECONDARY);
    }

    public static JButton danger(String text) {
        return new PillButton(text, PillButton.Kind.DANGER);
    }

    /** Nút pill dùng trên nền navy. */
    public static JButton onDark(String text) {
        return new PillButton(text, PillButton.Kind.ON_DARK);
    }

    public static JToggleButton pillToggle(String text) {
        return new PillToggle(text);
    }

    /** Tiêu đề màn hình (28px, weight vừa). */
    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(sans(Font.BOLD, 24f));
        label.setForeground(INK);
        return label;
    }

    /** Chữ phụ nhỏ, màu {@link #INK_SECONDARY}. */
    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(sans(Font.PLAIN, 13f));
        label.setForeground(INK_SECONDARY);
        label.setBorder(new EmptyBorder(0, 0, 0, 4)); // chừa chỗ để chữ cuối không bị cắt
        return label;
    }

    public static JLabel money(String text, float size) {
        JLabel label = new JLabel(text);
        label.setFont(money(size));
        label.setForeground(INK);
        return label;
    }

    /** Viền ô nhập: viền xanh nhạt, chuyển sang chàm khi có tiêu điểm. */
    public static Border fieldBorder(boolean focused) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(focused ? PRIMARY : HAIRLINE_INPUT),
                new EmptyBorder(7, 10, 7, 10));
    }

    public static <T extends JTextComponent> T field(T component) {
        component.setBorder(fieldBorder(false));
        component.setFont(sans(Font.PLAIN, 14f));
        component.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                component.setBorder(fieldBorder(true));
            }

            @Override
            public void focusLost(FocusEvent e) {
                component.setBorder(fieldBorder(false));
            }
        });
        return component;
    }

    /** Bảng phẳng: đường kẻ ngang mảnh, tiêu đề chữ nhỏ màu phụ, hàng cao hơn. */
    public static void table(JTable table) {
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setGridColor(HAIRLINE);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(CANVAS);
        table.getTableHeader().setPreferredSize(new Dimension(100, 34));
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                    boolean focus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, false, false, row, column);
                label.setFont(sans(Font.PLAIN, 12f));
                label.setForeground(INK_SECONDARY);
                label.setBackground(CANVAS);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, HAIRLINE), new EmptyBorder(0, 12, 0, 12)));
                return label;
            }
        });
        DefaultTableCellRenderer cell = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                    boolean focus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(t, value, selected, false, row, column);
                label.setBorder(new EmptyBorder(0, 12, 0, 12));
                return label;
            }
        };
        table.setDefaultRenderer(Object.class, cell);
        table.setDefaultRenderer(String.class, cell);
    }

    // ---- tag trạng thái ----

    /** Các kiểu tag, tương ứng {@code .tag}, {@code .tag-live}, {@code .tag-done}... trên web. */
    public enum Tone {
        SOFT(PRIMARY_SUBDUED, PRIMARY_DEEP),
        LIVE(RUBY, Color.WHITE),
        DONE(HAIRLINE, INK_MUTE),
        WARN(new Color(0xF1E7DA), WARNING),
        REJECT(new Color(0xFBDDE7), RUBY),
        WIN(new Color(0xD9EEDF), SUCCESS);

        final Color background;
        final Color foreground;

        Tone(Color background, Color foreground) {
            this.background = background;
            this.foreground = foreground;
        }
    }

    public static JLabel tag(String text, Tone tone) {
        return new Tag(text.toUpperCase(java.util.Locale.ROOT), tone);
    }

    public static JLabel statusTag(AuctionStatus status) {
        Tone tone = switch (status == null ? AuctionStatus.FINISHED : status) {
            case ACTIVE -> Tone.LIVE;
            case SCHEDULED -> Tone.SOFT;
            case PENDING_APPROVAL -> Tone.WARN;
            case REJECTED -> Tone.REJECT;
            case FINISHED -> Tone.DONE;
        };
        return tag(MainFrame.statusText(status), tone);
    }

    // ---- các lớp vẽ ----

    /** Panel bo góc có viền mảnh. */
    public static class Card extends JPanel {
        private static final long serialVersionUID = 1L;

        private final Color fill;
        private final Color line;
        private final int arc;

        Card(Color fill, Color line, int arc) {
            this.fill = fill;
            this.line = line;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc * 2, arc * 2);
                g2.setColor(line);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc * 2, arc * 2);
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    /** Nhãn dạng pill (chữ in hoa nhỏ). */
    private static final class Tag extends JLabel {
        private static final long serialVersionUID = 1L;

        private final Tone tone;

        Tag(String text, Tone tone) {
            super(text);
            this.tone = tone;
            setFont(sans(Font.BOLD, 10.5f));
            setForeground(tone.foreground);
            setBorder(new EmptyBorder(4, 10, 4, 10));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(tone.background);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    /** Nút pill: chính (chàm), phụ (viền chàm), nguy hiểm (viền đỏ), trên nền tối. */
    public static final class PillButton extends JButton {
        private static final long serialVersionUID = 1L;

        enum Kind { PRIMARY, SECONDARY, DANGER, ON_DARK }

        private final Kind kind;
        private boolean hover;

        PillButton(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(sans(Font.PLAIN, 14f));
            setBorder(new EmptyBorder(10, 18, 10, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, 38));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                boolean on = isEnabled();
                boolean active = on && (hover || getModel().isPressed());
                Color fill;
                Color text;
                Color line = null;
                switch (kind) {
                    case PRIMARY -> {
                        fill = active ? PRIMARY_PRESS : PRIMARY;
                        text = Color.WHITE;
                    }
                    case ON_DARK -> {
                        fill = active ? PRIMARY_PRESS : PRIMARY;
                        text = Color.WHITE;
                        line = new Color(255, 255, 255, 90);
                    }
                    case DANGER -> {
                        fill = active ? new Color(0xFDEBF1) : CANVAS;
                        text = RUBY;
                        line = RUBY;
                    }
                    default -> {
                        fill = active ? new Color(0xF3F2FF) : CANVAS;
                        text = PRIMARY;
                        line = PRIMARY;
                    }
                }
                if (!on) {
                    g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(0.5f));
                }
                int w = getWidth();
                int h = getHeight();
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
                if (line != null) {
                    g2.setColor(line);
                    g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
                }
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                String label = getText() == null ? "" : getText();
                g2.setColor(text);
                g2.drawString(label, (w - fm.stringWidth(label)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    /** Bộ lọc dạng pill: được chọn thì nền chàm nhạt, chữ chàm đậm. */
    public static final class PillToggle extends JToggleButton {
        private static final long serialVersionUID = 1L;

        PillToggle(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(sans(Font.PLAIN, 14f));
            setBorder(new EmptyBorder(8, 16, 8, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                boolean selected = isSelected();
                int w = getWidth();
                int h = getHeight();
                g2.setColor(selected ? PRIMARY_SUBDUED : CANVAS);
                g2.fillRoundRect(0, 0, w - 1, h - 1, h, h);
                g2.setColor(selected ? PRIMARY_SUBDUED : HAIRLINE);
                g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
                g2.setFont(sans(selected ? Font.BOLD : Font.PLAIN, 14f));
                FontMetrics fm = g2.getFontMetrics();
                String label = getText() == null ? "" : getText();
                g2.setColor(selected ? PRIMARY_DEEP : INK_SECONDARY);
                g2.drawString(label, (w - fm.stringWidth(label)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(sans(Font.BOLD, 14f));
            return new Dimension(fm.stringWidth(getText()) + 34, 36);
        }
    }
}
