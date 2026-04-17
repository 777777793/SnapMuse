package com.snapmuse.app.model;

import java.time.Instant;
import java.util.UUID;

public class ChatMessage {

    private String id = UUID.randomUUID().toString();
    private String role;
    private String content;
    private String status;
    private String screenshotPath;
    private String modelName;
    private String streamingHint;
    private Instant createdAt = Instant.now();

    public ChatMessage() {
    }

    public ChatMessage(String role, String content, String status, String screenshotPath) {
        this.role = role;
        this.content = content;
        this.status = status;
        this.screenshotPath = screenshotPath;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getScreenshotPath() {
        return screenshotPath;
    }

    public void setScreenshotPath(String screenshotPath) {
        this.screenshotPath = screenshotPath;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getStreamingHint() {
        return streamingHint;
    }

    public void setStreamingHint(String streamingHint) {
        this.streamingHint = streamingHint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
