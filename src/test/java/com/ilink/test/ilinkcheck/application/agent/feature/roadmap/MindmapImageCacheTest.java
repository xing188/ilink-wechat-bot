package com.ilink.test.ilinkcheck.application.agent.feature.roadmap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MindmapImageCacheTest {

    @Test
    void shouldStoreAndRetrieveImageBytes() {
        MindmapImageCache cache = new MindmapImageCache();
        byte[] bytes = new byte[]{0x01, 0x02, 0x03, 0x04};

        String key = cache.put(bytes);
        byte[] retrieved = cache.get(key);

        assertThat(key).isNotBlank();
        assertThat(retrieved).isEqualTo(bytes);
    }

    @Test
    void shouldReturnNullForUnknownKey() {
        MindmapImageCache cache = new MindmapImageCache();

        byte[] retrieved = cache.get("non-existent-key");

        assertThat(retrieved).isNull();
    }

    @Test
    void shouldExpireEntriesAfterTtl() throws InterruptedException {
        MindmapImageCache cache = new MindmapImageCache(50L, 10);
        String key = cache.put(new byte[]{0x01});

        Thread.sleep(100L);

        assertThat(cache.get(key)).isNull();
    }

    @Test
    void shouldEvictWhenMaxSizeReached() {
        MindmapImageCache cache = new MindmapImageCache(60_000L, 2);
        cache.put(new byte[]{0x01});
        cache.put(new byte[]{0x02});
        cache.put(new byte[]{0x03});

        assertThat(cache.size()).isLessThanOrEqualTo(2);
    }
}
