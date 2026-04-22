package com.lannooo.audiocenter.audio;

import android.content.Context;
import android.util.Log;

import com.lannooo.audiocenter.tool.AppUtil;

import java.io.File;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

public class ClientAudioHandler extends AbstractAudioHandler {
    private static final String TAG = "ClientAudioHandler";

    private Channel remoteChannel;
    private String remoteKey;
    private final FileUploadManager fileUploadManager;
    private final RoutePresetManager routePresetManager;
    private UltrasonicConfig ultrasonicConfig = new UltrasonicConfig();
    private UltrasonicConfig manualUltrasonicConfig = new UltrasonicConfig();
    private boolean manualOverrideEnabled = false;
    private RoutePresetManager.PreparedRoute preparedRoute;

    public ClientAudioHandler(Context context, ExecutorService executor) {
        super(context, executor);
        this.fileUploadManager = new FileUploadManager();
        this.routePresetManager = new RoutePresetManager(context);
        this.preparedRoute = RoutePresetManager.PreparedRoute.defaultRoute(routePresetManager.getDeviceIdentitySummary());
    }

    public void setUltrasonicConfig(UltrasonicConfig ultrasonicConfig) {
        this.ultrasonicConfig = manualOverrideEnabled ? manualUltrasonicConfig.copy() : (ultrasonicConfig == null ? new UltrasonicConfig() : ultrasonicConfig);
    }

    public void setManualUltrasonicConfig(UltrasonicConfig ultrasonicConfig, boolean enabled) {
        this.manualUltrasonicConfig = ultrasonicConfig == null ? new UltrasonicConfig() : ultrasonicConfig.copy();
        this.manualOverrideEnabled = enabled;
        if (enabled) {
            this.ultrasonicConfig = this.manualUltrasonicConfig.copy();
        }
    }

    @Override
    public void configureRecorder(String outputFile,
                                  int duration,
                                  boolean enableProcess,
                                  boolean isCustom,
                                  boolean enableUltrasonic,
                                  AudioEventListener listener) {
        int audioSource = getAudioSource(enableProcess);
        int durationMs = (duration == -1) ? -1 : duration * 1000;

        if (duration == -1 && enableUltrasonic) {
            Log.e(TAG, "Ultrasonic recording must have a duration, Disable in force");
            enableUltrasonic = false;
        }
        this.enableUltrasonic = enableUltrasonic;

        File audioFile = new File(baseDir, outputFile);
        if (isCustom) {
            int sampleRate = this.enableUltrasonic ? ultrasonicConfig.getSampleRateHz() : AudioConstants.AUDIO_DEFAULT_SAMPLE_RATE;
            recorder = new CustomAudioRecorder(this, audioFile, audioSource, sampleRate, preparedRoute);
        } else {
            if (preparedRoute.getOutputDevice() != null || preparedRoute.getInputDevice() != null) {
                throw new IllegalStateException("Physical route presets require custom recorder mode.");
            }
            recorder = new SimpleAudioRecorder(this, audioFile, audioSource, durationMs);
        }
        recorder.setListener(listener);

        if (this.enableUltrasonic) {
            if ("fmcw".equalsIgnoreCase(ultrasonicConfig.getMode())) {
                player = new FmcwPlayer(ultrasonicConfig, duration, preparedRoute);
            } else {
                player = new FrequencyPlayer(this, ultrasonicConfig.getStartFreqHz(), duration, preparedRoute);
            }
            player.setListener(listener);
        }
    }

    @Override
    public void configurePlayer(String inputFile,
                                String type,
                                boolean enableLoop,
                                AudioEventListener listener) {
        int audioType = getAudioContentType(type);
        File audioFile = Paths.get(baseDir.getAbsolutePath(), "server", inputFile).toFile();
        player = new SimpleAudioPlayer(this, audioFile, audioType, enableLoop);
        player.setListener(listener);
    }

    @Override
    public UploadingFileItem writeUploadingFile(ChannelHandlerContext ctx, ByteBuf buf) {
        return fileUploadManager.writeChunk(Objects.requireNonNull(remoteKey), buf);
    }

    @Override
    public void addUploadingFile(ChannelHandlerContext ctx, Map<String, Object> data) {
        String savePath = getBaseDir().getAbsolutePath();
        long chunks = (long) (double) Objects.requireNonNull(data.get("chunks"));
        long length = (long) (double) Objects.requireNonNull(data.get("length"));
        String filename = (String) data.get("filepath");
        fileUploadManager.addTask(Objects.requireNonNull(remoteKey), savePath, filename, chunks, length);
    }

    public void cacheServerChannel(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        String remoteKey = AppUtil.parseAddress(channel, true, true);
        String localKey = AppUtil.parseAddress(channel, false, true);
        String key = AppUtil.sha1Hex(remoteKey + localKey, 8);
        this.remoteChannel = channel;
        this.remoteKey = key;
    }

    public void clearServerChannel() {
        this.remoteChannel = null;
        this.remoteKey = null;
    }

    public RoutePresetManager.PreparedRoute prepareRoute(String routePreset) {
        this.preparedRoute = routePresetManager.prepareRoute(routePreset);
        return preparedRoute;
    }

    public void playCaptureStartBeep() throws InterruptedException {
        routePresetManager.playCaptureStartCue(preparedRoute);
    }

    public Map<String, Object> buildRegisterRouteInfo() {
        return new LinkedHashMap<>(routePresetManager.buildRegisterRouteInfo());
    }

    public String getRoutePresetName() {
        return routePresetManager.getRoutePresetName();
    }

    public String getRouteCalibrationStatus() {
        return routePresetManager.getCalibrationStatus();
    }

    public String getRouteDiagnosticsText() {
        return routePresetManager.describeAvailableDevices();
    }

    public String getSavedRouteOutputDeviceId() {
        return routePresetManager.getSavedOutputDeviceId();
    }

    public String getSavedRouteInputDeviceId() {
        return routePresetManager.getSavedInputDeviceId();
    }

    public String getRouteDeviceIdentitySummary() {
        return routePresetManager.getDeviceIdentitySummary();
    }

    public void saveRouteCalibration(String outputDeviceId, String inputDeviceId) {
        routePresetManager.saveCalibration(outputDeviceId, inputDeviceId);
    }

    public void applyPreparedInputRoute(android.media.AudioRecord audioRecord, RoutePresetManager.PreparedRoute preparedRoute) {
        routePresetManager.applyPreferredInput(audioRecord, preparedRoute);
    }
}
