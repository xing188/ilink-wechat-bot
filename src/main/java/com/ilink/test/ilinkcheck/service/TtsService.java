package com.ilink.test.ilinkcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.io.ByteArrayOutputStream;

@Service
@Slf4j
public class TtsService {

    private final TextToSpeechModel textToSpeechModel;
    private final TextToSpeechOptions defaultOptions;

    public TtsService(TextToSpeechModel textToSpeechModel, TextToSpeechOptions defaultOptions) {
        this.textToSpeechModel = textToSpeechModel;
        this.defaultOptions = defaultOptions;
    }

    public byte[] synthesizeMp3(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text must not be blank");
        }

        TextToSpeechPrompt prompt = new TextToSpeechPrompt(text.trim(), defaultOptions);
        TextToSpeechResponse response = textToSpeechModel.call(prompt);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        response.getResults().forEach(speech -> {
            byte[] chunk = speech.getOutput();
            if (chunk != null && chunk.length > 0) {
                outputStream.writeBytes(chunk);
            }
        });

        byte[] audioBytes = outputStream.toByteArray();
        log.info("Synthesized mp3 bytes={}", audioBytes.length);
        if (audioBytes.length == 0) {
            throw new IllegalStateException("TTS returned empty audio content");
        }
        return audioBytes;
    }
}
