package com.livebid.client.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;

/**
 * Giao diện dùng chung của desktop client: bảng màu, font và cách vẽ các thành phần Swing.
 *
 * <p>Bảng màu và cặp font <b>giống hệt trang web</b> ({@code source/web/style.css}) để hai bên thống nhất:
 * <ul>
 *   <li>Playfair Display: tiêu đề (chữ số của font này là kiểu old-style nên không dùng cho giá).</li>
 *   <li>Be Vietnam Pro: mọi chữ còn lại và mọi con số.</li>
 * </ul>
 * Font nằm trong tài nguyên {@code /fonts} (giấy phép SIL OFL, xem {@code fonts/OFL-*.txt}).
 *
 * <p>Gọi {@link #install()} một lần trên EDT, <b>trước khi</b> tạo cửa sổ. Lớp này chỉ đổi cách hiển thị,
 * không chạm logic mạng hay trạng thái phòng.
 */
public final class UiTheme {

    private static final System.Logger LOG = System.getLogger(UiTheme.class.getName());

    // ------------------------------------------------------------------ bảng màu (khớp :root trong style.css)

    public static final Color BG = new Color(0xF5F1E8);
    public static final Color SURFACE = new Color(0xFFFDF8);
    public static final Color SURFACE_2 = new Color(0xEFE9DC);
    public static final Color TEXT = new Color(0x1F1D19);
    public static final Color MUTED = new Color(0x6B6558);
    public static final Color BORDER = new Color(0xE2DACB);
    public static final Color INK = new Color(0x14231D);
    public static final Color INK_TEXT = new Color(0xEDE6D6);
    public static final Color INK_MUTED = new Color(0xA9B5AE);
    public static final Color INK_GOLD = new Color(0xD9AE5F);
    public static final Color PRIMARY = new Color(0x1F5A47);
    public static final Color PRIMARY_HOVER = new Color(0x17483A);
    public static final Color PRIMARY_TEXT = Color.WHITE;
    public static final Color GOLD = new Color(0x9A6F22);
    public static final Color GOLD_SOFT = new Color(0xF4E8CC);
    public static final Color OK = new Color(0x2E7D57);
    public static final Color WARN = new Color(0xA86412);
    public static final Color ERROR = new Color(0xB42318);

    /** Đánh dấu nút chính (nền xanh rêu) cho {@link RoundButtonUI}. */
    static final String PRIMARY_BUTTON = "livebid.primaryButton";

    private static final float BODY_SIZE = 14f;

    private static Font sansRegular = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static Font sansSemiBold = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static Font sansBold = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static Font display = new Font(Font.SERIF, Font.PLAIN, 14);

    private UiTheme() {
    }

    // ------------------------------------------------------------------ cài đặt

