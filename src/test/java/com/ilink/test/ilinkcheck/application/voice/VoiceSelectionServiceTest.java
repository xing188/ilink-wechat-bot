package com.ilink.test.ilinkcheck.application.voice;

import com.ilink.test.ilinkcheck.model.ReplyMode;
import com.ilink.test.ilinkcheck.model.VoiceOption;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoiceSelectionServiceTest {

    @Test
    void shouldOpenMenuWhenUserAsksToSwitchVoice() {
        VoicePreferenceService voicePreferenceService = mock(VoicePreferenceService.class);
        when(voicePreferenceService.isVoiceSelectionPending("u1")).thenReturn(false);
        when(voicePreferenceService.buildVoiceMenuText("u1")).thenReturn("menu");

        VoiceSelectionService service = new VoiceSelectionService(voicePreferenceService);

        Optional<VoiceSelectionService.SelectionReply> reply = service.handleInput("u1", "换一个音色", false);

        assertThat(reply).isPresent();
        assertThat(reply.orElseThrow().text()).isEqualTo("menu");
        assertThat(reply.orElseThrow().replyMode()).isEqualTo(ReplyMode.TEXT);
        verify(voicePreferenceService).markVoiceSelectionPending("u1");
    }

    @Test
    void shouldApplySelectionWhenPending() {
        VoicePreferenceService voicePreferenceService = mock(VoicePreferenceService.class);
        when(voicePreferenceService.isVoiceSelectionPending("u1")).thenReturn(true);
        when(voicePreferenceService.buildVoiceChangedText(VoiceOption.OPTION_2)).thenReturn("done");

        VoiceSelectionService service = new VoiceSelectionService(voicePreferenceService);

        Optional<VoiceSelectionService.SelectionReply> reply = service.handleInput("u1", "2", true);

        assertThat(reply).isPresent();
        assertThat(reply.orElseThrow().text()).isEqualTo("done");
        assertThat(reply.orElseThrow().replyMode()).isEqualTo(ReplyMode.AUDIO_FILE);
        verify(voicePreferenceService).setUserVoice("u1", VoiceOption.OPTION_2);
        verify(voicePreferenceService).clearVoiceSelectionPending("u1");
    }

    @Test
    void shouldIgnoreNormalMessageWhenNotInSelectionFlow() {
        VoicePreferenceService voicePreferenceService = mock(VoicePreferenceService.class);
        when(voicePreferenceService.isVoiceSelectionPending("u1")).thenReturn(false);

        VoiceSelectionService service = new VoiceSelectionService(voicePreferenceService);

        Optional<VoiceSelectionService.SelectionReply> reply = service.handleInput("u1", "今天天气不错", false);

        assertThat(reply).isEmpty();
    }
}
