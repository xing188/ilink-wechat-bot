package com.ilink.test.ilinkcheck.application.voice;

import java.time.Duration;

public interface VoicePreferenceRepository {

    String findVoiceParam(String userId);

    void saveVoiceParam(String userId, String voiceParam);

    boolean hasPendingVoiceSelection(String userId);

    void markPendingVoiceSelection(String userId, Duration ttl);

    void clearPendingVoiceSelection(String userId);
}
