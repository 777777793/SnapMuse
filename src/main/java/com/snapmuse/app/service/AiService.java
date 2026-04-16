package com.snapmuse.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapmuse.app.model.ApiEndpointConfig;
import com.snapmuse.app.model.AppConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiService(ConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String ask(String question, Path imagePath) throws IOException, InterruptedException {
        AppConfig config = configService.getConfig();
        String effectiveSystemPrompt = normalizeSystemPrompt(config.getSystemPrompt());
        String trimmedQuestion = question == null ? "" : question.trim();
        String imageDataUrl = encodeImageDataUrl(imagePath);
        List<String> failures = new ArrayList<>();
        for (ApiEndpointConfig endpoint : config.getApiConfigs()) {
            if (!isUsable(endpoint)) {
                continue;
            }
            try {
                log.info("Preparing AI request. endpointName={}, baseUrl={}, model={}, hasImage={}, systemPrompt={}",
                        safe(endpoint.getName()),
                        safe(endpoint.getBaseUrl()),
                        safe(endpoint.getModel()),
                        imagePath != null,
                        preview(effectiveSystemPrompt, 240));
                return invoke(endpoint, effectiveSystemPrompt, trimmedQuestion, imageDataUrl, imagePath);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (Exception ex) {
                log.warn("AI request failed on endpoint {}: {}", safe(endpoint.getName()), ex.getMessage());
                failures.add((endpoint.getName() == null || endpoint.getName().isBlank() ? "未命名接口" : endpoint.getName())
                        + ": " + ex.getMessage());
            }
        }
        if (failures.isEmpty()) {
            throw new IllegalStateException("请先在设置里至少配置一组可用的 API 地址、Key 和模型");
        }
        throw new IllegalStateException("所有 API 调用都失败了\n" + String.join("\n", failures));
    }

    private String invoke(ApiEndpointConfig endpoint, String systemPrompt, String question, String imageDataUrl, Path imagePath)
            throws IOException, InterruptedException {
        return doRequest(endpoint, systemPrompt, question, imageDataUrl, imagePath);
    }

    private String doRequest(ApiEndpointConfig endpoint, String systemPrompt, String question, String imageDataUrl, Path imagePath)
            throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", endpoint.getModel());
        List<Map<String, Object>> messages = buildMessages(systemPrompt, question, imageDataUrl);
        payload.put("messages", messages);

        log.info("Sending AI payload. endpointName={}, requestUrl={}, model={}, systemPrompt={}, userQuestion={}, imagePath={}, messages={}",
                safe(endpoint.getName()),
                resolveEndpointUrl(endpoint.getBaseUrl()),
                safe(endpoint.getModel()),
                preview(systemPrompt, 240),
                preview(question, 240),
                imagePath == null ? "" : imagePath.toString(),
                objectMapper.writeValueAsString(sanitizeMessagesForLog(messages)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveEndpointUrl(endpoint.getBaseUrl())))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + endpoint.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        String content = extractContent(contentNode);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("接口返回成功，但没有解析到回答内容");
        }
        return content.trim();
    }

    private List<Map<String, Object>> buildMessages(String systemPrompt, String question, String imageDataUrl) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        String trimmedQuestion = question == null ? "" : question.trim();

        if (imageDataUrl == null) {
            messages.add(Map.of("role", "user", "content", trimmedQuestion));
            return messages;
        }

        List<Map<String, Object>> userContent = new ArrayList<>();
        if (!trimmedQuestion.isBlank()) {
            userContent.add(Map.of("type", "text", "text", trimmedQuestion));
        }
        userContent.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", imageDataUrl)
        ));
        messages.add(Map.of("role", "user", "content", userContent));
        return messages;
    }

    private String encodeImageDataUrl(Path imagePath) throws IOException {
        if (imagePath == null) {
            return null;
        }
        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
        return "data:image/png;base64," + base64;
    }

    private String normalizeSystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return ConfigService.DEFAULT_SYSTEM_PROMPT;
        }
        return systemPrompt.trim();
    }

    private String extractContent(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : node) {
                if ("text".equals(item.path("type").asText())) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(item.path("text").asText(""));
                }
            }
            return builder.toString();
        }
        return node.toString();
    }

    private String resolveEndpointUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed + "chat/completions";
        }
        return trimmed + "/chat/completions";
    }

    private boolean isUsable(ApiEndpointConfig endpoint) {
        return endpoint != null
                && endpoint.getBaseUrl() != null && !endpoint.getBaseUrl().isBlank()
                && endpoint.getApiKey() != null && !endpoint.getApiKey().isBlank()
                && endpoint.getModel() != null && !endpoint.getModel().isBlank();
    }

    private Object sanitizeMessagesForLog(List<Map<String, Object>> messages) {
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            Object content = message.get("content");
            if (content instanceof String textContent) {
                sanitized.add(Map.of(
                        "role", message.get("role"),
                        "content", preview(textContent, 400)
                ));
                continue;
            }
            if (content instanceof List<?> contentList) {
                List<Object> sanitizedContentList = new ArrayList<>();
                for (Object item : contentList) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Object type = itemMap.get("type");
                        if ("text".equals(type)) {
                            sanitizedContentList.add(Map.of(
                                    "type", "text",
                                    "text", preview(String.valueOf(itemMap.get("text")), 400)
                            ));
                        } else if ("image_url".equals(type)) {
                            sanitizedContentList.add(Map.of(
                                    "type", "image_url",
                                    "image_url", Map.of("url", "[base64-image-omitted]")
                            ));
                        } else {
                            sanitizedContentList.add(Map.of("type", String.valueOf(type)));
                        }
                    }
                }
                sanitized.add(Map.of(
                        "role", message.get("role"),
                        "content", sanitizedContentList
                ));
            }
        }
        return sanitized;
    }

    private String preview(String text, int limit) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() <= limit) {
            return singleLine;
        }
        return singleLine.substring(0, limit) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
