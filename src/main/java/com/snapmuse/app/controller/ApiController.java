package com.snapmuse.app.controller;

import com.snapmuse.app.dto.AskRequest;
import com.snapmuse.app.dto.StartSelectionRequest;
import com.snapmuse.app.model.AppConfig;
import com.snapmuse.app.service.ChatHistoryService;
import com.snapmuse.app.service.ConfigService;
import com.snapmuse.app.service.ConversationService;
import com.snapmuse.app.service.EventStreamService;
import com.snapmuse.app.service.RuntimeStateService;
import com.snapmuse.app.service.SelectionService;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ConfigService configService;
    private final RuntimeStateService runtimeStateService;
    private final ChatHistoryService chatHistoryService;
    private final SelectionService selectionService;
    private final ConversationService conversationService;
    private final EventStreamService eventStreamService;

    public ApiController(
            ConfigService configService,
            RuntimeStateService runtimeStateService,
            ChatHistoryService chatHistoryService,
            SelectionService selectionService,
            ConversationService conversationService,
            EventStreamService eventStreamService) {
        this.configService = configService;
        this.runtimeStateService = runtimeStateService;
        this.chatHistoryService = chatHistoryService;
        this.selectionService = selectionService;
        this.conversationService = conversationService;
        this.eventStreamService = eventStreamService;
    }

    @GetMapping("/config")
    public AppConfig getConfig() {
        return configService.getConfig();
    }

    @PostMapping("/config")
    public AppConfig saveConfig(@RequestBody AppConfig config) {
        try {
            return configService.saveConfig(config);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存配置失败: " + ex.getMessage(), ex);
        }
    }

    @GetMapping("/state")
    public Object getState() {
        return runtimeStateService.getState();
    }

    @GetMapping("/history")
    public Object getHistory() {
        return chatHistoryService.getMessages();
    }

    @PostMapping("/history/clear")
    public Map<String, String> clearHistory() {
        chatHistoryService.clearMessages();
        return Map.of("message", "已清空对话记录");
    }

    @GetMapping("/events")
    public SseEmitter subscribeEvents() {
        return eventStreamService.subscribe();
    }

    @PostMapping("/selection/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> setQuestion(@RequestBody StartSelectionRequest request) {
        String q = request.getQuestion() == null ? "" : request.getQuestion().trim();
        selectionService.setQuestion(q);
        return Map.of("message", "已设置问题");
    }

    @PostMapping("/selection/cancel")
    public Map<String, String> cancelSelection() {
        selectionService.cancel();
        return Map.of("message", "已取消");
    }

    @PostMapping("/chat/ask")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> askText(@RequestBody AskRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先输入问题");
        }
        conversationService.askText(request.getQuestion().trim());
        return Map.of("message", "已提交给 AI");
    }
}
