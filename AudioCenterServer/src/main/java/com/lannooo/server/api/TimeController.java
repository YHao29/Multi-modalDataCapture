package com.lannooo.server.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/time")
public class TimeController {

    /**
     * 同步时间 - 返回服务器当前时间戳
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncTime(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        long currentTime = System.currentTimeMillis();
        
        response.put("status", "success");
        response.put("server_timestamp", currentTime);
        response.put("message", "Time synchronized");
        
        // 如果客户端提供了时间戳，计算差值
        if (request != null && request.containsKey("client_timestamp")) {
            long clientTime = ((Number) request.get("client_timestamp")).longValue();
            long offset = currentTime - clientTime;
            response.put("client_timestamp", clientTime);
            response.put("offset_ms", offset);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取服务器当前时间
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentTime() {
        Map<String, Object> response = new HashMap<>();
        
        response.put("status", "success");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}
