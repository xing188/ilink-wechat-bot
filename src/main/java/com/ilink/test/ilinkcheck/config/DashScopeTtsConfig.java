package com.ilink.test.ilinkcheck.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioSpeechApi;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechModel;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DashScopeTtsConfig {

    @Bean
    public DashScopeAudioSpeechApi dashScopeAudioSpeechApi(@Value("${spring.ai.dashscope.api-key}") String apiKey) {
        return new DashScopeAudioSpeechApi(apiKey);
    }

    @Bean
    public TextToSpeechOptions textToSpeechOptions(
            @Value("${app.tts.model:cosyvoice-v3-flash}") String model,
            @Value("${app.tts.voice:longanyang}") String voice,
            @Value("${app.tts.sample-rate:24000}") Integer sampleRate,
            @Value("${app.tts.speed:1.0}") Double speed) {

        return DashScopeAudioSpeechOptions.builder()
                .model(model)
                .voice(voice)
                .sampleRate(sampleRate)
                .speed(speed)
                .responseFormat(DashScopeAudioSpeechApi.ResponseFormat.MP3)
                .build();
    }

    @Bean
    public TextToSpeechModel textToSpeechModel(DashScopeAudioSpeechApi dashScopeAudioSpeechApi,
                                               TextToSpeechOptions textToSpeechOptions) {
        return new DashScopeAudioSpeechModel(
                dashScopeAudioSpeechApi,
                (DashScopeAudioSpeechOptions) textToSpeechOptions
        );
    }
}
