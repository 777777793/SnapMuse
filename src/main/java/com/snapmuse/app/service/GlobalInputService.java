package com.snapmuse.app.service;

import com.snapmuse.app.model.AppConfig;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.Point;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GlobalInputService implements NativeMouseInputListener, NativeKeyListener {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(GlobalInputService.class);
    private static final long HOLD_CAPTURE_MILLIS = 2000L;
    private final SelectionService selectionService;
    private final ConfigService configService;
    private final RuntimeStateService runtimeStateService;
    private final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService holdScheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Integer> singleKeyMap = Map.ofEntries(
            Map.entry("A", NativeKeyEvent.VC_A),
            Map.entry("B", NativeKeyEvent.VC_B),
            Map.entry("C", NativeKeyEvent.VC_C),
            Map.entry("D", NativeKeyEvent.VC_D),
            Map.entry("E", NativeKeyEvent.VC_E),
            Map.entry("F", NativeKeyEvent.VC_F),
            Map.entry("G", NativeKeyEvent.VC_G),
            Map.entry("H", NativeKeyEvent.VC_H),
            Map.entry("I", NativeKeyEvent.VC_I),
            Map.entry("J", NativeKeyEvent.VC_J),
            Map.entry("K", NativeKeyEvent.VC_K),
            Map.entry("L", NativeKeyEvent.VC_L),
            Map.entry("M", NativeKeyEvent.VC_M),
            Map.entry("N", NativeKeyEvent.VC_N),
            Map.entry("O", NativeKeyEvent.VC_O),
            Map.entry("P", NativeKeyEvent.VC_P),
            Map.entry("Q", NativeKeyEvent.VC_Q),
            Map.entry("R", NativeKeyEvent.VC_R),
            Map.entry("S", NativeKeyEvent.VC_S),
            Map.entry("T", NativeKeyEvent.VC_T),
            Map.entry("U", NativeKeyEvent.VC_U),
            Map.entry("V", NativeKeyEvent.VC_V),
            Map.entry("W", NativeKeyEvent.VC_W),
            Map.entry("X", NativeKeyEvent.VC_X),
            Map.entry("Y", NativeKeyEvent.VC_Y),
            Map.entry("Z", NativeKeyEvent.VC_Z),
            Map.entry("0", NativeKeyEvent.VC_0),
            Map.entry("1", NativeKeyEvent.VC_1),
            Map.entry("2", NativeKeyEvent.VC_2),
            Map.entry("3", NativeKeyEvent.VC_3),
            Map.entry("4", NativeKeyEvent.VC_4),
            Map.entry("5", NativeKeyEvent.VC_5),
            Map.entry("6", NativeKeyEvent.VC_6),
            Map.entry("7", NativeKeyEvent.VC_7),
            Map.entry("8", NativeKeyEvent.VC_8),
            Map.entry("9", NativeKeyEvent.VC_9)
    );
    private volatile long leftPressStartedAt;
    private volatile Point leftPressPoint;
    private volatile String lastTriggeredHotkey;
    private volatile ScheduledFuture<?> leftHoldTask;
    private volatile boolean leftPointCapturedForCurrentPress;

    public GlobalInputService(SelectionService selectionService, ConfigService configService, RuntimeStateService runtimeStateService) {
        this.selectionService = selectionService;
        this.configService = configService;
        this.runtimeStateService = runtimeStateService;
    }

    @PostConstruct
    public void init() {
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);

        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeMouseListener(this);
            GlobalScreen.addNativeMouseMotionListener(this);
            GlobalScreen.addNativeKeyListener(this);
            log.info("全局监听已注册");
            runtimeStateService.updateHookReady(true, "就绪");
        } catch (NativeHookException ex) {
            log.error("全局监听注册失败", ex);
            runtimeStateService.updateHookReady(false, "全局监听启动失败，请检查辅助功能权限: " + ex.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            cancelHoldTask();
            holdScheduler.shutdownNow();
            GlobalScreen.removeNativeMouseListener(this);
            GlobalScreen.removeNativeMouseMotionListener(this);
            GlobalScreen.removeNativeKeyListener(this);
            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.unregisterNativeHook();
            }
        } catch (NativeHookException ignored) {
        }
    }

    @Override
    public void nativeMousePressed(NativeMouseEvent nativeEvent) {
        if (nativeEvent.getButton() == NativeMouseEvent.BUTTON1) {
            leftPressStartedAt = System.currentTimeMillis();
            leftPressPoint = new Point(nativeEvent.getX(), nativeEvent.getY());
            leftPointCapturedForCurrentPress = false;
            scheduleHoldCapture(leftPressPoint, leftPressStartedAt);
        }
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent nativeEvent) {
        if (nativeEvent.getButton() != NativeMouseEvent.BUTTON1) {
            return;
        }
        cancelHoldTask();
        long heldMillis = System.currentTimeMillis() - leftPressStartedAt;
        if (!leftPointCapturedForCurrentPress
                && heldMillis >= HOLD_CAPTURE_MILLIS
                && leftPressPoint != null
                && selectionService.canAcceptPoint()) {
            log.info("长按松开触发选点 ({}, {}), 持续{}ms", leftPressPoint.x, leftPressPoint.y, heldMillis);
            selectionService.acceptPoint(new Point(leftPressPoint.x, leftPressPoint.y));
        }
        leftPressPoint = null;
        leftPointCapturedForCurrentPress = false;
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeEvent) {
        pressedKeys.add(nativeEvent.getKeyCode());
        AppConfig config = configService.getConfig();
        if (matchesHotkey(config.getScrollUpHotkey()) && !config.getScrollUpHotkey().equalsIgnoreCase(lastTriggeredHotkey)) {
            lastTriggeredHotkey = config.getScrollUpHotkey();
            runtimeStateService.hotkeyTriggered("UP");
        } else if (matchesHotkey(config.getScrollDownHotkey()) && !config.getScrollDownHotkey().equalsIgnoreCase(lastTriggeredHotkey)) {
            lastTriggeredHotkey = config.getScrollDownHotkey();
            runtimeStateService.hotkeyTriggered("DOWN");
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeEvent) {
        pressedKeys.remove(nativeEvent.getKeyCode());
        lastTriggeredHotkey = null;
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeEvent) {
    }

    @Override
    public void nativeMouseClicked(NativeMouseEvent nativeEvent) {
    }

    @Override
    public void nativeMouseMoved(NativeMouseEvent nativeEvent) {
    }

    @Override
    public void nativeMouseDragged(NativeMouseEvent nativeEvent) {
    }

    private boolean matchesHotkey(String hotkeyValue) {
        if (hotkeyValue == null || hotkeyValue.isBlank()) {
            return false;
        }
        Set<Integer> currentKeys = new HashSet<>(pressedKeys);
        String[] parts = hotkeyValue.toUpperCase().split("\\+");
        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (!matchesPart(part, currentKeys)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesPart(String part, Set<Integer> currentKeys) {
        return switch (part) {
            case "ALT" -> currentKeys.contains(NativeKeyEvent.VC_ALT);
            case "CTRL", "CONTROL" -> currentKeys.contains(NativeKeyEvent.VC_CONTROL);
            case "SHIFT" -> currentKeys.contains(NativeKeyEvent.VC_SHIFT);
            case "CMD", "COMMAND", "META" -> currentKeys.contains(NativeKeyEvent.VC_META);
            case "UP" -> currentKeys.contains(NativeKeyEvent.VC_UP);
            case "DOWN" -> currentKeys.contains(NativeKeyEvent.VC_DOWN);
            case "LEFT" -> currentKeys.contains(NativeKeyEvent.VC_LEFT);
            case "RIGHT" -> currentKeys.contains(NativeKeyEvent.VC_RIGHT);
            case "PAGEUP" -> currentKeys.contains(NativeKeyEvent.VC_PAGE_UP);
            case "PAGEDOWN" -> currentKeys.contains(NativeKeyEvent.VC_PAGE_DOWN);
            case "HOME" -> currentKeys.contains(NativeKeyEvent.VC_HOME);
            case "END" -> currentKeys.contains(NativeKeyEvent.VC_END);
            default -> singleKeyMap.containsKey(part) && currentKeys.contains(singleKeyMap.get(part));
        };
    }

    private void scheduleHoldCapture(Point point, long pressStartedAt) {
        cancelHoldTask();
        leftHoldTask = holdScheduler.schedule(() -> {
            if (!selectionService.canAcceptPoint()) {
                return;
            }
            if (leftPressPoint == null || leftPointCapturedForCurrentPress) {
                return;
            }
            if (!leftPressPoint.equals(point) || leftPressStartedAt != pressStartedAt) {
                return;
            }
            leftPointCapturedForCurrentPress = true;
            log.info("长按2秒确认选点 ({}, {})", point.x, point.y);
            selectionService.acceptPoint(new Point(point.x, point.y));
        }, HOLD_CAPTURE_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void cancelHoldTask() {
        if (leftHoldTask != null) {
            leftHoldTask.cancel(false);
            leftHoldTask = null;
        }
    }
}
