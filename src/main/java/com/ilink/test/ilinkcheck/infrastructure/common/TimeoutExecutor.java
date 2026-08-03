package com.ilink.test.ilinkcheck.infrastructure.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 公共超时执行器。
 *
 * <p>统一"后台执行 + 限时等待 + 超时/失败兜底"的超时策略，
 * 避免各处手写 CompletableFuture / future.get(timeout) 的重复逻辑。
 * Roadmap 抓取、意图路由、TTS、图片生成、翻译等长调用统一走这里。</p>
 *
 * <p>使用守护线程池，不会阻止 JVM 退出；{@link Future#cancel(boolean)} 会中断正在执行的任务线程。</p>
 */
public final class TimeoutExecutor {

    private static final Logger log = LoggerFactory.getLogger(TimeoutExecutor.class);

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "timeout-worker");
        thread.setDaemon(true);
        return thread;
    });

    private TimeoutExecutor() {
    }

    /** 提交任务到后台线程池，立即返回 Future，不阻塞调用方。 */
    public static <T> Future<T> submit(Callable<T> task) {
        return EXECUTOR.submit(task);
    }

    /**
     * 等待已提交的任务结果；超时或执行失败时取消任务并返回兜底值。
     */
    public static <T> T await(Future<T> future, long timeoutMs, T fallback, String taskName) {
        try {
            T value = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return value != null ? value : fallback;
        } catch (Exception e) {
            future.cancel(true);
            log.warn("[TimeoutExecutor] {} 超时或失败（上限 {} ms），原因={}，使用兜底值",
                    taskName, timeoutMs, e.getClass().getSimpleName());
            return fallback;
        }
    }

    /**
     * 提交并限时等待结果；超时/失败返回兜底值。适用于串行调用场景。
     */
    public static <T> T executeOrFallback(Callable<T> task, long timeoutMs, T fallback, String taskName) {
        return await(submit(task), timeoutMs, fallback, taskName);
    }

    /**
     * 提交并限时等待结果；超时/失败抛出 {@link IllegalStateException}，由调用方自行降级。
     */
    public static <T> T executeOrThrow(Callable<T> task, long timeoutMs, String taskName) {
        Future<T> future = submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            log.warn("[TimeoutExecutor] {} 超时或失败（上限 {} ms），原因={}", taskName, timeoutMs, e.getClass().getSimpleName());
            throw new IllegalStateException(taskName + " 执行超时或失败（上限 " + timeoutMs + "ms）", e);
        }
    }
}
