package com.ilink.test.ilinkcheck.application.tool.categories.translation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@Slf4j
public class QwenTranslationService implements TranslationService {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;

    public QwenTranslationService(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.chat.model:qwen3.7-plus}") String model) {
        this.apiKey = apiKey;
        this.model = model; // 与 application-ai.yml 的 chat.model 保持一致
        this.restTemplate = createRestTemplateWithTimeout();
        // 启动时立即诊断：key 是否为未解析的占位符
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${") && apiKey.endsWith("}")) {
            log.error("[翻译] ⚠️ API Key 未配置或未解析！当前值前20字符: {}",
                    apiKey == null ? "null" : apiKey.substring(0, Math.min(20, apiKey.length())));
            log.error("[翻译] 请确认环境变量 ALIYUN_API_KEY 已设置，或在 application.yml 中直接填写 sk- 开头的完整 Key");
        } else {
            log.info("[翻译] QwenTranslationService 初始化成功，API Key 前6位: {}... | 模型: {}",
                    apiKey.substring(0, Math.min(6, apiKey.length())), model);
        }
    }

    private static RestTemplate createRestTemplateWithTimeout() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);   // 连接超时 5 秒
        factory.setReadTimeout(30000);     // 读取超时 30 秒
        return new RestTemplate(factory);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String translate(String text, String sourceLang, String targetLang) {
        List<String> results = batchTranslate(List.of(text), sourceLang, targetLang);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> batchTranslate(List<String> texts, String sourceLang, String targetLang) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        long startMs = System.currentTimeMillis();
        log.info("[翻译] 开始批量翻译 {} 段文本", texts.size());

        try {
            // 将所有文本编号拼接成一次请求，让 LLM 按格式返回
            StringBuilder numberedText = new StringBuilder();
            for (int i = 0; i < texts.size(); i++) {
                numberedText.append(String.format("[%d] %s", i + 1, texts.get(i)));
                if (i < texts.size() - 1) numberedText.append("\n");
            }

            String prompt = String.format(
                    "Translate the following %d texts from %s to %s. " +
                    "Return ONLY the translations, one per line, in the same order, " +
                    "prefixed with the same number in brackets [N]. " +
                    "No explanations, no extra text.\n\n%s",
                    texts.size(), sourceLang, targetLang, numberedText
            );

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "You are a professional translator. Translate accurately and concisely."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "max_tokens", 4096,
                    "temperature", 0.3
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            var resp = restTemplate.exchange(
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                    HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

            // 诊断：记录 HTTP 状态码
            log.info("[翻译] DashScope API 响应状态: {}", resp.getStatusCode());

            var choices = (List<Map<String, Object>>) resp.getBody().get("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("[翻译] API 返回空 choices，回退原文");
                return texts;
            }
            var msg = (Map<String, Object>) choices.get(0).get("message");
            String content = msg != null ? (String) msg.get("content") : null;

            if (content == null || content.isBlank()) {
                log.warn("[翻译] API 返回空内容，回退原文");
                return texts;
            }

            // 解析带编号的翻译结果
            List<String> translated = parseNumberedTranslations(content, texts.size());

            long elapsed = System.currentTimeMillis() - startMs;
            log.info("[翻译] 批量翻译完成，共 {} 段，耗时 {} ms", texts.size(), elapsed);

            return translated;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            log.error("[翻译] ⚠️ 批量翻译失败！耗时 {} ms", elapsed, e);
            // 打印根因链（ResourceAccessException 通常包裹了更具体的异常）
            Throwable root = e;
            while (root.getCause() != null) { root = root.getCause(); }
            log.error("[翻译] 根本原因: {} | 消息: {}", root.getClass().getSimpleName(), root.getMessage());
            log.error("[翻译] 诊断信息: API Key 前6位={} | 模型={} | 目标URL=dashscope.aliyuncs.com | 文本数={}",
                    apiKey == null ? "null" : apiKey.substring(0, Math.min(6, apiKey.length())),
                    model, texts.size());
            return texts; // 全部回退为原文
        }
    }

    /**
     * 解析 LLM 返回的带编号翻译结果：[1] 翻译1\n[2] 翻译2\n ...
     * 如果某行解析失败或缺失，对应位置使用原文。
     */
    private static List<String> parseNumberedTranslations(String content, int expectedCount) {
        String[] lines = content.split("\n");
        String[] results = new String[expectedCount];

        for (String line : lines) {
            line = line.trim();
            // 匹配 [数字] 翻译文本
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("^\\[(\\d+)\\]\\s*(.+)$").matcher(line);
            if (m.find()) {
                int idx = Integer.parseInt(m.group(1)) - 1; // 转为 0-based
                if (idx >= 0 && idx < expectedCount) {
                    results[idx] = m.group(2).trim();
                }
            }
        }

        // 缺失的位置用 null 标记（调用方会回退为原文）
        return IntStream.range(0, expectedCount)
                .mapToObj(i -> results[i] != null ? results[i] : null)
                .toList();
    }
}
