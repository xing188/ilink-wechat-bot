package com.ilink.test.ilinkcheck.infrastructure.persistence.voicePreference;

import com.ilink.test.ilinkcheck.application.voice.VoicePreferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Slf4j
public class RedisVoicePreferenceRepository implements VoicePreferenceRepository {

    private static final String VOICE_KEY_PREFIX = "user:voice_preference:";
    private static final String VOICE_SELECTION_PENDING_KEY_PREFIX = "user:voice_selection_pending:";

    private final StringRedisTemplate redisTemplate;
    private final Map<String, String> localVoicePreferenceCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> localPendingSelectionCache = new ConcurrentHashMap<>();

    public RedisVoicePreferenceRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String findVoiceParam(String userId) {
        try {
            String voiceParam = redisTemplate.opsForValue().get(VOICE_KEY_PREFIX + userId);
            if (voiceParam != null) {
                localVoicePreferenceCache.put(userId, voiceParam);
                return voiceParam;
            }
        }
        catch (Exception e) {
            log.warn("Redis unavailable when reading voice preference. user={}", userId, e);
        }

        return localVoicePreferenceCache.get(userId);
    }

    @Override
    public void saveVoiceParam(String userId, String voiceParam) {
        localVoicePreferenceCache.put(userId, voiceParam);
        try {
            redisTemplate.opsForValue().set(VOICE_KEY_PREFIX + userId, voiceParam);
        }
        catch (Exception e) {
            log.warn("Redis unavailable when saving voice preference. user={}", userId, e);
        }
    }

    @Override
    public boolean hasPendingVoiceSelection(String userId) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(VOICE_SELECTION_PENDING_KEY_PREFIX + userId))) {
                return true;
            }
        }
        catch (Exception e) {
            log.warn("Redis unavailable when checking pending voice selection. user={}", userId, e);
        }

        return isLocalPending(userId);
    }

    @Override
    public void markPendingVoiceSelection(String userId, Duration ttl) {
        localPendingSelectionCache.put(userId, Instant.now().plus(ttl));
        try {
            redisTemplate.opsForValue().set(VOICE_SELECTION_PENDING_KEY_PREFIX + userId, "1", ttl);
        }
        catch (Exception e) {
            log.warn("Redis unavailable when marking pending voice selection. user={}", userId, e);
        }
    }

    @Override
    public void clearPendingVoiceSelection(String userId) {
        localPendingSelectionCache.remove(userId);
        try {
            redisTemplate.delete(VOICE_SELECTION_PENDING_KEY_PREFIX + userId);
        }
        catch (Exception e) {
            log.warn("Redis unavailable when clearing pending voice selection. user={}", userId, e);
        }
    }

    private boolean isLocalPending(String userId) {
        Instant expiresAt = localPendingSelectionCache.get(userId);
        if (expiresAt == null) {
            return false;
        }

        if (Instant.now().isAfter(expiresAt)) {
            localPendingSelectionCache.remove(userId);
            return false;
        }
        return true;
    }
}
