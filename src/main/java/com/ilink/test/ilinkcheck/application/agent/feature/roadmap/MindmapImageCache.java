package com.ilink.test.ilinkcheck.application.agent.feature.roadmap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 思维导图产物临时缓存（既缓存 PNG 图片字节，也缓存 .xmind 文件字节）。
 *
 * <p>由于 Agent 工作流当前返回文本，生成的图片/文件需要一种"bridge"方式送到微信发送层：
 * 阶段执行器把字节存入本缓存并返回 {@code [MINDMAP_IMAGE]:<图片key>:<文件key>} 标记文本，
 * {@link com.ilink.test.ilinkcheck.adapter.inbound.wechat.TextMessageProcessor}
 * 解析标记后再从缓存分别取出图片与文件字节发送。</p>
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li>进程内内存存储，带 TTL（默认 5 分钟）与容量上限（默认 128 张），防止内存泄漏；</li>
 *   <li>key 随机生成，不依赖 conversationId，避免多轮会话间互相干扰；</li>
 *   <li>线程安全，支持并发 put/get。</li>
 * </ul>
 */
@Slf4j
@Service
public class MindmapImageCache {

    /** 默认有效期 5 分钟（用户从生成到点击查看通常很快） */
    private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;
    /** 默认最多保留 128 张图片 */
    private static final int DEFAULT_MAX_SIZE = 128;

    private final Map<String, CacheEntry> store = new ConcurrentHashMap<>();
    private final long ttlMs;
    private final int maxSize;
    private final SecureRandom random = new SecureRandom();

    public MindmapImageCache() {
        this(DEFAULT_TTL_MS, DEFAULT_MAX_SIZE);
    }

    public MindmapImageCache(long ttlMs, int maxSize) {
        this.ttlMs = ttlMs;
        this.maxSize = maxSize;
    }

    /**
     * 存入图片字节，返回访问 key。
     */
    public String put(byte[] imageBytes) {
        evictIfNeeded();
        evictExpired();

        String key = generateKey();
        store.put(key, new CacheEntry(imageBytes, Instant.now().toEpochMilli()));
        log.info("[MindmapImageCache] put key={}, size={}, total={}", key, imageBytes.length, store.size());
        return key;
    }

    /**
     * 根据 key 取出图片字节；key 不存在或已过期则返回 null。
     */
    public byte[] get(String key) {
        evictExpired();
        CacheEntry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            store.remove(key);
            return null;
        }
        return entry.bytes;
    }

    /**
     * 当前缓存条目数（主要用于测试）。
     */
    public int size() {
        evictExpired();
        return store.size();
    }

    private void evictExpired() {
        long now = Instant.now().toEpochMilli();
        Iterator<Map.Entry<String, CacheEntry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CacheEntry> e = it.next();
            if (now - e.getValue().timestamp > ttlMs) {
                it.remove();
            }
        }
    }

    private void evictIfNeeded() {
        while (store.size() >= maxSize) {
            String oldest = store.keySet().stream().findFirst().orElse(null);
            if (oldest == null) {
                break;
            }
            store.remove(oldest);
        }
    }

    private boolean isExpired(CacheEntry entry) {
        return Instant.now().toEpochMilli() - entry.timestamp > ttlMs;
    }

    private String generateKey() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record CacheEntry(byte[] bytes, long timestamp) {
    }
}
