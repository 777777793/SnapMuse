package com.snapmuse.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapmuse.app.model.ApiEndpointConfig;
import com.snapmuse.app.model.AppConfig;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {

    public static final String DEFAULT_SYSTEM_PROMPT = """
            请根据我发的题目图片答题。先识别题目内容，再按题型作答：
            - 单选题：给正确选项+一句原因
            - 多选题：给全部正确选项+简要判断
            - 场景题：先给结论，再简要分析
            - 编码题：给可运行代码，并简要说明思路
            """.trim();
    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    private static final Pattern SYSTEM_PROMPT_PATTERN = Pattern.compile(
            "(\"systemPrompt\"\\s*:\\s*\")(.*?)(\",\\s*\"(?:screenshotDirectory|scrollUpHotkey|scrollDownHotkey)\")",
            Pattern.DOTALL);
    private final ObjectMapper objectMapper;
    private final Object lock = new Object();
    private final Path appRoot = Paths.get("").toAbsolutePath();
    private final Path dataDirectory = appRoot.resolve("snapmuse-data");
    private final Path configFile = dataDirectory.resolve("config.json");
    private AppConfig currentConfig;

    public ConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(dataDirectory);
        if (Files.exists(configFile)) {
            currentConfig = loadConfigWithRepair();
        } else {
            currentConfig = defaultConfig();
            persist(currentConfig);
        }
        ensureFixedApiSlots(currentConfig);
        ensureScreenshotDirectory(currentConfig.getScreenshotDirectory());
    }

    public AppConfig getConfig() {
        synchronized (lock) {
            reloadConfigFromDiskQuietly();
            return objectMapper.convertValue(currentConfig, AppConfig.class);
        }
    }

    public AppConfig saveConfig(AppConfig config) throws IOException {
        synchronized (lock) {
            ensureFixedApiSlots(config);
            if (config.getSystemPrompt() == null || config.getSystemPrompt().isBlank()) {
                config.setSystemPrompt(defaultConfig().getSystemPrompt());
            }
            if (config.getScreenshotDirectory() == null || config.getScreenshotDirectory().isBlank()) {
                config.setScreenshotDirectory(defaultConfig().getScreenshotDirectory());
            }
            if (config.getScrollUpHotkey() == null || config.getScrollUpHotkey().isBlank()) {
                config.setScrollUpHotkey("ALT+UP");
            }
            if (config.getScrollDownHotkey() == null || config.getScrollDownHotkey().isBlank()) {
                config.setScrollDownHotkey("ALT+DOWN");
            }
            config.setSystemPrompt(config.getSystemPrompt().trim());
            config.setScreenshotDirectory(config.getScreenshotDirectory().trim());
            config.setScrollUpHotkey(config.getScrollUpHotkey().trim());
            config.setScrollDownHotkey(config.getScrollDownHotkey().trim());
            ensureScreenshotDirectory(config.getScreenshotDirectory());
            currentConfig = objectMapper.convertValue(config, AppConfig.class);
            persist(currentConfig);
            log.info("Config saved. screenshotDirectory={}, scrollUpHotkey={}, scrollDownHotkey={}, systemPrompt={}",
                    currentConfig.getScreenshotDirectory(),
                    currentConfig.getScrollUpHotkey(),
                    currentConfig.getScrollDownHotkey(),
                    preview(currentConfig.getSystemPrompt(), 200));
            return objectMapper.convertValue(currentConfig, AppConfig.class);
        }
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    private void persist(AppConfig config) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), config);
    }

    private void reloadConfigFromDiskQuietly() {
        if (!Files.exists(configFile)) {
            return;
        }
        try {
            currentConfig = loadConfigWithRepair();
            ensureFixedApiSlots(currentConfig);
            ensureScreenshotDirectory(currentConfig.getScreenshotDirectory());
        } catch (IOException ex) {
            log.warn("重新加载配置文件失败，继续使用内存中的配置: {}", ex.getMessage());
        }
    }

    private AppConfig loadConfigWithRepair() throws IOException {
        try {
            return objectMapper.readValue(configFile.toFile(), AppConfig.class);
        } catch (IOException ex) {
            String raw = Files.readString(configFile, StandardCharsets.UTF_8);
            String repaired = repairBrokenSystemPrompt(raw);
            if (repaired.equals(raw)) {
                throw ex;
            }
            log.warn("检测到配置文件中的 systemPrompt JSON 格式损坏，已自动修复后重新加载");
            AppConfig repairedConfig = objectMapper.readValue(repaired, AppConfig.class);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configFile.toFile(), repairedConfig);
            return repairedConfig;
        }
    }

    private String repairBrokenSystemPrompt(String rawJson) {
        Matcher matcher = SYSTEM_PROMPT_PATTERN.matcher(rawJson);
        if (!matcher.find()) {
            return rawJson;
        }
        String repairedPrompt = escapeJsonStringContent(matcher.group(2));
        return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + repairedPrompt + matcher.group(3)));
    }

    private String escapeJsonStringContent(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }

    private AppConfig defaultConfig() {
        AppConfig config = new AppConfig();
        List<ApiEndpointConfig> endpoints = new ArrayList<>();
        endpoints.add(new ApiEndpointConfig("主接口", "https://api.openai.com/v1", "", "gpt-4.1-mini"));
        endpoints.add(new ApiEndpointConfig("兜底接口 1", "https://api.openai.com/v1", "", "gpt-4.1-mini"));
        endpoints.add(new ApiEndpointConfig("兜底接口 2", "https://api.openai.com/v1", "", "gpt-4.1-mini"));
        config.setApiConfigs(endpoints);
        config.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        config.setScreenshotDirectory(appRoot.resolve("snapmuse-data").resolve("screenshots").toString());
        config.setScrollUpHotkey("ALT+UP");
        config.setScrollDownHotkey("ALT+DOWN");
        return config;
    }

    private void ensureScreenshotDirectory(String directory) throws IOException {
        Files.createDirectories(Path.of(directory));
    }

    private void ensureFixedApiSlots(AppConfig config) {
        if (config.getApiConfigs() == null) {
            config.setApiConfigs(new ArrayList<>());
        }
        while (config.getApiConfigs().size() < 3) {
            config.getApiConfigs().add(new ApiEndpointConfig("接口 " + (config.getApiConfigs().size() + 1), "", "", ""));
        }
        if (config.getApiConfigs().size() > 3) {
            config.setApiConfigs(new ArrayList<>(config.getApiConfigs().subList(0, 3)));
        }
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
}
