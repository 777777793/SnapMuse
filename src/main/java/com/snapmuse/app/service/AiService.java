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
import java.util.function.Consumer;
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

    /**
     * @param onChunk         每收到一段文字时回调
     * @param onModelResolved 确定由哪个接口回答时回调（接口名称）
     * @param onFallback      发生降级时回调（提示文字）
     * @return 实际使用的接口名称
     */
    public String ask(String question, Path imagePath,
                      Consumer<String> onChunk,
                      Consumer<String> onModelResolved,
                      Consumer<String> onFallback) throws IOException, InterruptedException {
        AppConfig config = configService.getConfig();
        String effectiveSystemPrompt = normalizeSystemPrompt(config.getSystemPrompt());
        String trimmedQuestion = question == null ? "" : question.trim();
        String imageDataUrl = encodeImageDataUrl(imagePath);
        List<String> failures = new ArrayList<>();
        List<Integer> candidates = resolveCandidateIndexes(config);

        boolean firstAttempt = true;
        for (int candidateIndex : candidates) {
            ApiEndpointConfig endpoint = config.getApiConfigs().get(candidateIndex);
            if (!isUsable(endpoint)) {
                String label = buildEndpointLabel(endpoint, candidateIndex);
                failures.add(label + ": 当前模型配置不完整");
                log.warn("跳过未配置的接口 index={}, label={}", candidateIndex, label);
                if (!firstAttempt) {
                    onFallback.accept("接口配置不完整，继续降级…");
                }
                firstAttempt = false;
                continue;
            }
            if (!firstAttempt) {
                String label = buildEndpointLabel(endpoint, candidateIndex);
                log.info("降级切换至接口 index={}, label={}", candidateIndex, label);
                onFallback.accept("正在降级切换至：" + label);
            }
            firstAttempt = false;
            try {
                log.info("Preparing AI request. endpointName={}, model={}, hasImage={}",
                        safe(endpoint.getName()), safe(endpoint.getModel()), imagePath != null);
                String resolvedName = buildEndpointLabel(endpoint, candidateIndex);
                onModelResolved.accept(resolvedName);
                doStreamRequest(endpoint, effectiveSystemPrompt, trimmedQuestion, imageDataUrl, onChunk);
                return resolvedName;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (Exception ex) {
                log.warn("AI request failed on endpoint {}: {}", safe(endpoint.getName()), ex.getMessage());
                failures.add(buildEndpointLabel(endpoint, candidateIndex) + ": " + ex.getMessage());
                if (candidates.size() > 1) {
                    onFallback.accept("接口出错，正在降级切换…");
                }
            }
        }
        if (failures.isEmpty()) {
            throw new IllegalStateException("请先在设置里至少配置一组可用的 API 地址、Key 和模型");
        }
        throw new IllegalStateException("所有 API 调用都失败了\n" + String.join("\n", failures));
    }

    private void doStreamRequest(ApiEndpointConfig endpoint, String systemPrompt, String question,
                                 String imageDataUrl, Consumer<String> onChunk)
            throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", endpoint.getModel());
        payload.put("stream", true);
        payload.put("messages", buildMessages(systemPrompt, question, imageDataUrl));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveEndpointUrl(endpoint.getBaseUrl())))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + endpoint.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = response.body().reduce("", (a, b) -> a + b);
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + body);
        }

        StringBuilder fullContent = new StringBuilder();
        for (String line : (Iterable<String>) response.body()::iterator) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("stopped by user");
            }
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JsonNode delta = objectMapper.readTree(data)
                            .path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull()) {
                        String chunk = delta.asText();
                        if (!chunk.isEmpty()) {
                            fullContent.append(chunk);
                            onChunk.accept(chunk);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skip unparseable SSE line: {}", line);
                }
            }
        }
        if (fullContent.isEmpty()) {
            throw new IllegalStateException("接口返回成功，但没有解析到回答内容");
        }
    }

    private List<Map<String, Object>> buildMessages(String systemPrompt, String question, String imageDataUrl) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        String trimmed = question == null ? "" : question.trim();
        if (imageDataUrl == null) {
            messages.add(Map.of("role", "user", "content", trimmed));
            return messages;
        }
        List<Map<String, Object>> userContent = new ArrayList<>();
        if (!trimmed.isBlank()) userContent.add(Map.of("type", "text", "text", trimmed));
        userContent.add(Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl)));
        messages.add(Map.of("role", "user", "content", userContent));
        return messages;
    }

    private String encodeImageDataUrl(Path imagePath) throws IOException {
        if (imagePath == null) return null;
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
    }

    private String normalizeSystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) return ConfigService.DEFAULT_SYSTEM_PROMPT;
        return systemPrompt.trim();
    }

    private String resolveEndpointUrl(String baseUrl) {
        String t = baseUrl == null ? "" : baseUrl.trim();
        if (t.endsWith("/chat/completions")) return t;
        if (t.endsWith("/")) return t + "chat/completions";
        return t + "/chat/completions";
    }

    private boolean isUsable(ApiEndpointConfig e) {
        return e != null
                && e.getBaseUrl() != null && !e.getBaseUrl().isBlank()
                && e.getApiKey() != null && !e.getApiKey().isBlank()
                && e.getModel() != null && !e.getModel().isBlank();
    }

    private List<Integer> resolveCandidateIndexes(AppConfig config) {
        int size = config.getApiConfigs() == null ? 0 : config.getApiConfigs().size();
        if (size == 0) return List.of();

        List<Integer> fallback = config.getFallbackIndexes();
        if (fallback != null && fallback.size() > 1) {
            // 有多个降级接口：按顺序使用，过滤越界索引
            List<Integer> valid = new ArrayList<>();
            for (int idx : fallback) {
                if (idx >= 0 && idx < size && !valid.contains(idx)) valid.add(idx);
            }
            if (!valid.isEmpty()) return valid;
        }
        // 无降级：只用当前选中的接口
        int preferred = Math.max(0, Math.min(config.getPreferredApiIndex(), size - 1));
        return List.of(preferred);
    }

    private String buildEndpointLabel(ApiEndpointConfig endpoint, int index) {
        if (endpoint != null && endpoint.getName() != null && !endpoint.getName().isBlank()) {
            return endpoint.getName().trim();
        }
        return "接口 " + (index + 1);
    }

    private String safe(String value) { return value == null ? "" : value; }
}
