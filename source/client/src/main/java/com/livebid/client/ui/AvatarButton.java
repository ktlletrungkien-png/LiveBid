package com.livebid.client.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/**
 * Nút avatar tròn hiện chữ cái đầu của tên người dùng, giống avatar ở góc phải trang web.
 * Bấm vào để mở menu tài khoản (xem {@link MainFrame}).
 */
final class AvatarButton extends JButton {

    private static final long serialVersionUID = 1L;

    private static final Color FILL = new Color(0xB9, 0xB9, 0xF9);
    private static final Color TEXT = new Color(0x44, 0x34, 0xD4);
    private static final Color RING = new Color(0x53, 0x3A, 0xFD);
    private static final int SIZE = 38;

    AvatarButton() {
        setPreferredSize(new Dimension(SIZE, SIZE));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Tài khoản");
    }

    /** Đặt tên hiển thị; nút vẽ lại chữ cái đầu. */
    void setUserName(String displayName) {
        setText(initials(displayName));
        getAccessibleContext().setAccessibleName("Tài khoản: " + displayName);
        repaint();
    }

    /** Hai chữ cái đầu (chữ đầu của từ đầu và từ cuối), viết hoa; tên một từ thì lấy tối đa hai chữ cái. */
    static String initials(String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            return "?";
        }
        String[] words = name.split("\\s+");
        String result = words.length > 1
                ? words[0].substring(0, 1) + words[words.length - 1].substring(0, 1)
                : words[0].substring(0, Math.min(2, words[0].length()));
        return result.toUpperCase();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int d = Math.min(getWidth(), getHeight()) - 2;
            g2.setColor(FILL);
            g2.fillOval(1, 1, d, d);
            if (getModel().isRollover() || getModel().isPressed()) {
                g2.setColor(RING);
                g2.drawOval(1, 1, d, d);
            }
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            FontMetrics fm = g2.getFontMetrics();
            String text = getText() == null ? "" : getText();
            g2.setColor(TEXT);
            g2.drawString(text, 1 + (d - fm.stringWidth(text)) / 2, 1 + (d - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
