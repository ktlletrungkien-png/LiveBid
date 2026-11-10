package com.livebid.server.core;

import com.livebid.common.protocol.ErrorCode;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executor nghiệp vụ <b>một luồng</b>: mọi thao tác đọc/ghi trạng thái đấu giá và DB đi qua đây.
 *
 * <p>Vì chỉ có một luồng, các tác vụ chạy lần lượt theo đúng thứ tự được đưa vào. Hai người đặt giá
 * "cùng lúc" thực chất là hai tác vụ xếp hàng: tác vụ sau luôn thấy kết quả của tác vụ trước, nên không
 * cần khóa (lock) cho dữ liệu đấu giá và không thể có hai bid cùng thắng một mức giá.
 *
 * <ul>
 *   <li>Hàng đợi có giới hạn: đầy thì từ chối ngay với {@code SERVER_BUSY}, không chặn người gọi.</li>
 *   <li>Người gọi nhận {@link CompletableFuture}; không được {@code join()/get()} trên luồng đọc socket
 *       hay luồng giao diện, và <b>tuyệt đối không</b> chờ future trên chính luồng executor (tự khóa chết).</li>
 *   <li>Việc tốn thời gian không liên quan dữ liệu (băm mật khẩu, gọi RMI) chạy ở luồng khác,
 *       chỉ phần đọc/ghi DB mới đưa vào đây.</li>
 * </ul>
 */
public final class BusinessExecutor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BusinessExecutor.class);

    public static final int DEFAULT_QUEUE_CAPACITY = 10_000;
    public static final String THREAD_NAME = "business-executor";

    private final ThreadPoolExecutor executor;
    private volatile Thread workerThread;

    public BusinessExecutor() {
        this(DEFAULT_QUEUE_CAPACITY);
    }

    public BusinessExecutor(int queueCapacity) {
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, THREAD_NAME);
                    thread.setDaemon(true);
                    workerThread = thread;
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Đưa tác vụ vào hàng đợi; kết quả hoặc lỗi của tác vụ được chuyển nguyên vào future. */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(task.call());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(executor.isShutdown()
                    ? new ServiceException(ErrorCode.SERVER_BUSY, "Server đang dừng")
                    : new ServiceException(ErrorCode.SERVER_BUSY, "Server đang quá tải, thử lại sau"));
        }
        return future;
    }

    public CompletableFuture<Void> run(Runnable task) {
        return submit(() -> {
            task.run();
            return null;
        });
    }

    /** {@code true} nếu đang chạy trên luồng executor. */
    public boolean isExecutorThread() {
        return Thread.currentThread() == workerThread;
    }

    /** Dùng để khẳng định dữ liệu chỉ được chạm từ luồng executor. */
    public void checkExecutorThread() {
        if (!isExecutorThread()) {
            throw new IllegalStateException("Phải chạy trên " + THREAD_NAME + ", đang ở "
                    + Thread.currentThread().getName());
        }
    }

    /** Số tác vụ đang chờ (để log/giám sát). */
    public int queuedTasks() {
        return executor.getQueue().size();
    }

    /** Không nhận tác vụ mới, chờ tối đa 5 giây cho các tác vụ đang xếp hàng chạy xong. */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Executor nghiệp vụ chưa xong sau 5 giây, buộc dừng {} tác vụ",
                        executor.shutdownNow().size());
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
