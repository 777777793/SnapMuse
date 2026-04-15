package com.snapmuse.app.model;

public class ApiEndpointConfig {

    private String name;
    private String baseUrl;
    private String apiKey;
    private String model;

    public ApiEndpointConfig() {
    }

    public ApiEndpointConfig(String name, String baseUrl, String apiKey, String model) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
