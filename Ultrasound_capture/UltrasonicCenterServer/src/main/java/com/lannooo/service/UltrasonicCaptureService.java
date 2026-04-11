package com.lannooo.service;

import com.lannooo.common.Utils;
import com.lannooo.device.DeviceManager;
import com.lannooo.model.UltrasonicCaptureRequest;
import com.lannooo.model.UltrasonicFmcwConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

@Service
public class UltrasonicCaptureService {
    private static final Logger logger = Utils.getLogger(UltrasonicCaptureService.class);
    private static final long AUTO_CLEAR_GRACE_MILLIS = 3000L;

    @Autowired
    private DeviceManager deviceManager;

    @Autowired
    private RemoteAudioService remoteAudioService;

    private volatile boolean capturing = false;
    private volatile String currentOutput = null;
    private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong sessionCounter = new AtomicLong(0L);
    private volatile long activeSessionId = 0L;
    private volatile ScheduledFuture<?> autoClearFuture = null;

    public synchronized boolean startCapture(UltrasonicCaptureRequest request) {
        if (capturing) {
            logger.warning("Ultrasonic capture is already running");
            return false;
        }

        Set<String> targetKeys = resolveTargetKeys(request.getDeviceId());
        if (targetKeys.isEmpty()) {
            logger.warning("No target devices available for ultrasonic capture");
            return false;
        }

        UltrasonicFmcwConfig cfg = request.getUltrasonic() == null ? new UltrasonicFmcwConfig() : request.getUltrasonic();
        boolean success = false;
        for (String key : targetKeys) {
            try {
                remoteAudioService.captureUltrasonic(key, "start", request.getMode(), request.getOutput(), request.getDurationSeconds(), request.isProcess(), request.isForward(), request.isDeleteAfterForward(), cfg);
                success = true;
            } catch (Exception e) {
                logger.severe("Failed to start ultrasonic capture on device " + key + ": " + e.getMessage());
            }
        }

        if (success) {
            long sessionId = sessionCounter.incrementAndGet();
            capturing = true;
            currentOutput = request.getOutput();
            activeSessionId = sessionId;
            state.clear();
            state.put("device_id", request.getDeviceId());
            state.put("duration_seconds", request.getDurationSeconds());
            state.put("output", request.getOutput());
            state.put("mode", request.getMode());
            state.put("ultrasonic", cfg);
            state.put("started_at_ms", System.currentTimeMillis());
            state.put("session_id", sessionId);
            state.put("completion_reason", "running");
            scheduleAutoClear(sessionId, request.getDurationSeconds());
        }
        return success;
    }

    public synchronized boolean stopCapture(String deviceId) {
        Set<String> targetKeys = resolveTargetKeys(deviceId);
        if (targetKeys.isEmpty()) {
            return false;
        }

        boolean success = false;
        for (String key : targetKeys) {
            try {
                remoteAudioService.stopUltrasonicCapture(key);
                success = true;
            } catch (Exception e) {
                logger.severe("Failed to stop ultrasonic capture on device " + key + ": " + e.getMessage());
            }
        }

        if (success) {
            finalizeCaptureState("explicit_stop");
        }
        return success;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("capturing", capturing);
        status.put("current_output", currentOutput);
        status.put("device_count", deviceManager.getConnectedDevices().size());
        status.put("state", new LinkedHashMap<>(state));
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }

    private void scheduleAutoClear(long sessionId, int durationSeconds) {
        if (autoClearFuture != null) {
            autoClearFuture.cancel(false);
        }
        long delayMillis = Math.max(1000L, durationSeconds * 1000L + AUTO_CLEAR_GRACE_MILLIS);
        autoClearFuture = scheduler.schedule(() -> {
            synchronized (UltrasonicCaptureService.this) {
                if (!capturing || activeSessionId != sessionId) {
                    return;
                }
                logger.info("Auto-clearing ultrasonic capture state for session " + sessionId);
                finalizeCaptureState("auto_timeout");
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private synchronized void finalizeCaptureState(String reason) {
        capturing = false;
        currentOutput = null;
        if (autoClearFuture != null) {
            autoClearFuture.cancel(false);
            autoClearFuture = null;
        }
        state.put("completed_at_ms", System.currentTimeMillis());
        state.put("completion_reason", reason);
    }

    private Set<String> resolveTargetKeys(String deviceId) {
        if (deviceId == null || "ALL".equalsIgnoreCase(deviceId)) {
            return deviceManager.getCaptureKeys();
        }
        if (!deviceManager.isRegistered(deviceId) || !deviceManager.isCaptureEnabled(deviceId)) {
            return Set.of();
        }
        return Set.of(deviceId);
    }
}