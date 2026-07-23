package com.ilink.test.ilinkcheck.application.reply;

import com.ilink.test.ilinkcheck.model.ReplyMode;
import com.ilink.test.ilinkcheck.model.VoiceOption;
import com.ilink.test.ilinkcheck.application.voice.VoicePreferenceService;
import com.ilink.test.ilinkcheck.infrastructure.ai.tts.TtsService;
import com.ilink.test.ilinkcheck.infrastructure.document.DocumentExportService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplyStrategyServiceTest {

    @Test
    void shouldBuildAudioReplyDraft() {
        TtsService ttsService = mock(TtsService.class);
        DocumentExportService documentExportService = mock(DocumentExportService.class);
        VoicePreferenceService voicePreferenceService = mock(VoicePreferenceService.class);
        when(voicePreferenceService.getUserVoiceOption("u1")).thenReturn(VoiceOption.OPTION_4);
        when(ttsService.synthesizeMp3("你好", "longlaobo_v3")).thenReturn(new byte[]{1, 2, 3});

        ReplyStrategyService service = new ReplyStrategyService(ttsService, documentExportService, voicePreferenceService);

        ReplyDraft draft = service.build("u1", "你好", ReplyMode.AUDIO_FILE);

        assertThat(draft.type()).isEqualTo(ReplyDraft.Type.FILE);
        assertThat(draft.fileName()).isEqualTo("reply.mp3");
        assertThat(draft.caption()).isEqualTo("你好");
        assertThat(draft.fileBytes()).containsExactly(1, 2, 3);
        verify(ttsService).synthesizeMp3("你好", "longlaobo_v3");
    }

    @Test
    void shouldBuildDocxDraftForLongReply() {
        TtsService ttsService = mock(TtsService.class);
        DocumentExportService documentExportService = mock(DocumentExportService.class);
        VoicePreferenceService voicePreferenceService = mock(VoicePreferenceService.class);
        String longReply = "a".repeat(801);
        when(documentExportService.exportToDocx("AI详细回复", longReply)).thenReturn(new byte[]{9, 8, 7});

        ReplyStrategyService service = new ReplyStrategyService(ttsService, documentExportService, voicePreferenceService);

        ReplyDraft draft = service.build("u1", longReply, ReplyMode.TEXT);

        assertThat(draft.type()).isEqualTo(ReplyDraft.Type.FILE);
        assertThat(draft.fileName()).isEqualTo("AI详细回复.docx");
        assertThat(draft.fileBytes()).containsExactly(9, 8, 7);
        verify(documentExportService).exportToDocx("AI详细回复", longReply);
    }
}
