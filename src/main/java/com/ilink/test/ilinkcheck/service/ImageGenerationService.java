package com.ilink.test.ilinkcheck.service;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.ilink.test.ilinkcheck.processor.TypingIndicator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ImageGenerationService {

    private static final String SUBMIT_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";
    private static final String TASK_RESULT_URL_TEMPLATE = "https://dashscope.aliyuncs.com/api/v1/tasks/{task_id}";
    private static final int MAX_POLL_COUNT = 30;
    private static final long POLL_INTERVAL_MS = 2000L;

    private final TypingIndicator typingIndicator;
    private final RestClient restClient;

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    public ImageGenerationService(TypingIndicator typingIndicator) {
        this.typingIndicator = typingIndicator;
        this.restClient = RestClient.builder().build();
    }

    public void generateAndSend(ILinkClient ilinkClient, String fromUserId, String userPrompt) {
        typingIndicator.start(ilinkClient, fromUserId);

        try {
            log.info("Received image generation request. prompt={}", userPrompt);

            String taskId = submitTask(userPrompt);
            String imageUrl = pollImageUrl(taskId);
            byte[] imageBytes = downloadImage(imageUrl);

            ilinkClient.sendImage(fromUserId, imageBytes, "wanx.jpg", "图片生成成功。");
            log.info("Wanx image generation succeeded. taskId={}, imageUrl={}", taskId, imageUrl);
        }
        catch (Exception e) {
            log.error("Wanx image generation failed", e);
            try {
                ilinkClient.sendText(fromUserId, "图片生成失败，请稍后再试。");
            }
            catch (Exception ignored) {
            }
        }
        finally {
            typingIndicator.stop(ilinkClient, fromUserId);
        }
    }

    private String submitTask(String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", "wanx-v1",
                "input", Map.of("prompt", userPrompt),
                "parameters", Map.of(
                        "size", "1024*1024",
                        "n", 1
                )
        );

        Map<String, Object> response = restClient.post()
                .uri(SUBMIT_URL)
                .header("Authorization", bearerToken())
                .header("X-DashScope-Async", "enable")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        String taskId = extractTaskId(response);
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalStateException("DashScope did not return a task id: " + response);
        }
        return taskId;
    }

    private String pollImageUrl(String taskId) throws InterruptedException {
        for (int i = 1; i <= MAX_POLL_COUNT; i++) {
            Map<String, Object> response = restClient.get()
                    .uri(TASK_RESULT_URL_TEMPLATE, taskId)
                    .header("Authorization", bearerToken())
                    .retrieve()
                    .body(Map.class);

            Map<String, Object> output = getMap(response, "output");
            String taskStatus = getString(output, "task_status");
            if (taskStatus == null) {
                taskStatus = getString(output, "taskStatus");
            }

            log.info("Wanx task polling. taskId={}, attempt={}, status={}", taskId, i, taskStatus);

            if ("SUCCEEDED".equalsIgnoreCase(taskStatus)) {
                String imageUrl = extractImageUrl(output);
                if (imageUrl == null || imageUrl.isBlank()) {
                    throw new IllegalStateException("DashScope task succeeded but no image URL was returned: " + response);
                }
                return imageUrl;
            }

            if ("FAILED".equalsIgnoreCase(taskStatus) || "CANCELED".equalsIgnoreCase(taskStatus)) {
                throw new IllegalStateException("DashScope task failed: " + response);
            }

            Thread.sleep(POLL_INTERVAL_MS);
        }

        throw new IllegalStateException("Timed out waiting for DashScope image generation task: " + taskId);
    }

    private byte[] downloadImage(String imageUrl) throws IOException {
        try (var inputStream = URI.create(imageUrl).toURL().openStream()) {
            return inputStream.readAllBytes();
        }
    }

    private String extractTaskId(Map<String, Object> response) {
        Map<String, Object> output = getMap(response, "output");
        String taskId = getString(output, "task_id");
        if (taskId == null) {
            taskId = getString(output, "taskId");
        }
        return taskId;
    }

    private String extractImageUrl(Map<String, Object> output) {
        Object resultsObject = output == null ? null : output.get("results");
        if (!(resultsObject instanceof List<?> results) || results.isEmpty()) {
            return null;
        }

        Object first = results.getFirst();
        if (!(first instanceof Map<?, ?> firstMap)) {
            return null;
        }

        Object url = firstMap.get("url");
        return url instanceof String value ? value : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private String getString(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        return value instanceof String text ? text : null;
    }

    private String bearerToken() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("spring.ai.dashscope.api-key is blank");
        }
        return "Bearer " + apiKey;
    }
}
