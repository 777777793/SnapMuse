package com.snapmuse.app.service;

import java.awt.Point;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SelectionService {

    private static final Logger log = LoggerFactory.getLogger(SelectionService.class);
    private final RuntimeStateService runtimeStateService;
    private final ScreenshotService screenshotService;
    private final ConversationService conversationService;
    private final Object lock = new Object();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private Point firstPoint;
    private volatile String pendingQuestion;
    private ScheduledFuture<?> firstPointTimeoutTask;

    public SelectionService(
            RuntimeStateService runtimeStateService,
            ScreenshotService screenshotService,
            ConversationService conversationService) {
        this.runtimeStateService = runtimeStateService;
        this.screenshotService = screenshotService;
        this.conversationService = conversationService;
    }

    /** 前端设置问题（可选，不设置会用默认问题） */
    public void setQuestion(String question) {
        this.pendingQuestion = question;
        log.info("已设置问题: {}", question);
    }

    /** 清除当前选点状态 */
    public void cancel() {
        synchronized (lock) {
            pendingQuestion = null;
            firstPoint = null;
            cancelTimeoutTask();
            log.info("手动取消选点");
        }
        runtimeStateService.selectionCancelled("已取消");
    }

    /** 是否可以接受选点（AI忙时不接受） */
    public boolean canAcceptPoint() {
        return !runtimeStateService.getState().isAiBusy();
    }

    public void acceptPoint(Point point) {
        Point first;
        String question;
        String displayText;
        synchronized (lock) {
            if (runtimeStateService.getState().isAiBusy()) {
                log.info("AI处理中，忽略选点 ({}, {})", point.x, point.y);
                return;
            }
            if (firstPoint == null) {
                firstPoint = point;
                log.info("已选择第一个点 ({}, {})", point.x, point.y);
                scheduleFirstPointTimeout(point);
                runtimeStateService.firstPointCaptured();
                return;
            }
            first = firstPoint;
            question = pendingQuestion;
            displayText = buildDisplayText(question);
            pendingQuestion = null;
            firstPoint = null;
            cancelTimeoutTask();
            log.info("已选择第二个点 ({}, {})，区域 ({},{}) -> ({},{})", point.x, point.y, first.x, first.y, point.x, point.y);
        }

        log.info("开始截图问AI，用户问题: {}, 对话显示内容: {}", question == null ? "" : question, displayText);
        runtimeStateService.screenshotSubmitting();
        try {
            conversationService.askWithScreenshot(question, displayText, screenshotService.capture(first, point));
        } catch (Exception ex) {
            log.error("截图或AI提交失败", ex);
            runtimeStateService.aiFinished("截图失败：" + ex.getMessage());
        }
    }

    private void scheduleFirstPointTimeout(Point recordedPoint) {
        cancelTimeoutTask();
        firstPointTimeoutTask = scheduler.schedule(() -> {
            synchronized (lock) {
                if (firstPoint != null && firstPoint.equals(recordedPoint)) {
                    log.info("第一个点超时已清除 ({}, {})", recordedPoint.x, recordedPoint.y);
                    firstPoint = null;
                    runtimeStateService.resetFirstPointForTimeout();
                }
            }
        }, 5, TimeUnit.SECONDS);
    }

    private void cancelTimeoutTask() {
        if (firstPointTimeoutTask != null) {
            firstPointTimeoutTask.cancel(false);
            firstPointTimeoutTask = null;
        }
    }

    private String buildDisplayText(String question) {
        if (question != null && !question.isBlank()) {
            return question.trim();
        }
        return "已发送截图";
    }
}
