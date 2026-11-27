package com.livebid.client.image;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Quy tắc ảnh phía client, khớp kho ảnh của server ({@code ImageStore}): JPEG, PNG, WebP, tối đa
 * {@value #MAX_BYTES} byte. Server vẫn kiểm tra lại theo chữ ký đầu file; ở đây chỉ để báo lỗi sớm.
 */
public final class ImageFiles {

    /** Khớp {@code ImageStore.MAX_BYTES}. */
    public static final long MAX_BYTES = 2L * 1024 * 1024;

    /** Phần mở rộng hiện trong bộ lọc của hộp chọn file. */
    public static final String[] EXTENSIONS = {"jpg", "jpeg", "png", "webp"};

    private ImageFiles() {
    }

    /** Content-Type theo phần mở rộng; {@code null} nếu không phải loại ảnh được nhận. */
    public static String contentType(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    /**
     * Kiểm tra tên và dung lượng file trước khi gửi.
     *
     * @throws ImageException khi sai loại hoặc quá lớn
     */
    public static void check(String fileName, long size) {
        if (contentType(fileName) == null) {
            throw new ImageException("Chỉ nhận ảnh JPEG, PNG hoặc WebP");
        }
        if (size <= 0) {
            throw new ImageException("File ảnh rỗng");
        }
        if (size > MAX_BYTES) {
            throw new ImageException("Ảnh tối đa 2 MB");
        }
    }

    /** Thu nhỏ (giữ tỉ lệ) để cạnh dài không quá {@code maxSide}; ảnh đã nhỏ thì trả nguyên. */
    public static BufferedImage shrink(BufferedImage image, int maxSide) {
        int w = image.getWidth();
        int h = image.getHeight();
        int longest = Math.max(w, h);
        if (maxSide <= 0 || longest <= maxSide) {
            return image;
        }
        double scale = (double) maxSide / longest;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, image.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
