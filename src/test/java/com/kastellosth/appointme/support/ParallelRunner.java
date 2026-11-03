package com.kastellosth.appointme.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Test helper: runs the same task in many threads that are released at the same moment. */
public final class ParallelRunner {

    private ParallelRunner() {
    }

    /**
     * Starts {@code count} threads, waits until ALL of them are ready, then releases them together
     * (so they hit the database at the same instant), and returns every result.
     * The task should catch its own exceptions and turn them into a result value.
     */
    public static <T> List<T> runAtOnce(int count, Callable<T> task) throws Exception {
        // One thread per task: the pool must be as big as the number of tasks, otherwise
        // some tasks would wait for a free thread and would not really run at the same time.
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch ready = new CountDownLatch(count);   // "I am standing on the start line"
            CountDownLatch go = new CountDownLatch(1);          // the starting gun

            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }

            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Not all threads got ready in 30 seconds");
            }
            go.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
