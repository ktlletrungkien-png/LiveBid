package com.livebid.client.image;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Cửa vào phần ảnh cho giao diện Swing. Đây là <b>chỗ duy nhất</b> chạy việc ảnh trên luồng nền:
 * {@link HttpImageTransport} chạy trên 2 luồng riêng, kết quả trả về EDT qua callback.
 *
 * <p>Thêm cache LRU: mã ảnh không bao giờ đổi nội dung nên cache không bao giờ cũ. Ảnh trong cache đã thu
 * nhỏ còn cạnh dài tối đa {@value #MAX_SIDE} điểm ảnh nên {@value #CACHE_SIZE} ảnh chỉ tốn vài chục MB.
 * Nhiều thẻ xin cùng một ảnh thì chỉ tải một lần.
 */
public final class ClientImages {

    /** Cạnh dài tối đa của ảnh giữ trong bộ nhớ, đủ cho khung lớn nhất (phòng đấu giá). */
    public static final int MAX_SIDE = 960;

    static final int CACHE_SIZE = 64;

    private final HttpImageTransport transport;
    private final ExecutorService worker = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "client-images");
        thread.setDaemon(true);
        return thread;
    });

    /** Mã ảnh → ảnh đang tải hoặc đã tải. Chỉ truy cập trên EDT. */
    private final Map<String, CompletableFuture<BufferedImage>> cache = new LinkedHashMap<>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<BufferedImage>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public ClientImages(HttpImageTransport transport) {
        this.transport = transport;
    }

    /** Gọi khi kết nối lại: quên địa chỉ HTTP đã dò. Ảnh trong cache vẫn dùng được. */
    public void reset() {
        transport.reset();
    }

    /** Lấy ảnh (cache hoặc tải). Gọi trên EDT; callback cũng chạy trên EDT. Lỗi không được nhớ. */
    public void load(String imageId, Consumer<BufferedImage> onImage, Consumer<String> onError) {
        CompletableFuture<BufferedImage> future = cache.get(imageId);
        if (future == null || future.isCompletedExceptionally()) {
            future = CompletableFuture.supplyAsync(() -> transport.load(imageId, MAX_SIDE), worker);
            cache.put(imageId, future);
        }
        whenDone(future, onImage, onError);
    }

    /** Tải ảnh lên; callback chạy trên EDT, {@code onDone} nhận mã ảnh. */
    public void upload(Path file, Consumer<String> onDone, Consumer<String> onError) {
        whenDone(CompletableFuture.supplyAsync(() -> transport.upload(file), worker), onDone, onError);
    }

    private static <T> void whenDone(CompletableFuture<T> future, Consumer<T> onValue, Consumer<String> onError) {
        future.whenComplete((value, error) -> SwingUtilities.invokeLater(() -> {
            if (error == null) {
                onValue.accept(value);
            } else {
                onError.accept(messageOf(error));
            }
        }));
    }

    private static String messageOf(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof ImageException ? cause.getMessage() : "Lỗi ảnh: " + cause;
    }
}
