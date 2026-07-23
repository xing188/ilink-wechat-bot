package com.ilink.test.ilinkcheck.application.voice;

import com.ilink.test.ilinkcheck.model.VoiceOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class VoicePreferenceService {

    private static final Duration VOICE_SELECTION_PENDING_TTL = Duration.ofMinutes(5);
    private static final VoiceOption DEFAULT_VOICE = VoiceOption.OPTION_1;

    private final VoicePreferenceRepository voicePreferenceRepository;

    public VoicePreferenceService(VoicePreferenceRepository voicePreferenceRepository) {
        this.voicePreferenceRepository = voicePreferenceRepository;
    }

    public String getUserVoiceParam(String userId) {
        String voiceParam = voicePreferenceRepository.findVoiceParam(userId);
        VoiceOption option = VoiceOption.fromVoiceParam(voiceParam);
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

        voicePreferenceRepository.saveVoiceParam(userId, option.getVoiceParam());
        log.info("User [{}] voice switched to {} ({})", userId, option.getName(), option.getVoiceParam());
    }

    public boolean isVoiceSelectionPending(String userId) {
        return voicePreferenceRepository.hasPendingVoiceSelection(userId);
    }

    public void markVoiceSelectionPending(String userId) {
        voicePreferenceRepository.markPendingVoiceSelection(userId, VOICE_SELECTION_PENDING_TTL);
    }

    public void clearVoiceSelectionPending(String userId) {
        voicePreferenceRepository.clearPendingVoiceSelection(userId);
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
}
