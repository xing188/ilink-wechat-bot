package com.ilink.test.ilinkcheck.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class FileChatMemory implements ChatMemory {

    private static final String STORAGE_FILE_PATH = "chat_memory_history.json";
    private static final int MAX_MESSAGES = 20;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<Map<String, String>>> historyMap = new HashMap<>();

    public FileChatMemory() {
        loadFromFile();
    }

    @Override
    public synchronized void add(String conversationId, List<Message> messages) {
        List<Map<String, String>> conversation = historyMap.computeIfAbsent(conversationId, key -> new ArrayList<>());
        for (Message message : messages) {
            if (!(message instanceof UserMessage) && !(message instanceof AssistantMessage)) {
                continue;
            }

            String text = message.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            Map<String, String> storedMessage = new LinkedHashMap<>();
            storedMessage.put("type", message.getMessageType().name());
            storedMessage.put("content", text);
            conversation.add(storedMessage);
        }

        saveToFile();
    }

    @Override
    public synchronized List<Message> get(String conversationId) {
        List<Map<String, String>> conversation = historyMap.getOrDefault(conversationId, Collections.emptyList());
        if (conversation.isEmpty()) {
            return Collections.emptyList();
        }

        int start = Math.max(0, conversation.size() - MAX_MESSAGES);
        List<Map<String, String>> subList = conversation.subList(start, conversation.size());

        List<Message> result = new ArrayList<>(subList.size());
        for (Map<String, String> storedMessage : subList) {
            String type = storedMessage.get("type");
            String content = storedMessage.get("content");

            if ("USER".equals(type)) {
                result.add(new UserMessage(content));
            }
            else if ("ASSISTANT".equals(type)) {
                result.add(new AssistantMessage(content));
            }
        }

        return result;
    }

    @Override
    public synchronized void clear(String conversationId) {
        historyMap.remove(conversationId);
        saveToFile();
    }

    private synchronized void saveToFile() {
        try {
            objectMapper.writeValue(new File(STORAGE_FILE_PATH), historyMap);
        }
        catch (Exception e) {
            log.error("Failed to save chat memory file", e);
        }
    }

    private synchronized void loadFromFile() {
        File file = new File(STORAGE_FILE_PATH);
        if (!file.exists()) {
            return;
        }

        try {
            Map<String, List<Map<String, String>>> data = objectMapper.readValue(
                    file,
                    new TypeReference<Map<String, List<Map<String, String>>>>() {
                    });
            historyMap.putAll(data);
            log.info("Loaded chat memory from disk. conversations={}", historyMap.size());
        }
        catch (Exception e) {
            log.error("Failed to load chat memory file", e);
        }
    }
}
