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
    private boolean fallbackEnabled = true;

    public List<ApiEndpointConfig> getApiConfigs() {
        return apiConfigs;
    }

    public void setApiConfigs(List<ApiEndpointConfig> apiConfigs) {
        this.apiConfigs = apiConfigs;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getScreenshotDirectory() {
        return screenshotDirectory;
    }

    public void setScreenshotDirectory(String screenshotDirectory) {
        this.screenshotDirectory = screenshotDirectory;
    }

    public String getScrollUpHotkey() {
        return scrollUpHotkey;
    }

    public void setScrollUpHotkey(String scrollUpHotkey) {
        this.scrollUpHotkey = scrollUpHotkey;
    }

    public String getScrollDownHotkey() {
        return scrollDownHotkey;
    }

    public void setScrollDownHotkey(String scrollDownHotkey) {
        this.scrollDownHotkey = scrollDownHotkey;
    }

    public String getModelSwitchHotkey() {
        return modelSwitchHotkey;
    }

    public void setModelSwitchHotkey(String modelSwitchHotkey) {
        this.modelSwitchHotkey = modelSwitchHotkey;
    }

    public int getPreferredApiIndex() {
        return preferredApiIndex;
    }

    public void setPreferredApiIndex(int preferredApiIndex) {
        this.preferredApiIndex = preferredApiIndex;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }
}
