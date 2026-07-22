package com.ilink.test.ilinkcheck.service;

import com.ilink.test.ilinkcheck.model.VoiceOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class VoicePreferenceService {

    private static final String VOICE_KEY_PREFIX = "user:voice_preference:";
    private static final String VOICE_SELECTION_PENDING_KEY_PREFIX = "user:voice_selection_pending:";
    private static final Duration VOICE_SELECTION_PENDING_TTL = Duration.ofMinutes(5);
    private static final VoiceOption DEFAULT_VOICE = VoiceOption.OPTION_1;

    private final StringRedisTemplate redisTemplate;
    private final Map<String, String> localVoicePreferenceCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> localPendingSelectionCache = new ConcurrentHashMap<>();

    public VoicePreferenceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String getUserVoiceParam(String userId) {
        try {
            String voiceParam = redisTemplate.opsForValue().get(VOICE_KEY_PREFIX + userId);
            VoiceOption option = VoiceOption.fromVoiceParam(voiceParam);
            if (option != null) {
                return option.getVoiceParam();
            }
        }
        catch (Exception e) {
            log.warn("Redis unavailable when reading voice preference. user={}", userId, e);
        }

        String fallback = localVoicePreferenceCache.get(userId);
        VoiceOption option = VoiceOption.fromVoiceParam(fallback);
        if (option != null) {
            return option.getVoiceParam();
        }

        return DEFAULT_VOICE.getVoiceParam();
    }

    public VoiceOption getUserVoiceOption(String userId) {
        VoiceOption option = VoiceOption.fromVoiceParam(getUserVoiceParam(userId));
        return option != null ? option : DEFAULT_VOICE;
    }

    public void setUserVoice(String userId, VoiceOption option) {
        if (option == null) {
            throw new IllegalArgumentException("option must not be null");
        }

        localVoicePreferenceCache.put(userId, option.getVoiceParam());
        try {
            redisTemplate.opsForValue().set(VOICE_KEY_PREFIX + userId, option.getVoiceParam());
        }
        catch (Exception e) {
            log.warn("Redis unavailable when saving voice preference. user={}", userId, e);
        }

        log.info("User [{}] voice switched to {} ({})", userId, option.getName(), option.getVoiceParam());
    }

    public boolean isVoiceSelectionPending(String userId) {
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

    public void markVoiceSelectionPending(String userId) {
        localPendingSelectionCache.put(userId, Instant.now().plus(VOICE_SELECTION_PENDING_TTL));
        try {
            redisTemplate.opsForValue().set(VOICE_SELECTION_PENDING_KEY_PREFIX + userId, "1", VOICE_SELECTION_PENDING_TTL);
        }
        catch (Exception e) {
            log.warn("Redis unavailable when marking pending voice selection. user={}", userId, e);
        }
    }

    public void clearVoiceSelectionPending(String userId) {
        localPendingSelectionCache.remove(userId);
        try {
            redisTemplate.delete(VOICE_SELECTION_PENDING_KEY_PREFIX + userId);
        }
        catch (Exception e) {
            log.warn("Redis unavailable when clearing pending voice selection. user={}", userId, e);
        }
    }

    public String buildVoiceMenuText(String userId) {
        VoiceOption currentVoice = getUserVoiceOption(userId);
        StringBuilder sb = new StringBuilder("🎙️ 请选择你喜欢的语音音色：\n\n");

        for (VoiceOption option : VoiceOption.values()) {
            sb.append(option.displayLine(option == currentVoice)).append('\n');
        }

        sb.append("\n👉 请直接回复数字 (1-5) 或音色名称进行切换");
        return sb.toString();
    }

    public String buildVoiceChangedText(VoiceOption option) {
        return String.format("好的，已切换到 %s（%s）。之后我会用这个音色回复你。",
                option.getName(),
                option.getVoiceParam());
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
