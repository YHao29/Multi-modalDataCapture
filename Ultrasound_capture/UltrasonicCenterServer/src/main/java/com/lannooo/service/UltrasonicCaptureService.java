package com.lannooo.service;

import com.lannooo.common.Utils;
import com.lannooo.device.DeviceManager;
import com.lannooo.model.UltrasonicCaptureRequest;
import com.lannooo.model.UltrasonicFmcwConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
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
            state.put("route_requested_preset", cfg.getRoutePreset());
            state.put("route_applied_preset", "");
            state.put("route_binding_status", cfg.getRoutePreset() == null || cfg.getRoutePreset().isEmpty() ? "default" : "pending");
            state.put("route_output_binding", "");
            state.put("route_input_binding", "");
            state.put("route_error_message", "");
            String routeModelKey = targetKeys.iterator().next();
            state.put("route_device_model", deviceManager.getRouteCapabilitySnapshot(routeModelKey).getOrDefault("model", "Unknown"));
            state.put("started_at_ms", System.currentTimeMillis());
            state.put("session_id", sessionId);
            state.put("completion_reason", "running");
            scheduleAutoClear(sessionId, request.getDurationSeconds());
        }
        return success;
    }

    public synchronized Map<String, Object> preflightRoute(String deviceId, String routePreset) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("device_id", deviceId);
        result.put("route_preset", routePreset);
        if (routePreset == null || routePreset.trim().isEmpty()) {
            result.put("ok", true);
            result.put("message", "default route");
            result.put("capability", Collections.emptyMap());
            return result;
        }

        Set<String> targetKeys = resolveTargetKeys(deviceId);
        if (targetKeys.isEmpty()) {
            result.put("ok", false);
            result.put("message", "device_not_available");
            return result;
        }

        String resolvedKey = targetKeys.iterator().next();
        Map<String, Object> capability = deviceManager.getRouteCapabilitySnapshot(resolvedKey);
        result.put("capability", capability);
        Object presetsObj = capability.get("supported_route_presets");
        boolean supported = false;
        if (presetsObj instanceof List) {
            for (Object preset : (List<?>) presetsObj) {
                if (routePreset.equals(String.valueOf(preset))) {
                    supported = true;
                    break;
                }
            }
        }

        if (!supported) {
            result.put("ok", false);
            result.put("message", "route_preset_not_supported");
            return result;
        }

        result.put("ok", true);
        result.put("message", "route preflight passed");
        return result;
    }

    public synchronized void updateRouteStatus(String deviceId, Map<String, Object> routeState) {
        if (routeState == null || routeState.isEmpty()) {
            return;
        }

        Object outputName = routeState.get("output_name");
        if (outputName != null && currentOutput != null && !currentOutput.equals(String.valueOf(outputName))) {
            return;
        }
        if (deviceId != null) {
            state.put("device_id", deviceId);
        }

        copyRouteField(routeState, "requested_preset", "route_requested_preset");
        copyRouteField(routeState, "applied_preset", "route_applied_preset");
        copyRouteField(routeState, "binding_status", "route_binding_status");
        copyRouteField(routeState, "output_binding", "route_output_binding");
        copyRouteField(routeState, "input_binding", "route_input_binding");
        copyRouteField(routeState, "error_message", "route_error_message");
        copyRouteField(routeState, "device_model", "route_device_model");
        state.put("route_updated_at_ms", System.currentTimeMillis());

        Object bindingStatus = routeState.get("binding_status");
        if (bindingStatus != null && "failed".equalsIgnoreCase(String.valueOf(bindingStatus))) {
            finalizeCaptureState("route_binding_failed");
        }
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

    private void copyRouteField(Map<String, Object> routeState, String sourceKey, String targetKey) {
        if (routeState.containsKey(sourceKey)) {
            state.put(targetKey, routeState.get(sourceKey));
        }
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
