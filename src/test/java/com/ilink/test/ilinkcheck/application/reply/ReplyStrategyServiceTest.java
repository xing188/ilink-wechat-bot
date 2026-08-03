package com.ilink.test.ilinkcheck.application.reply;

import com.ilink.test.ilinkcheck.model.ReplyMode;
import com.ilink.test.ilinkcheck.model.VoiceOption;
import com.ilink.test.ilinkcheck.application.voice.VoicePreferenceService;
import com.ilink.test.ilinkcheck.infrastructure.ai.tts.DashScopeTextToSpeechServiceImpl;
import com.ilink.test.ilinkcheck.infrastructure.document.DocumentExportService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReplyStrategyServiceTest {

    @Test
    void shouldBuildAudioReplyDraft() {
        DashScopeTextToSpeechServiceImpl ttsService = mock(DashScopeTextToSpeechServiceImpl.class);
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
    void shouldKeepLongReplyAsPlainText() {
        DashScopeTextToSpeechServiceImpl ttsService = mock(DashScopeTextToSpeechServiceImpl.class);
        DocumentExportService documentExportService = mock(DocumentExportService.class);
        VoicePreferenceService voicePreferenceService = mock(VoicePreferenceService.class);
        String longReply = "a".repeat(801);

        ReplyStrategyService service = new ReplyStrategyService(ttsService, documentExportService, voicePreferenceService);

        ReplyDraft draft = service.build("u1", longReply, ReplyMode.TEXT);

        assertThat(draft.type()).isEqualTo(ReplyDraft.Type.TEXT);
        assertThat(draft.text()).isEqualTo(longReply);
        verifyNoInteractions(documentExportService);
    }

    @Test
    void forceTextModeSkipsDocxConversionEvenForLongReply() {
        // FORCE_TEXT 模式下同样直接返回纯文本
        DashScopeTextToSpeechServiceImpl ttsService = mock(DashScopeTextToSpeechServiceImpl.class);
        DocumentExportService documentExportService = mock(DocumentExportService.class);
        VoicePreferenceService voicePreferenceService = mock(VoicePreferenceService.class);
        // 模拟一条路线输出（远超 800 字符）
        String longRoadmap = "RAG 学习路线\n\n第一阶段：预备知识\n- 基础编程语法：掌握变量、循环、条件判断\n"
                + "第二阶段：核心概念\n- RAG 的核心概念：检索增强生成原理\n"
                + "第三阶段：实战建议\n- 做一个最小 demo\n\n推荐资料\n"
                + "- [Getting Started with RAG](https://dev.to/example)（来源：Dev.to）：入门文章（👍 320）\n"
                + "- [RAG official docs](https://example.com/docs)（来源：Example）：官方文档";

        ReplyStrategyService service = new ReplyStrategyService(ttsService, documentExportService, voicePreferenceService);

        ReplyDraft draft = service.build("u1", longRoadmap, ReplyMode.FORCE_TEXT);

        assertThat(draft.type()).isEqualTo(ReplyDraft.Type.TEXT);
        assertThat(draft.text()).contains("学习路线");
        assertThat(draft.text()).contains("（来源：Dev.to）");
        verifyNoInteractions(documentExportService);
    }
}
