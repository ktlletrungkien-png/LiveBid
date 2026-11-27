package com.livebid.client.ui;

import com.livebid.client.image.ClientImages;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.swing.JComponent;

/**
 * Khung ảnh đại diện của phiên (giống {@code .thumb} trên web): ảnh phủ kín khung và bị cắt phần thừa, bo
 * góc; không có ảnh, đang tải hoặc lỗi thì hiện nền nhạt kèm một dòng chữ.
 *
 * <p>Chiều cao cố định, chiều rộng giãn theo chỗ chứa. Chỉ dùng trên EDT. Ảnh về muộn của mã cũ (người
 * dùng đã đổi sang ảnh khác) bị bỏ qua.
 */
final class ImageThumb extends JComponent {

    private static final long serialVersionUID = 1L;

    private static final int ARC = 12;

    private final transient ClientImages images;
    private transient BufferedImage image;
    private String imageId;
    private String text = "Chưa có ảnh";

    /**
     * @param images nguồn ảnh; {@code null} = chỉ hiện ảnh đưa vào bằng {@link #showPreview}
     * @param width  chiều rộng mong muốn
     * @param height chiều cao cố định
     */
    ImageThumb(ClientImages images, int width, int height) {
        this.images = images;
        setPreferredSize(new Dimension(width, height));
        setMinimumSize(new Dimension(80, height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    /** Hiện ảnh theo mã; {@code null} = "Chưa có ảnh". Gọi lại với cùng mã thì không tải lại. */
    void show(String newImageId) {
        String id = newImageId == null || newImageId.isBlank() ? null : newImageId;
        if (Objects.equals(id, imageId) && (image != null || id == null)) {
            return;
        }
        imageId = id;
        image = null;
        if (id == null) {
            setText("Chưa có ảnh");
            return;
        }
        if (images == null) {
            return;
        }
        setText("Đang tải ảnh...");
        images.load(id, loaded -> {
            if (id.equals(imageId)) {
                image = loaded;
                repaint();
            }
        }, error -> {
            if (id.equals(imageId)) {
                setText("Không tải được ảnh");
                setToolTipText(error);
            }
        });
    }

    /** Hiện ảnh xem trước (vừa chọn từ máy, chưa có mã) hoặc một dòng chữ khi {@code preview} null. */
    void showPreview(BufferedImage preview, String message) {
        imageId = null;
        image = preview;
        setText(message == null ? "Chưa có ảnh" : message);
    }

    private void setText(String value) {
        text = value;
        setToolTipText(null);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int w = getWidth();
            int h = getHeight();
            Shape clip = new RoundRectangle2D.Float(0, 0, w - 1f, h - 1f, ARC, ARC);
            g.setColor(UiTheme.CANVAS_SOFT);
            g.fill(clip);
            if (image != null) {
                g.clip(clip);
                drawCover(g, image, w, h);
                g.setClip(null);
            } else {
                g.setColor(UiTheme.INK_MUTE);
                g.setFont(UiTheme.sans(Font.PLAIN, 13f));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            }
            g.setColor(UiTheme.HAIRLINE);
            g.draw(clip);
        } finally {
            g.dispose();
        }
    }

    /** Phóng ảnh để phủ kín khung, cắt đều hai bên phần thừa (như CSS {@code object-fit: cover}). */
    private static void drawCover(Graphics2D g, BufferedImage img, int w, int h) {
        double scale = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
        int dw = (int) Math.ceil(img.getWidth() * scale);
        int dh = (int) Math.ceil(img.getHeight() * scale);
        g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
    }
}
