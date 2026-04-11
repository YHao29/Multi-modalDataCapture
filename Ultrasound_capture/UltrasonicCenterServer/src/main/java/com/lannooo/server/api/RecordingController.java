package com.lannooo.server.api;

import com.lannooo.service.RecordingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/recording")
public class RecordingController {

    @Autowired
    private RecordingService recordingService;

    /**
     * 开始录制
     * @param request 包含场景ID、时间戳、持续时间等参数
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startRecording(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String sceneId = (String) request.get("scene_id");
            Long timestamp = request.containsKey("timestamp") ? 
                ((Number) request.get("timestamp")).longValue() : System.currentTimeMillis();
            Integer duration = request.containsKey("duration") ? 
                ((Number) request.get("duration")).intValue() : 10;
            
            boolean success = recordingService.startRecording(sceneId, timestamp, duration);
            
            if (success) {
                response.put("status", "success");
                response.put("message", "Recording started");
                response.put("scene_id", sceneId);
                response.put("timestamp", timestamp);
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "error");
                response.put("message", "No devices connected or recording failed");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 停止录制
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopRecording() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean success = recordingService.stopRecording();
            
            if (success) {
                response.put("status", "success");
                response.put("message", "Recording stopped");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "error");
                response.put("message", "Failed to stop recording");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 获取录制状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean isRecording = recordingService.isRecording();
            String currentScene = recordingService.getCurrentScene();
            
            response.put("status", "success");
            response.put("is_recording", isRecording);
            response.put("current_scene", currentScene);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
