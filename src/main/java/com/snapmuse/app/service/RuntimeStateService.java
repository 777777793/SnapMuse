package com.snapmuse.app.service;

import com.snapmuse.app.model.AppState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RuntimeStateService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeStateService.class);
    private final EventStreamService eventStreamService;
    private final Object lock = new Object();
    private final AppState state = new AppState();

    public RuntimeStateService(EventStreamService eventStreamService) {
        this.eventStreamService = eventStreamService;
        state.setStatusMessage("就绪");
        state.setLampState("OFF");
    }

    public AppState getState() {
        synchronized (lock) {
            return copy(state);
        }
    }

    public void updateHookReady(boolean ready, String statusMessage) {
        synchronized (lock) {
            state.setHookReady(ready);
            state.setStatusMessage(statusMessage);
        }
        publish();
    }

    public void firstPointCaptured() {
        synchronized (lock) {
            state.setWaitingForSecondPoint(true);
            state.setStatusMessage("已选第一个点");
            state.setLampState("YELLOW");
        }
        publish();
    }

    public void resetFirstPointForTimeout() {
        synchronized (lock) {
            state.setWaitingForSecondPoint(false);
            state.setStatusMessage("超时已清除");
            state.setLampState("OFF");
        }
        publish();
    }

    public void screenshotSubmitting() {
        synchronized (lock) {
            state.setWaitingForSecondPoint(false);
            state.setAiBusy(true);
            state.setStatusMessage("截图中，正在问AI");
            state.setLampState("GREEN");
        }
        publish();
    }

    public void selectionCancelled(String statusMessage) {
        synchronized (lock) {
            state.setWaitingForSecondPoint(false);
            state.setStatusMessage(statusMessage);
            state.setLampState("OFF");
        }
        publish();
    }

    public void aiStarted(String statusMessage) {
        synchronized (lock) {
            state.setAiBusy(true);
            state.setStatusMessage(statusMessage);
            state.setLampState("GREEN");
        }
        publish();
    }

    public void aiFinished(String statusMessage) {
        synchronized (lock) {
            state.setAiBusy(false);
            state.setPendingQuestion(null);
            state.setStatusMessage(statusMessage);
            state.setLampState("OFF");
        }
        publish();
    }

    public void hotkeyTriggered(String direction) {
        synchronized (lock) {
            state.setLastHotkeyDirection(direction);
            state.setLastHotkeyAt(System.currentTimeMillis());
        }
        publish();
    }

    public void publish() {
        AppState snapshot = getState();
        log.info("状态变更: lamp={}, msg={}", snapshot.getLampState(), snapshot.getStatusMessage());
        eventStreamService.publish("state", snapshot);
    }

    private AppState copy(AppState source) {
        AppState copy = new AppState();
        copy.setHookReady(source.isHookReady());
        copy.setSelectionActive(source.isSelectionActive());
        copy.setWaitingForSecondPoint(source.isWaitingForSecondPoint());
        copy.setAiBusy(source.isAiBusy());
        copy.setPendingQuestion(source.getPendingQuestion());
        copy.setStatusMessage(source.getStatusMessage());
        copy.setLastHotkeyDirection(source.getLastHotkeyDirection());
        copy.setLastHotkeyAt(source.getLastHotkeyAt());
        copy.setLampState(source.getLampState());
        return copy;
    }
}
