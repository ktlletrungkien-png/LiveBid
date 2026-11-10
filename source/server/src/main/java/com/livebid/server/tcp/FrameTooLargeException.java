package com.livebid.server.tcp;

import java.io.IOException;

/** Thông điệp TCP vượt quá giới hạn kích thước; kết nối gửi nó phải bị đóng. */
public class FrameTooLargeException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int limitBytes;

    public FrameTooLargeException(int limitBytes) {
        super("Thông điệp vượt quá " + limitBytes + " byte");
        this.limitBytes = limitBytes;
    }

    public int limitBytes() {
        return limitBytes;
    }
}
