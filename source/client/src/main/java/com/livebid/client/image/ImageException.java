package com.livebid.client.image;

/** Lỗi khi gửi hoặc lấy ảnh; {@link #getMessage()} là câu tiếng Việt hiển thị được cho người dùng. */
public final class ImageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImageException(String message) {
        super(message);
    }

    public ImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
