package com.snapmuse.app.model;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {

    private List<ApiEndpointConfig> apiConfigs = new ArrayList<>();
    private String systemPrompt;
    private String screenshotDirectory;
    private String scrollUpHotkey;
    private String scrollDownHotkey;
    private String modelSwitchHotkey;
    private int preferredApiIndex;
    /** 参与降级的接口索引，按优先顺序排列。空或只有1个 = 不降级。 */
    private List<Integer> fallbackIndexes = new ArrayList<>();

    public List<ApiEndpointConfig> getApiConfigs() { return apiConfigs; }
    public void setApiConfigs(List<ApiEndpointConfig> apiConfigs) { this.apiConfigs = apiConfigs; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getScreenshotDirectory() { return screenshotDirectory; }
    public void setScreenshotDirectory(String screenshotDirectory) { this.screenshotDirectory = screenshotDirectory; }

    public String getScrollUpHotkey() { return scrollUpHotkey; }
    public void setScrollUpHotkey(String scrollUpHotkey) { this.scrollUpHotkey = scrollUpHotkey; }

    public String getScrollDownHotkey() { return scrollDownHotkey; }
    public void setScrollDownHotkey(String scrollDownHotkey) { this.scrollDownHotkey = scrollDownHotkey; }

    public String getModelSwitchHotkey() { return modelSwitchHotkey; }
    public void setModelSwitchHotkey(String modelSwitchHotkey) { this.modelSwitchHotkey = modelSwitchHotkey; }

    public int getPreferredApiIndex() { return preferredApiIndex; }
    public void setPreferredApiIndex(int preferredApiIndex) { this.preferredApiIndex = preferredApiIndex; }

    public List<Integer> getFallbackIndexes() { return fallbackIndexes; }
    public void setFallbackIndexes(List<Integer> fallbackIndexes) {
        this.fallbackIndexes = fallbackIndexes == null ? new ArrayList<>() : fallbackIndexes;
    }

    /** 兼容旧配置文件：忽略旧字段，不报错。 */
    public void setFallbackEnabled(boolean ignored) {}
    public boolean isFallbackEnabled() { return fallbackIndexes != null && fallbackIndexes.size() > 1; }
}
