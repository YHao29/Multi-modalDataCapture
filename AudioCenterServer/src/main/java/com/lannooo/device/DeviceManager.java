package com.lannooo.device;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.lannooo.common.Utils;
import com.lannooo.service.AsyncService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;


@Component
public class DeviceManager {
    private static final Logger logger = Utils.getLogger(DeviceManager.class);

    private final Set<String> deviceKeys;
    private final Map<String, Device> devices;

    private final Map<String, String> idNames;
    // for keep the status of remote device's microphone status
    // true: capture enabled, false: capture disabled
    private final Map<String, Boolean> captureStatus;
    private final Map<String, Boolean> playbackStatus;

    @Autowired
    private FileUploadManager fileUploadManager;

    @Autowired
    private ChannelManager channelManager;

    @Autowired
    private AsyncService asyncService;

    @Autowired
    private SessionManager sessionManager;


    public DeviceManager() {
        // for keep the order as it is registered
        this.devices = new ConcurrentHashMap<>(16);
        this.deviceKeys = this.devices.keySet(); // see as a view
        this.captureStatus = new ConcurrentHashMap<>(16);
        this.playbackStatus = new ConcurrentHashMap<>(16);
        this.idNames = new ConcurrentHashMap<>(16);
    }

    public Map<String, Device> getDevices() {
        return Collections.unmodifiableMap(devices);
    }

    public Set<String> getDeviceKeys() {
        return Collections.unmodifiableSet(deviceKeys);
    }

    public Set<String> getCaptureKeys() {
        return getDeviceKeys().stream().filter(captureStatus::get).collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> getPlaybackKeys() {
        return getDeviceKeys().stream().filter(playbackStatus::get).collect(Collectors.toUnmodifiableSet());
    }

    public Map<String, Boolean> getCaptureStatus() {
        return Collections.unmodifiableMap(captureStatus);
    }

    public Map<String, Boolean> getPlaybackStatus() {
        return Collections.unmodifiableMap(playbackStatus);
    }

    public List<String> getConnectedDevices() {
        return new ArrayList<>(deviceKeys);
    }

    public boolean isRegistered(String key) {
        return deviceKeys.contains(key);
    }

    public String uniqueKey(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        // check if the key is already registered
        String key = channelManager.getKey(channel);
        if (null != key) {
            return key;
        }
        // build a new key
        String remoteAddress = Utils.parseAddress(channel, true, false);
        String localAddress = Utils.parseAddress(channel, false, true);
        return Utils.sha1Hex(remoteAddress + localAddress, 8);
    }

    public void unregisterRemoteDevice(ChannelHandlerContext ctx) {
        String key = uniqueKey(ctx);

        if (deviceKeys.contains(key)) {
            PhoneDevice device = (PhoneDevice) devices.remove(key);
            captureStatus.remove(key);
            playbackStatus.remove(key);
            channelManager.unregisterChannel(key);
            fileUploadManager.removeTask(key);
            fileUploadManager.unregisterDeviceName(key);

            logger.info("Unregistered device: " + device);
        }
    }


    public void registerOrUpdateRemoteDevice(ChannelHandlerContext ctx, Map<String, Object> data) {
        Channel channel = ctx.channel();
        String key = uniqueKey(ctx);
        String remoteAddress = Utils.parseAddress(channel, true, true);
        String localAddress = Utils.parseAddress(channel, false, true);

        String brand = (String) data.getOrDefault("Brand", "Unknown");
        String model = (String) data.getOrDefault("Model", "Unknown");
        String name = brand + "/" + model;

        if (!deviceKeys.contains(key)) {
            PhoneDevice device = new PhoneDevice(key, name);
            device.setRemoteAddress(remoteAddress);
            device.setLocalAddress(localAddress);
            device.setExtra(data);

            // create a new device object, and cache the corresponding channel
            devices.putIfAbsent(key, device);
            captureStatus.putIfAbsent(key, Boolean.TRUE);
            playbackStatus.putIfAbsent(key, Boolean.FALSE);
            channelManager.registerChannel(key, channel);

            logger.info("Registered device: " + device);
        } else {
            // update name and extra information
            PhoneDevice device = (PhoneDevice) devices.get(key);
            device.setExtra(data);
            device.setName(name);
            // update ids names
            idNames.putIfAbsent(key, name);
            fileUploadManager.registerDeviceName(key, name);
            asyncService.submit(() -> {
                // save mapping of id and name into file
                try {
                    Utils.saveMap(idNames, "audio/id-names");
                } catch (IOException e) {
                    logger.severe("Failed to save id-names: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            });
            logger.info("Updated device: " + device);
        }
    }

    public UploadingFileItem writeUploadingFile(ChannelHandlerContext ctx, ByteBuf chunkBuf) {
        String key = uniqueKey(ctx);
        return fileUploadManager.writeChunk(key, chunkBuf);
    }

    public void addUploadingFile(ChannelHandlerContext ctx, Map<String, Object> data) {
        String key = uniqueKey(ctx);

        // transform double to long
        long chunks = (long) (double) data.get("chunks");
        long length = (long) (double) data.get("length");
        String filename = (String) data.get("filepath");
        logger.info("File upload request: " + filename + " chunks: " + chunks + " length: " + length);

        String expKey = sessionManager.getExpKey();
        fileUploadManager.addTask(key, expKey, filename, chunks, length);
    }

    public boolean hasFileInUploading() {
        return fileUploadManager.hasOngoingTasks();
    }

    public boolean updateDeviceFunctions(String deviceId, String capability, String enableAction) {
        if (!deviceKeys.contains(deviceId)) {
            return false;
        }
        Boolean status = "on".equalsIgnoreCase(enableAction) ? Boolean.TRUE : Boolean.FALSE;
        switch (capability) {
            case "capture":
                captureStatus.replace(deviceId, status);
                break;
            case "playback":
                playbackStatus.replace(deviceId, status);
                break;
            case "all":
                captureStatus.replace(deviceId, status);
                playbackStatus.replace(deviceId, status);
                break;
            default:
                return false;
        }
        return true;
    }

    public boolean isPlaybackEnabled(String key) {
        return playbackStatus.getOrDefault(key, Boolean.FALSE);
    }

    public boolean isCaptureEnabled(String key) {
        return captureStatus.getOrDefault(key, Boolean.FALSE);
    }
}
