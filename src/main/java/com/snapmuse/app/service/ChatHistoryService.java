package com.snapmuse.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapmuse.app.model.ChatMessage;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
    private final ConfigService configService;
    private final EventStreamService eventStreamService;
    private final ObjectMapper objectMapper;
    private final Object lock = new Object();
    private Path historyFile;
    private final List<ChatMessage> messages = new ArrayList<>();

    public ChatHistoryService(ConfigService configService, EventStreamService eventStreamService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.eventStreamService = eventStreamService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() throws IOException {
        historyFile = configService.getDataDirectory().resolve("history.json");
        if (Files.exists(historyFile)) {
            List<ChatMessage> loaded = objectMapper.readValue(historyFile.toFile(), new TypeReference<>() {
            });
            messages.addAll(loaded);
        } else {
            persist();
        }
    }

    public List<ChatMessage> getMessages() {
        synchronized (lock) {
            return objectMapper.convertValue(messages, new TypeReference<>() {
            });
        }
    }

    public ChatMessage addMessage(ChatMessage message) {
        synchronized (lock) {
            messages.add(message);
            persistQuietly();
        }
        publish();
        return message;
    }

    public void updateMessage(ChatMessage message) {
        synchronized (lock) {
            for (int index = 0; index < messages.size(); index++) {
                if (messages.get(index).getId().equals(message.getId())) {
                    messages.set(index, message);
                    break;
                }
            }
            persistQuietly();
        }
        publish();
    }

    public void clearMessages() {
        synchronized (lock) {
            log.info("Clearing chat history, current size={}", messages.size());
            messages.clear();
            persistQuietly();
        }
        publish();
    }

    private void publish() {
        eventStreamService.publish("history", getMessages());
    }

    private void persistQuietly() {
        try {
            persist();
        } catch (IOException ignored) {
        }
    }

    private void persist() throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(historyFile.toFile(), messages);
    }
}
