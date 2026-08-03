package com.ilink.test.ilinkcheck.infrastructure.ai.tts;

import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechOptions;
import com.ilink.test.ilinkcheck.application.voice.TextToSpeechService;
import com.ilink.test.ilinkcheck.infrastructure.common.TimeoutExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;

@Service
@Slf4j
public class DashScopeTextToSpeechServiceImpl implements TextToSpeechService {

    private final TextToSpeechModel textToSpeechModel;
    private final TextToSpeechOptions defaultOptions;

    /** TTS 合成最长等待时间（毫秒），超时抛出异常由调用方降级为文本回复。 */
    @Value("${app.tts.timeout-ms:30000}")
    private long ttsTimeoutMs = 30000;

    public DashScopeTextToSpeechServiceImpl(TextToSpeechModel textToSpeechModel, TextToSpeechOptions defaultOptions) {
        this.textToSpeechModel = textToSpeechModel;
        this.defaultOptions = defaultOptions;
    }

    public byte[] synthesizeMp3(String text) {
        return synthesizeMp3(text, null);
    }

    @Override
    public byte[] synthesizeMp3(String text, String voiceParam) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text must not be blank");
        }

        TextToSpeechOptions options = defaultOptions.copy();
        if (StringUtils.hasText(voiceParam) && options instanceof DashScopeAudioSpeechOptions dashScopeOptions) {
            dashScopeOptions.setVoice(voiceParam.trim());
        }

        TextToSpeechPrompt prompt = new TextToSpeechPrompt(text.trim(), options);
        TextToSpeechResponse response = TimeoutExecutor.executeOrThrow(
                () -> textToSpeechModel.call(prompt),
                ttsTimeoutMs,
                "TTS 语音合成"
        );

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        response.getResults().forEach(speech -> {
            byte[] chunk = speech.getOutput();
            if (chunk != null && chunk.length > 0) {
                outputStream.writeBytes(chunk);
            }
        });

        byte[] audioBytes = outputStream.toByteArray();
        log.info("Synthesized mp3 bytes={}, voice={}", audioBytes.length, options.getVoice());
        if (audioBytes.length == 0) {
            throw new IllegalStateException("TTS returned empty audio content");
        }
        return audioBytes;
    }
}
