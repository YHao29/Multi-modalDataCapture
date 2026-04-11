package com.lannooo.server.api;

import com.lannooo.model.UltrasonicCaptureRequest;
import com.lannooo.service.UltrasonicCaptureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ultrasonic")
public class UltrasonicController {

    @Autowired
    private UltrasonicCaptureService ultrasonicCaptureService;

    @PostMapping("/capture/start")
    public ResponseEntity<Map<String, Object>> startCapture(@RequestBody(required = false) UltrasonicCaptureRequest request) {
        UltrasonicCaptureRequest actualRequest = request == null ? new UltrasonicCaptureRequest() : request;
        Map<String, Object> response = new LinkedHashMap<>();
        boolean success = ultrasonicCaptureService.startCapture(actualRequest);
        if (!success) {
            response.put("status", "error");
            response.put("message", "Failed to start ultrasonic capture");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
        response.put("status", "success");
        response.put("message", "Ultrasonic capture started");
        response.put("request", actualRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/capture/stop")
    public ResponseEntity<Map<String, Object>> stopCapture(@RequestParam(defaultValue = "ALL") String deviceId) {
        Map<String, Object> response = new LinkedHashMap<>();
        boolean success = ultrasonicCaptureService.stopCapture(deviceId);
        if (!success) {
            response.put("status", "error");
            response.put("message", "Failed to stop ultrasonic capture");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
        response.put("status", "success");
        response.put("message", "Ultrasonic capture stopped");
        response.put("device_id", deviceId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/capture/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.putAll(ultrasonicCaptureService.getStatus());
        return ResponseEntity.ok(response);
    }
}
