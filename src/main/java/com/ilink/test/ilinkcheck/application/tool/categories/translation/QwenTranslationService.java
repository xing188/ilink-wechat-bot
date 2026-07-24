package com.ilink.test.ilinkcheck.application.tool.categories.translation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class QwenTranslationService implements TranslationService {

    private final RestTemplate restTemplate;
    private final String apiKey;

    public QwenTranslationService(RestTemplate restTemplate,
                                  @Value("${spring.ai.openai.api-key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String translate(String text, String sourceLang, String targetLang) {
        try {
            String prompt = String.format("Translate %s to %s. Return ONLY translation, no explanations.", sourceLang, targetLang);

            Map<String, Object> body = Map.of(
                    "model", "qwen3.6-plus",
                    "messages", List.of(
                            Map.of("role", "system", "content", prompt),
                            Map.of("role", "user", "content", text)
                    ),
                    "max_tokens", 2048,
                    "temperature", 0.3
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            var resp = restTemplate.exchange(
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                    HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            var choices = (List<Map<String, Object>>) resp.getBody().get("choices");
            if (choices == null || choices.isEmpty()) return null;
            var msg = (Map<String, Object>) choices.get(0).get("message");
            return msg != null ? (String) msg.get("content") : null;
        } catch (Exception e) {
            log.error("翻译失败", e);
            return null;
        }
    }
}