    /** Nạp font, đặt look-and-feel Metal với bảng màu LiveBid và các mặc định của UIManager. */
    public static void install() {
        loadFonts();
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        MetalLookAndFeel.setCurrentTheme(new LiveBidMetalTheme());
        try {
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (UnsupportedLookAndFeelException e) {
            LOG.log(System.Logger.Level.WARNING, "Không đặt được giao diện Metal, dùng mặc định: " + e.getMessage());
            return;
        }
        applyDefaults(UIManager.getDefaults());
    }

    /** Font chữ thường (Be Vietnam Pro) cỡ {@code size}; {@code weight}: 400, 600 hoặc 700. */
    public static Font sans(int weight, float size) {
        Font base = weight >= 700 ? sansBold : weight >= 600 ? sansSemiBold : sansRegular;
        return base.deriveFont(size);
    }

    /** Font tiêu đề (Playfair Display), in đậm. */
    public static Font display(float size) {
        return display.deriveFont(Font.BOLD, size);
    }

    /** Font cho số tiền, đồng hồ: Be Vietnam Pro đậm (chữ số đều nét, dễ đọc). */
    public static Font money(float size) {
        return sans(700, size);
    }

    // ------------------------------------------------------------------ tiện ích cho các màn hình

    /** Tiêu đề màn hình. */
    public static void title(JLabel label, float size) {
        label.setFont(display(size));
        label.setForeground(TEXT);
    }

    /** Nhãn phụ: chữ nhỏ in hoa, màu nhạt (giống h3 trên web). */
    public static void caption(JLabel label) {
        label.setText(label.getText().toUpperCase(java.util.Locale.ROOT));
        label.setFont(sans(700, 11.5f));
        label.setForeground(MUTED);
    }

    /** Nút chính: nền xanh rêu, chữ trắng. */
    public static void primary(AbstractButton button) {
        button.putClientProperty(PRIMARY_BUTTON, Boolean.TRUE);
        button.setForeground(PRIMARY_TEXT);
        button.setFont(sans(700, BODY_SIZE));
    }

    /** Bảng: dòng cao, chỉ kẻ ngang, đầu bảng chữ in hoa nhỏ. */
    public static void table(JTable table) {
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setPreferredSize(new Dimension(0, 36));
        // Tiêu đề cột căn trái như bảng trên web (mặc định của Swing là căn giữa).
        if (table.getTableHeader().getDefaultRenderer() instanceof javax.swing.table.DefaultTableCellRenderer header) {
            header.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        }
    }

    /**
     * Khung "thẻ": viền mảnh, có lề trong. Góc vuông vì nền của JPanel Swing luôn là hình chữ nhật;
     * bo viền mà nền không bo sẽ lộ góc.
     */
    public static Border card(int padding) {
        return new CompoundBorder(new RoundedLineBorder(BORDER, 0), new EmptyBorder(padding, padding, padding, padding));
    }

    /**
     * Thanh thương hiệu trên cùng cửa sổ, giống đầu trang web. Thương hiệu nằm ở {@code BorderLayout.WEST};
     * chỗ {@code BorderLayout.EAST} để trống cho nút tài khoản.
     */
    public static JPanel brandBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(INK);
        bar.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, INK_GOLD),
                new EmptyBorder(8, 18, 6, 14)));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brand.setOpaque(false);
        JLabel name = new JLabel("LiveBid");
        name.setFont(display(22f));
        name.setForeground(INK_TEXT);
        JLabel dot = new JLabel(".");
        dot.setFont(display(22f));
        dot.setForeground(INK_GOLD);
        brand.add(name);
        brand.add(dot);
        bar.add(brand, BorderLayout.WEST);
        return bar;
    }

    /**
     * Chữ cái đầu cho ảnh đại diện, cùng quy tắc với trang web: chữ cái đầu tiên của hai từ cuối có chữ
     * ("Cao Công Hoè" → "CH", "Hoè (Desktop)" → "HD"). Không có chữ nào thì "?".
     */
    public static String initials(String name) {
        List<Integer> letters = new ArrayList<>();
        for (String word : name == null ? new String[0] : name.trim().split("\\s+")) {
            word.codePoints().filter(Character::isLetter).findFirst().ifPresent(letters::add);
        }
        StringBuilder out = new StringBuilder();
        letters.subList(Math.max(0, letters.size() - 2), letters.size()).forEach(out::appendCodePoint);
        return out.isEmpty() ? "?" : out.toString().toUpperCase(java.util.Locale.ROOT);
    }

    /** Ảnh đại diện tròn màu vàng đồng có chữ cái đầu, cỡ {@code size} điểm ảnh. */
    public static javax.swing.Icon avatarIcon(String name, int size) {
        String text = initials(name);
        return new javax.swing.Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(INK_GOLD);
                g2.fillOval(x, y, size, size);
                g2.setColor(INK);
                g2.setFont(sans(700, size * 0.38f));
                java.awt.FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, x + (size - fm.stringWidth(text)) / 2,
                        y + (size - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Nút tài khoản trên thanh thương hiệu: ảnh đại diện + tên, nền trong suốt. Tắt HTML để tên người dùng
     * kiểu {@code <html>...} chỉ hiện như chữ.
     */
    public static void accountButton(AbstractButton button) {
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setForeground(INK_TEXT);
        button.setFont(sans(600, BODY_SIZE));
        button.setBorder(new EmptyBorder(3, 4, 3, 8));
        button.setIconTextGap(10);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.putClientProperty("html.disable", Boolean.TRUE);
    }

    // ------------------------------------------------------------------ nội bộ

    private static void loadFonts() {
        sansRegular = loadFont("BeVietnamPro-Regular.ttf", sansRegular);
        sansSemiBold = loadFont("BeVietnamPro-SemiBold.ttf", sansSemiBold);
        sansBold = loadFont("BeVietnamPro-Bold.ttf", sansBold);
        display = loadFont("PlayfairDisplay-Variable.ttf", display);
    }

    private static Font loadFont(String file, Font fallback) {
        try (InputStream in = UiTheme.class.getResourceAsStream("/fonts/" + file)) {
            if (in == null) {
                LOG.log(System.Logger.Level.WARNING, "Thiếu font " + file + ", dùng font hệ thống");
                return fallback;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font.deriveFont(BODY_SIZE);
        } catch (IOException | FontFormatException e) {
            LOG.log(System.Logger.Level.WARNING, "Không nạp được font " + file + ": " + e.getMessage());
            return fallback;
        }
    }

    private static void applyDefaults(UIDefaults d) {
        // Mọi thành phần dùng Be Vietnam Pro.
        FontUIResource body = new FontUIResource(sans(400, BODY_SIZE));
        List<Object> fontKeys = new ArrayList<>();
        for (Object key : d.keySet()) {
            if (key instanceof String name && name.endsWith(".font")) {
                fontKeys.add(key);
            }
        }
        fontKeys.forEach(key -> d.put(key, body));

        ColorUIResource bg = new ColorUIResource(BG);
        ColorUIResource surface = new ColorUIResource(SURFACE);
        ColorUIResource surface2 = new ColorUIResource(SURFACE_2);
        ColorUIResource text = new ColorUIResource(TEXT);
        ColorUIResource muted = new ColorUIResource(MUTED);
        ColorUIResource border = new ColorUIResource(BORDER);
        ColorUIResource goldSoft = new ColorUIResource(GOLD_SOFT);
        ColorUIResource primary = new ColorUIResource(PRIMARY);

        d.put("Panel.background", bg);
        d.put("OptionPane.background", bg);
        d.put("Label.foreground", text);
        d.put("Label.disabledForeground", muted);
        d.put("ToolTip.background", new ColorUIResource(INK));
        d.put("ToolTip.foreground", new ColorUIResource(INK_TEXT));
        d.put("ToolTip.border", new BorderUIResource(new EmptyBorder(6, 10, 6, 10)));

        // Nút: vẽ bằng RoundButtonUI (bo góc, nút chính xanh rêu).
        d.put("ButtonUI", RoundButtonUI.class.getName());
        d.put(RoundButtonUI.class.getName(), RoundButtonUI.class);
        d.put("Button.font", new FontUIResource(sans(600, BODY_SIZE)));
        d.put("Button.foreground", text);
        d.put("Button.background", surface);
        d.put("Button.disabledText", muted);

        // Ô nhập.
        Border field = new BorderUIResource(new CompoundBorder(new FocusLineBorder(), new EmptyBorder(6, 10, 6, 10)));
        for (String prefix : new String[] {"TextField", "PasswordField", "FormattedTextField"}) {
            d.put(prefix + ".background", surface);
            d.put(prefix + ".foreground", text);
            d.put(prefix + ".inactiveBackground", surface2);
            d.put(prefix + ".selectionBackground", goldSoft);
            d.put(prefix + ".selectionForeground", text);
            d.put(prefix + ".caretForeground", text);
            d.put(prefix + ".border", field);
        }
        d.put("TextArea.background", surface);
        d.put("TextArea.foreground", text);
        d.put("TextArea.selectionBackground", goldSoft);
        d.put("TextArea.selectionForeground", text);
        d.put("TextArea.margin", new InsetsUIResource(6, 8, 6, 8));
        d.put("Spinner.border", new BorderUIResource(new RoundedLineBorder(BORDER, 0)));
        d.put("Spinner.background", surface);
        d.put("ComboBox.background", surface);
        d.put("ComboBox.foreground", text);
        d.put("ComboBox.selectionBackground", goldSoft);
        d.put("ComboBox.selectionForeground", text);
        d.put("ComboBox.buttonBackground", surface);

        // Cuộn và bảng.
        d.put("ScrollPane.border", new BorderUIResource(new RoundedLineBorder(BORDER, 0)));
        // JTable tự gán viền này cho JScrollPane chứa nó (ghi đè ScrollPane.border).
        d.put("Table.scrollPaneBorder", new BorderUIResource(new RoundedLineBorder(BORDER, 0)));
        d.put("ScrollPane.background", bg);
        d.put("Viewport.background", surface);
        d.put("Table.background", surface);
        d.put("Table.foreground", text);
        d.put("Table.gridColor", border);
        d.put("Table.selectionBackground", goldSoft);
        d.put("Table.selectionForeground", text);
        Border cell = new BorderUIResource(new EmptyBorder(0, 12, 0, 12));
        d.put("Table.cellNoFocusBorder", cell);
        d.put("Table.focusCellHighlightBorder", cell);
        d.put("TableHeader.background", surface2);
        d.put("TableHeader.foreground", muted);
        d.put("TableHeader.font", new FontUIResource(sans(700, 12f)));
        d.put("TableHeader.cellBorder", new BorderUIResource(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(0, 12, 0, 12))));

        // Tab: gạch chân vàng đồng như thanh điều hướng web.
        d.put("TabbedPaneUI", UnderlineTabbedPaneUI.class.getName());
        d.put(UnderlineTabbedPaneUI.class.getName(), UnderlineTabbedPaneUI.class);
        d.put("TabbedPane.font", new FontUIResource(sans(600, BODY_SIZE)));
        d.put("TabbedPane.foreground", text);
        d.put("TabbedPane.background", bg);
        d.put("TabbedPane.contentOpaque", Boolean.FALSE);
        d.put("TabbedPane.tabsOpaque", Boolean.FALSE);

        // Khung có tiêu đề (ví dụ "Lịch sử đặt giá").
        d.put("TitledBorder.font", new FontUIResource(sans(700, 12f)));
        d.put("TitledBorder.titleColor", muted);
        d.put("TitledBorder.border", new BorderUIResource(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER)));

        // Menu bật lên (menu tài khoản): nền sáng, viền mảnh, dòng đang chọn tô nền phụ.
        d.put("PopupMenu.background", surface);
        d.put("PopupMenu.border", new BorderUIResource(new CompoundBorder(new RoundedLineBorder(BORDER, 0),
                new EmptyBorder(6, 6, 6, 6))));
        for (String prefix : new String[] {"MenuItem", "Menu"}) {
            d.put(prefix + ".background", surface);
            d.put(prefix + ".foreground", text);
            d.put(prefix + ".selectionBackground", surface2);
            d.put(prefix + ".selectionForeground", text);
            d.put(prefix + ".font", new FontUIResource(sans(600, BODY_SIZE)));
            d.put(prefix + ".margin", new InsetsUIResource(8, 6, 8, 12));
        }
        d.put("Separator.background", surface);

        // Tick chọn, thanh tiến trình... dùng màu chính.
        d.put("CheckBox.background", bg);
        d.put("ProgressBar.foreground", primary);
        d.put("Separator.foreground", border);
    }

    /** Bảng màu Metal: thay các màu xanh mặc định của Java bằng màu LiveBid. */
    private static final class LiveBidMetalTheme extends DefaultMetalTheme {

        @Override
        public String getName() {
            return "LiveBid";
        }

        @Override
        protected ColorUIResource getPrimary1() {
            return new ColorUIResource(PRIMARY);
        }

        @Override
        protected ColorUIResource getPrimary2() {
            return new ColorUIResource(GOLD);
        }

        @Override
        protected ColorUIResource getPrimary3() {
            return new ColorUIResource(GOLD_SOFT);
        }

        @Override
        protected ColorUIResource getSecondary1() {
            return new ColorUIResource(MUTED);
        }

        @Override
        protected ColorUIResource getSecondary2() {
            return new ColorUIResource(BORDER);
        }

        @Override
        protected ColorUIResource getSecondary3() {
            return new ColorUIResource(BG);
        }

        @Override
        protected ColorUIResource getBlack() {
            return new ColorUIResource(TEXT);
        }

        @Override
        protected ColorUIResource getWhite() {
            return new ColorUIResource(SURFACE);
        }

        @Override
        public FontUIResource getControlTextFont() {
            return new FontUIResource(sans(600, BODY_SIZE));
        }

        @Override
        public FontUIResource getSystemTextFont() {
            return new FontUIResource(sans(400, BODY_SIZE));
        }

        @Override
        public FontUIResource getUserTextFont() {
            return new FontUIResource(sans(400, BODY_SIZE));
        }

        @Override
        public FontUIResource getMenuTextFont() {
            return new FontUIResource(sans(400, BODY_SIZE));
        }

        @Override
        public FontUIResource getWindowTitleFont() {
            return new FontUIResource(sans(700, BODY_SIZE));
        }

        @Override
        public FontUIResource getSubTextFont() {
            return new FontUIResource(sans(400, 12f));
        }
    }

    /** Viền mảnh bo góc; {@code radius} 0 = góc vuông. */
    static final class RoundedLineBorder extends AbstractBorder implements UIResource {

        private static final long serialVersionUID = 1L;

        private final Color color;
        private final int radius;

        RoundedLineBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
        }
    }

    /** Viền ô nhập: xám nhạt bình thường, xanh rêu dày hơn khi đang nhập. */
    static final class FocusLineBorder extends AbstractBorder implements UIResource {

        private static final long serialVersionUID = 1L;

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            boolean focused = c.hasFocus() && c.isEnabled();
            g2.setColor(focused ? PRIMARY : BORDER);
            g2.setStroke(new BasicStroke(focused ? 2f : 1f));
            int inset = focused ? 1 : 0;
            g2.drawRect(x + inset, y + inset, width - 1 - 2 * inset, height - 1 - 2 * inset);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(2, 2, 2, 2);
            return insets;
        }
    }

    /** Bao một thành phần trong khung "thẻ" nền sáng. */
    public static JPanel cardPanel(JComponent content, int padding) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SURFACE);
        panel.setBorder(card(padding));
        panel.add(content, BorderLayout.CENTER);
        content.setOpaque(false);
        return panel;
    }
}
