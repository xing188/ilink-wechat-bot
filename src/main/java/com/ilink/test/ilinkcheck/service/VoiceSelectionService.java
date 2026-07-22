package com.ilink.test.ilinkcheck.service;

import com.ilink.test.ilinkcheck.model.ReplyMode;
import com.ilink.test.ilinkcheck.model.VoiceOption;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class VoiceSelectionService {

    private static final List<String> SWITCH_KEYWORDS = List.of(
            "换一个音色",
            "换个音色",
            "切换音色",
            "换音色",
            "更换音色",
            "选择音色",
            "换一个声音",
            "换个声音",
            "切换声音",
            "更换声音",
            "换声线",
            "切换声线",
            "换成",
            "改成",
            "改为",
            "切成"
    );

    private static final List<String> CANCEL_KEYWORDS = List.of("取消", "算了", "不用了", "不换了", "返回");

    private final VoicePreferenceService voicePreferenceService;

    public VoiceSelectionService(VoicePreferenceService voicePreferenceService) {
        this.voicePreferenceService = voicePreferenceService;
    }

    public Optional<SelectionReply> handleInput(String userId, String text, boolean preferAudioConfirmation) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }

        String normalized = text.trim();
        boolean pending = voicePreferenceService.isVoiceSelectionPending(userId);

        if (pending && isCancelCommand(normalized)) {
            voicePreferenceService.clearVoiceSelectionPending(userId);
            return Optional.of(new SelectionReply("好的，已取消本次音色切换。", ReplyMode.TEXT));
        }

        VoiceOption exactOption = VoiceOption.match(normalized);
        VoiceOption mentionedOption = VoiceOption.findMentioned(normalized);
        boolean switchIntent = containsSwitchIntent(normalized);

        if (exactOption != null && (pending || switchIntent)) {
            return Optional.of(applySelection(userId, exactOption, preferAudioConfirmation));
        }

        if (switchIntent) {
            if (mentionedOption != null) {
                return Optional.of(applySelection(userId, mentionedOption, preferAudioConfirmation));
            }

            voicePreferenceService.markVoiceSelectionPending(userId);
            return Optional.of(new SelectionReply(voicePreferenceService.buildVoiceMenuText(userId), ReplyMode.TEXT));
        }

        if (!pending) {
            return Optional.empty();
        }

        if (mentionedOption != null) {
            return Optional.of(applySelection(userId, mentionedOption, preferAudioConfirmation));
        }

        return Optional.of(new SelectionReply(
                "我没识别到你要切换的音色，请回复 1-5、音色名，或发送“取消”。\n\n"
                        + voicePreferenceService.buildVoiceMenuText(userId),
                ReplyMode.TEXT));
    }

    private SelectionReply applySelection(String userId, VoiceOption option, boolean preferAudioConfirmation) {
        voicePreferenceService.setUserVoice(userId, option);
        voicePreferenceService.clearVoiceSelectionPending(userId);

        ReplyMode replyMode = preferAudioConfirmation ? ReplyMode.AUDIO_FILE : ReplyMode.TEXT;
        return new SelectionReply(voicePreferenceService.buildVoiceChangedText(option), replyMode);
    }

    private boolean containsSwitchIntent(String text) {
        for (String keyword : SWITCH_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCancelCommand(String text) {
        for (String keyword : CANCEL_KEYWORDS) {
            if (text.equals(keyword) || text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public record SelectionReply(String text, ReplyMode replyMode) {
    }
}
