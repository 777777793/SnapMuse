package com.snapmuse.app.model;

public class AppState {

    private boolean hookReady;
    private boolean selectionActive;
    private boolean waitingForSecondPoint;
    private boolean aiBusy;
    private String pendingQuestion;
    private String statusMessage;
    private String lastHotkeyDirection;
    private long lastHotkeyAt;
    private String lampState;
    private int activeModelIndex;
    private String activeModelName;
    private String activeModelDisplay;
    private boolean fallbackEnabled;

    public boolean isHookReady() {
        return hookReady;
    }

    public void setHookReady(boolean hookReady) {
        this.hookReady = hookReady;
    }

    public boolean isSelectionActive() {
        return selectionActive;
    }

    public void setSelectionActive(boolean selectionActive) {
        this.selectionActive = selectionActive;
    }

    public boolean isWaitingForSecondPoint() {
        return waitingForSecondPoint;
    }

    public void setWaitingForSecondPoint(boolean waitingForSecondPoint) {
        this.waitingForSecondPoint = waitingForSecondPoint;
    }

    public boolean isAiBusy() {
        return aiBusy;
    }

    public void setAiBusy(boolean aiBusy) {
        this.aiBusy = aiBusy;
    }

    public String getPendingQuestion() {
        return pendingQuestion;
    }

    public void setPendingQuestion(String pendingQuestion) {
        this.pendingQuestion = pendingQuestion;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getLastHotkeyDirection() {
        return lastHotkeyDirection;
    }

    public void setLastHotkeyDirection(String lastHotkeyDirection) {
        this.lastHotkeyDirection = lastHotkeyDirection;
    }

    public long getLastHotkeyAt() {
        return lastHotkeyAt;
    }

    public void setLastHotkeyAt(long lastHotkeyAt) {
        this.lastHotkeyAt = lastHotkeyAt;
    }

    public String getLampState() {
        return lampState;
    }

    public void setLampState(String lampState) {
        this.lampState = lampState;
    }

    public int getActiveModelIndex() {
        return activeModelIndex;
    }

    public void setActiveModelIndex(int activeModelIndex) {
        this.activeModelIndex = activeModelIndex;
    }

    public String getActiveModelName() {
        return activeModelName;
    }

    public void setActiveModelName(String activeModelName) {
        this.activeModelName = activeModelName;
    }

    public String getActiveModelDisplay() {
        return activeModelDisplay;
    }

    public void setActiveModelDisplay(String activeModelDisplay) {
        this.activeModelDisplay = activeModelDisplay;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }
}
