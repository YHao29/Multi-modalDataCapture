package com.lannooo.service;

import com.lannooo.common.Utils;
import com.lannooo.device.DeviceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 录制服务 - 用于管理多模态数据采集
 */
@Service
public class RecordingService {
    private static final Logger logger = Utils.getLogger(RecordingService.class);

    @Autowired
    private DeviceManager deviceManager;

    @Autowired
    private RemoteAudioService remoteAudioService;

    // 当前录制状态
    private volatile boolean isRecording = false;
    
    // 当前场景ID
    private volatile String currentScene = null;
    
    // 录制参数缓存
    private final ConcurrentHashMap<String, Object> recordingParams = new ConcurrentHashMap<>();

    /**
     * 开始录制
     * @param sceneId 场景ID
     * @param timestamp 时间戳
     * @param duration 录制时长（秒）
     * @return 是否成功
     */
    public boolean startRecording(String sceneId, long timestamp, int duration) {
        try {
            // 检查是否有设备连接
            Set<String> deviceKeys = deviceManager.getDeviceKeys();
            if (deviceKeys.isEmpty()) {
                logger.warning("No devices connected, cannot start recording");
                return false;
            }

            // 检查是否已经在录制
            if (isRecording) {
                logger.warning("Already recording scene: " + currentScene);
                return false;
            }

            logger.info(String.format("Starting recording for scene: %s, timestamp: %d, duration: %d", 
                sceneId, timestamp, duration));

            // 保存录制参数
            recordingParams.put("scene_id", sceneId);
            recordingParams.put("timestamp", timestamp);
            recordingParams.put("duration", duration);

            // 对所有连接的设备发送录制指令
            boolean success = false;
            for (String key : deviceKeys) {
                try {
                    // 调用远程音频服务开始录制
                    remoteAudioService.captureAudio(
                        key,           // 设备key
                        "start",       // 动作
                        "single",      // 模式：单次录制
                        sceneId,       // 输出文件名（场景ID）
                        duration,      // 持续时间
                        false,         // 不处理
                        true,          // 转发数据
                        false,         // 录制后不删除
                        true           // 使用超声波模式
                    );
                    
                    success = true;
                    logger.info("Started recording on device: " + key);
                    
                } catch (Exception e) {
                    logger.severe("Failed to start recording on device " + key + ": " + e.getMessage());
                }
            }

            if (success) {
                isRecording = true;
                currentScene = sceneId;
            }

            return success;
            
        } catch (Exception e) {
            logger.severe("Error starting recording: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 停止录制
     * @return 是否成功
     */
    public boolean stopRecording() {
        try {
            if (!isRecording) {
                logger.warning("Not currently recording");
                return false;
            }

            logger.info("Stopping recording for scene: " + currentScene);

            // 对所有连接的设备发送停止指令
            Set<String> deviceKeys = deviceManager.getDeviceKeys();
            boolean success = false;
            
            for (String key : deviceKeys) {
                try {
                    // 调用远程音频服务停止录制
                    remoteAudioService.captureAudio(
                        key,           // 设备key
                        "stop",        // 动作
                        null,          // 模式（停止时不需要）
                        null,          // 输出文件名（停止时不需要）
                        0,             // 持续时间（停止时不需要）
                        false,         // 不处理
                        false,         // 不转发
                        false,         // 不删除
                        false          // 超声波模式（停止时不需要）
                    );
                    
                    success = true;
                    logger.info("Stopped recording on device: " + key);
                    
                } catch (Exception e) {
                    logger.severe("Failed to stop recording on device " + key + ": " + e.getMessage());
                }
            }

            if (success) {
                isRecording = false;
                String stoppedScene = currentScene;
                currentScene = null;
                recordingParams.clear();
                logger.info("Recording stopped for scene: " + stoppedScene);
            }

            return success;
            
        } catch (Exception e) {
            logger.severe("Error stopping recording: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取当前录制状态
     * @return true表示正在录制
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * 获取当前录制的场景ID
     * @return 场景ID，如果未在录制则返回null
     */
    public String getCurrentScene() {
        return currentScene;
    }

    /**
     * 获取录制参数
     * @param key 参数键
     * @return 参数值
     */
    public Object getRecordingParam(String key) {
        return recordingParams.get(key);
    }
}
