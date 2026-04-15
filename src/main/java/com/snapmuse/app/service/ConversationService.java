package com.snapmuse.app.service;

import com.snapmuse.app.model.ChatMessage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final ChatHistoryService chatHistoryService;
    private final AiService aiService;
    private final RuntimeStateService runtimeStateService;
    private final Executor executor = Executors.newCachedThreadPool();

    public ConversationService(ChatHistoryService chatHistoryService, AiService aiService, RuntimeStateService runtimeStateService) {
        this.chatHistoryService = chatHistoryService;
        this.aiService = aiService;
        this.runtimeStateService = runtimeStateService;
    }

    public void askText(String question) {
        ChatMessage userMessage = chatHistoryService.addMessage(new ChatMessage("user", question, "done", null));
        ChatMessage assistantMessage = chatHistoryService.addMessage(new ChatMessage("assistant", "正在思考中...", "pending", null));
        runtimeStateService.aiStarted("问题已提交，正在等待 AI 回答");
        runConversation(question, null, assistantMessage, userMessage.getScreenshotPath());
    }

    public void askWithScreenshot(String question, String displayText, Path screenshotPath) {
        ChatMessage userMessage = chatHistoryService.addMessage(new ChatMessage("user", displayText, "done", screenshotPath.toString()));
        ChatMessage assistantMessage = chatHistoryService.addMessage(new ChatMessage("assistant", "正在分析截图...", "pending", null));
        runtimeStateService.aiStarted("截图与问题已提交，正在等待 AI 回答");
        runConversation(question, screenshotPath, assistantMessage, userMessage.getScreenshotPath());
    }

    private void runConversation(String question, Path screenshotPath, ChatMessage assistantMessage, String screenshotDisplayPath) {
        CompletableFuture.runAsync(() -> {
            try {
                String answer = aiService.ask(question, screenshotPath);
                assistantMessage.setContent(answer);
                assistantMessage.setStatus("done");
                chatHistoryService.updateMessage(assistantMessage);
                runtimeStateService.aiFinished("AI 回答完成");
            } catch (Exception ex) {
                assistantMessage.setContent("AI 调用失败：\n" + ex.getMessage());
                assistantMessage.setStatus("error");
                chatHistoryService.updateMessage(assistantMessage);
                runtimeStateService.aiFinished("AI 调用失败，请检查设置");
            }
        }, executor);
    }
}
