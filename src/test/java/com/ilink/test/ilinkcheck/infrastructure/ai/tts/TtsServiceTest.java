package com.ilink.test.ilinkcheck.infrastructure.ai.tts;

import com.alibaba.cloud.ai.dashscope.api.DashScopeAudioSpeechApi;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeAudioSpeechOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TtsServiceTest {

    @Test
    void shouldOverrideVoicePerRequest() {
        TextToSpeechModel model = mock(TextToSpeechModel.class);
        TextToSpeechOptions defaultOptions = DashScopeAudioSpeechOptions.builder()
                .model("cosyvoice-v3-flash")
                .voice("longanyang")
                .responseFormat(DashScopeAudioSpeechApi.ResponseFormat.MP3)
                .build();
        when(model.call(any(TextToSpeechPrompt.class)))
                .thenReturn(new TextToSpeechResponse(List.of(new Speech(new byte[]{1, 2, 3}))));

        DashScopeTextToSpeechServiceImpl service = new DashScopeTextToSpeechServiceImpl(model, defaultOptions);

        byte[] result = service.synthesizeMp3("你好", "longlaobo_v3");

        assertThat(result).containsExactly(1, 2, 3);

        var captor = org.mockito.ArgumentCaptor.forClass(TextToSpeechPrompt.class);
        verify(model).call(captor.capture());
        assertThat(captor.getValue().getOptions().getVoice()).isEqualTo("longlaobo_v3");
    }
}
