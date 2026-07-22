package com.ilink.test.ilinkcheck.application.conversation;

import com.ilink.test.ilinkcheck.model.ConversationResult;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final ConversationAiService conversationAiService;
    private final ConversationResponseParser conversationResponseParser;

    public ConversationService(ConversationAiService conversationAiService,
                               ConversationResponseParser conversationResponseParser) {
        this.conversationAiService = conversationAiService;
        this.conversationResponseParser = conversationResponseParser;
    }

    public ConversationResult handleTextMessage(String conversationId, String text) {
        String aiReply = conversationAiService.generateReply(conversationId, text);
        return conversationResponseParser.parse(conversationId, aiReply);
    }

    public ConversationResult handleVoiceTranscript(String conversationId, String transcript) {
        return handleTextMessage(conversationId, transcript);
    }
}
