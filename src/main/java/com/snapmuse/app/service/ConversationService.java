package com.snapmuse.app.service;

import com.snapmuse.app.model.ChatMessage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final ChatHistoryService chatHistoryService;
    private final AiService aiService;
    private final RuntimeStateService runtimeStateService;
    private final Executor executor = Executors.newCachedThreadPool();

    private final AtomicReference<Thread> currentThread = new AtomicReference<>();
    private final AtomicReference<Future<?>> currentFuture = new AtomicReference<>();

    public ConversationService(ChatHistoryService chatHistoryService, AiService aiService,
                               RuntimeStateService runtimeStateService) {
        this.chatHistoryService = chatHistoryService;
        this.aiService = aiService;
        this.runtimeStateService = runtimeStateService;
    }

    public void stop() {
        Thread t = currentThread.get();
        if (t != null) t.interrupt();
        Future<?> f = currentFuture.get();
        if (f != null) f.cancel(true);
    }

    public void askText(String question) {
        ChatMessage userMsg = chatHistoryService.addMessage(new ChatMessage("user", question, "done", null));
        ChatMessage assistantMsg = chatHistoryService.addMessage(new ChatMessage("assistant", "", "pending", null));
        runtimeStateService.aiStarted("问题已提交，正在等待 AI 回答");
        runConversation(question, null, assistantMsg);
    }

    public void askWithScreenshot(String question, String displayText, Path screenshotPath) {
        chatHistoryService.addMessage(new ChatMessage("user", displayText, "done", screenshotPath.toString()));
        ChatMessage assistantMsg = chatHistoryService.addMessage(new ChatMessage("assistant", "", "pending", null));
        runtimeStateService.aiStarted("截图与问题已提交，正在等待 AI 回答");
        runConversation(question, screenshotPath, assistantMsg);
    }

    private void runConversation(String question, Path screenshotPath, ChatMessage assistantMsg) {
        Future<?> future = CompletableFuture.runAsync(() -> {
            currentThread.set(Thread.currentThread());
            try {
                String resolvedName = aiService.ask(
                        question,
                        screenshotPath,
                        chunk -> chatHistoryService.appendMessageChunk(assistantMsg, chunk),
                        modelName -> assistantMsg.setModelName(modelName),
                        hint -> chatHistoryService.setStreamingHint(assistantMsg, hint)
                );
                assistantMsg.setModelName(resolvedName);
                assistantMsg.setStatus("done");
                chatHistoryService.finalizeMessage(assistantMsg);
                runtimeStateService.aiFinished("AI 回答完成");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                assistantMsg.setStatus("done");
                chatHistoryService.finalizeMessage(assistantMsg);
                runtimeStateService.aiFinished("已停止");
            } catch (Exception ex) {
                assistantMsg.setContent("AI 调用失败：\n" + ex.getMessage());
                assistantMsg.setStatus("error");
                chatHistoryService.finalizeMessage(assistantMsg);
                runtimeStateService.aiFinished("AI 调用失败，请检查设置");
            } finally {
                currentThread.set(null);
                currentFuture.set(null);
            }
        }, executor);
        currentFuture.set(future);
    }
}
