package com.tmw.backend.testinfra;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ConcurrentTestRunner {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private ConcurrentTestRunner() {
    }

    /**
     * Chạy cùng một tác vụ trên N luồng xuất phát đồng thời (dùng cho spike 50 luồng reserve).
     */
    public static <T> ConcurrentResult<T> runSameTask(int threadCount, Callable<T> task) throws InterruptedException {
        return runSameTask(threadCount, DEFAULT_TIMEOUT, task);
    }

    public static <T> ConcurrentResult<T> runSameTask(
            int threadCount,
            Duration timeout,
            Callable<T> task
    ) throws InterruptedException {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount phải lớn hơn 0");
        }
        Objects.requireNonNull(task, "task không được null");
        List<Callable<T>> tasks = Collections.nCopies(threadCount, task);
        return runTasks(tasks, timeout);
    }

    /**
     * Chạy danh sách tác vụ khác nhau trên các luồng đồng thời (dùng để test 2 luồng khóa ngược thứ tự dòng PO).
     */
    public static <T> ConcurrentResult<T> runTasks(List<Callable<T>> tasks) throws InterruptedException {
        return runTasks(tasks, DEFAULT_TIMEOUT);
    }

    public static <T> ConcurrentResult<T> runTasks(
            List<Callable<T>> tasks,
            Duration timeout
    ) throws InterruptedException {
        Objects.requireNonNull(tasks, "tasks không được null");
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("Danh sách tasks không được rỗng");
        }

        int threadCount = tasks.size();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<ThreadOutcome<T>> outcomes = new ArrayList<>(Collections.nCopies(threadCount, null));

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                final Callable<T> workerTask = tasks.get(i);

                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        T value = workerTask.call();
                        outcomes.set(index, ThreadOutcome.success(index, value));
                    } catch (Throwable ex) {
                        outcomes.set(index, ThreadOutcome.failure(index, ex));
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            if (!readyLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Timeout khi chờ các luồng sẵn sàng xuất phát");
            }

            // Mở chốt cho toàn bộ luồng thực thi cùng một thời điểm
            startLatch.countDown();

            if (!doneLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "Timeout sau " + timeout.toSeconds() + "s khi chờ các luồng hoàn tất",
                        new TimeoutException()
                );
            }

            return new ConcurrentResult<>(List.copyOf(outcomes));
        } finally {
            executor.shutdownNow();
        }
    }

    public record ThreadOutcome<T>(
            int threadIndex,
            T value,
            Throwable error
    ) {
        public static <T> ThreadOutcome<T> success(int index, T value) {
            return new ThreadOutcome<>(index, value, null);
        }

        public static <T> ThreadOutcome<T> failure(int index, Throwable error) {
            return new ThreadOutcome<>(index, null, error);
        }

        public boolean isSuccess() {
            return error == null;
        }

        public boolean isFailure() {
            return error != null;
        }
    }

    public record ConcurrentResult<T>(List<ThreadOutcome<T>> outcomes) {

        public int totalThreads() {
            return outcomes.size();
        }

        public long successCount() {
            return outcomes.stream().filter(ThreadOutcome::isSuccess).count();
        }

        public long failureCount() {
            return outcomes.stream().filter(ThreadOutcome::isFailure).count();
        }

        public List<T> successValues() {
            return outcomes.stream()
                    .filter(ThreadOutcome::isSuccess)
                    .map(ThreadOutcome::value)
                    .toList();
        }

        public List<Throwable> errors() {
            return outcomes.stream()
                    .filter(ThreadOutcome::isFailure)
                    .map(ThreadOutcome::error)
                    .toList();
        }

        public <E extends Throwable> List<E> errorsOfType(Class<E> errorType) {
            return outcomes.stream()
                    .map(ThreadOutcome::error)
                    .filter(errorType::isInstance)
                    .map(errorType::cast)
                    .toList();
        }
    }
}