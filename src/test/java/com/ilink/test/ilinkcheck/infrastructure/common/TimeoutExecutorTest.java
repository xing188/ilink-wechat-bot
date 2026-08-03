package com.ilink.test.ilinkcheck.infrastructure.common;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TimeoutExecutor 单元测试。
 * 验证"后台执行 + 限时等待 + 超时/失败兜底"的统一行为。
 */
class TimeoutExecutorTest {

    @Test
    void executeOrFallbackReturnsResultWithinTimeout() {
        String result = TimeoutExecutor.executeOrFallback(
                () -> "ok",
                1000,
                "fallback",
                "test"
        );
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void executeOrFallbackReturnsFallbackOnTimeout() {
        String result = TimeoutExecutor.executeOrFallback(
                () -> {
                    Thread.sleep(300);
                    return "slow";
                },
                50,
                "fallback",
                "test-timeout"
        );
        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void executeOrFallbackReturnsFallbackOnException() {
        String result = TimeoutExecutor.executeOrFallback(
                () -> {
                    throw new RuntimeException("boom");
                },
                1000,
                "fallback",
                "test-exception"
        );
        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void executeOrThrowThrowsOnTimeout() {
        assertThatThrownBy(() -> TimeoutExecutor.executeOrThrow(
                () -> {
                    Thread.sleep(300);
                    return "slow";
                },
                50,
                "test-throw"
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void executeOrThrowReturnsResultWithinTimeout() {
        Integer value = TimeoutExecutor.executeOrThrow(() -> 42, 1000, "test-ok");
        assertThat(value).isEqualTo(42);
    }

    @Test
    void submitThenAwaitRunsTasksConcurrently() throws Exception {
        // 两个任务各耗时 200ms，若串行则总耗时约 400ms；并发应明显小于串行之和。
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        Callable<Integer> task = () -> {
            int now = active.incrementAndGet();
            maxActive.updateAndGet(m -> Math.max(m, now));
            Thread.sleep(200);
            active.decrementAndGet();
            return now;
        };

        Future<Integer> futureA = TimeoutExecutor.submit(task);
        Future<Integer> futureB = TimeoutExecutor.submit(task);

        TimeoutExecutor.await(futureA, 3000, -1, "A");
        TimeoutExecutor.await(futureB, 3000, -1, "B");

        assertThat(maxActive.get()).isEqualTo(2);
    }

    @Test
    void awaitReturnsFallbackWhenFutureFails() {
        Future<String> future = TimeoutExecutor.submit(() -> {
            throw new RuntimeException("boom");
        });
        String result = TimeoutExecutor.await(future, 1000, "fallback", "test-await");
        assertThat(result).isEqualTo("fallback");
    }
}
