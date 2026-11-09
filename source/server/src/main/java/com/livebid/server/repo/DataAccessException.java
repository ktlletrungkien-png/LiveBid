package com.livebid.server.repo;

/** Lỗi truy cập dữ liệu (bọc SQLException). Service đổi thành INTERNAL_ERROR. */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
