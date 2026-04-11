package com.lannooo.server.api;

import com.lannooo.device.DeviceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @Autowired
    private DeviceManager deviceManager;

    /**
     * 获取已连接的设备列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listDevices() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<String> devices = deviceManager.getConnectedDevices();
            
            response.put("status", "success");
            response.put("device_count", devices.size());
            response.put("devices", devices);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取服务器状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getServerStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("status", "success");
            response.put("server_running", true);
            response.put("device_count", deviceManager.getConnectedDevices().size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
