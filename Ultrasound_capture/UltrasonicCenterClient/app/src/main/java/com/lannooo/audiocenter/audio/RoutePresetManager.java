package com.lannooo.audiocenter.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RoutePresetManager {
    public static final String PRESET_MATE40PRO_BOTTOM_SPEAKER_BOTTOM_MIC = "mate40pro_bottom_speaker_bottom_mic";

    private static final String PREFS_NAME = "route_preset_calibration";
    private static final String KEY_DEVICE_MANUFACTURER = "device_manufacturer";
    private static final String KEY_DEVICE_MODEL = "device_model";
    private static final String KEY_OUTPUT_DEVICE_ID = "output_device_id";
    private static final String KEY_INPUT_DEVICE_ID = "input_device_id";

    private final AudioManager audioManager;
    private final SharedPreferences preferences;

    public RoutePresetManager(Context context) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getRoutePresetName() {
        return PRESET_MATE40PRO_BOTTOM_SPEAKER_BOTTOM_MIC;
    }

    public String getDeviceIdentitySummary() {
        return Build.MANUFACTURER + " / " + Build.MODEL + " / SDK " + Build.VERSION.SDK_INT;
    }

    public String getSavedOutputDeviceId() {
        return preferences.getString(KEY_OUTPUT_DEVICE_ID, "");
    }

    public String getSavedInputDeviceId() {
        return preferences.getString(KEY_INPUT_DEVICE_ID, "");
    }

    public void saveCalibration(String outputDeviceIdText, String inputDeviceIdText) {
        preferences.edit()
                .putString(KEY_DEVICE_MANUFACTURER, Build.MANUFACTURER)
                .putString(KEY_DEVICE_MODEL, Build.MODEL)
                .putString(KEY_OUTPUT_DEVICE_ID, outputDeviceIdText == null ? "" : outputDeviceIdText.trim())
                .putString(KEY_INPUT_DEVICE_ID, inputDeviceIdText == null ? "" : inputDeviceIdText.trim())
                .apply();
    }

    public String getCalibrationStatus() {
        CalibrationSnapshot calibration = readCalibrationSnapshot();
        if (calibration == null) {
            return "uncalibrated";
        }
        if (!calibration.matchesCurrentDevice()) {
            return "device_mismatch";
        }
        if (findOutputDevice(calibration.outputDeviceId) == null || findInputDevice(calibration.inputDeviceId) == null) {
            return "stale";
        }
        return "calibrated";
    }

    public Map<String, Object> buildRegisterRouteInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("route_device_key", currentDeviceKey());
        info.put("route_device_model", getDeviceIdentitySummary());
        info.put("route_calibration_status", getCalibrationStatus());
        info.put("route_calibrated_output_id", getSavedOutputDeviceId());
        info.put("route_calibrated_input_id", getSavedInputDeviceId());

        List<String> presets = new ArrayList<>();
        if ("calibrated".equals(getCalibrationStatus())) {
            presets.add(PRESET_MATE40PRO_BOTTOM_SPEAKER_BOTTOM_MIC);
        }
        info.put("supported_route_presets", presets);
        return info;
    }

    public PreparedRoute prepareRoute(String routePreset) {
        if (routePreset == null || routePreset.trim().isEmpty()) {
            return PreparedRoute.defaultRoute(getDeviceIdentitySummary());
        }
        if (!PRESET_MATE40PRO_BOTTOM_SPEAKER_BOTTOM_MIC.equals(routePreset)) {
            throw new IllegalStateException("Unsupported route preset: " + routePreset);
        }

        CalibrationSnapshot calibration = readCalibrationSnapshot();
        if (calibration == null) {
            throw new IllegalStateException("Route preset calibration is missing on device.");
        }
        if (!calibration.matchesCurrentDevice()) {
            throw new IllegalStateException("Stored route calibration does not match current device.");
        }

        AudioDeviceInfo outputDevice = findOutputDevice(calibration.outputDeviceId);
        if (outputDevice == null) {
            throw new IllegalStateException("Configured output device is not available.");
        }
        AudioDeviceInfo inputDevice = findInputDevice(calibration.inputDeviceId);
        if (inputDevice == null) {
            throw new IllegalStateException("Configured input device is not available.");
        }

        return PreparedRoute.bound(
                routePreset,
                getDeviceIdentitySummary(),
                outputDevice,
                inputDevice,
                describeDevice(outputDevice),
                describeDevice(inputDevice)
        );
    }

    public void applyPreferredOutput(AudioTrack audioTrack, PreparedRoute preparedRoute) {
        if (preparedRoute == null || preparedRoute.getOutputDevice() == null) {
            return;
        }
        if (!audioTrack.setPreferredDevice(preparedRoute.getOutputDevice())) {
            throw new IllegalStateException("Failed to bind preferred output device.");
        }
    }

    public void applyPreferredInput(AudioRecord audioRecord, PreparedRoute preparedRoute) {
        if (preparedRoute == null || preparedRoute.getInputDevice() == null) {
            return;
        }
        if (!audioRecord.setPreferredDevice(preparedRoute.getInputDevice())) {
            throw new IllegalStateException("Failed to bind preferred input device.");
        }
    }

    public void playCaptureStartCue(PreparedRoute preparedRoute) throws InterruptedException {
        int sampleRate = 48000;
        short[] samples = buildCueSignal(sampleRate);
        AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(samples.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        try {
            applyPreferredOutput(audioTrack, preparedRoute);
            audioTrack.write(samples, 0, samples.length);
            audioTrack.play();
            Thread.sleep(1040L);
        } finally {
            try {
                audioTrack.stop();
            } catch (Exception ignored) {
            }
            audioTrack.release();
        }
    }

    public String describeAvailableDevices() {
        StringBuilder builder = new StringBuilder();
        builder.append("Preset: ").append(PRESET_MATE40PRO_BOTTOM_SPEAKER_BOTTOM_MIC).append('\n');
        builder.append("Device: ").append(getDeviceIdentitySummary()).append('\n');
        builder.append("Calibration: ").append(getCalibrationStatus()).append('\n');
        builder.append("Saved Output ID: ").append(getSavedOutputDeviceId()).append('\n');
        builder.append("Saved Input ID: ").append(getSavedInputDeviceId()).append('\n');
        builder.append('\n').append("Outputs").append('\n');
        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            builder.append("  [").append(device.getId()).append("] ").append(describeDevice(device)).append('\n');
        }
        builder.append('\n').append("Inputs").append('\n');
        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            builder.append("  [").append(device.getId()).append("] ").append(describeDevice(device)).append('\n');
        }
        return builder.toString().trim();
    }

    private String describeDevice(AudioDeviceInfo device) {
        return String.valueOf(device.getProductName()) + " / type=" + deviceTypeToText(device.getType());
    }

    private String deviceTypeToText(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return "builtin_earpiece";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "builtin_speaker";
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return "builtin_mic";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "wired_headset";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return "usb_device";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "bluetooth_a2dp";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "bluetooth_sco";
            default:
                return "type_" + type;
        }
    }

    private short[] buildCueSignal(int sampleRate) {
        int totalSamples = sampleRate;
        short[] data = new short[totalSamples];
        writeTone(data, 0, (int) (sampleRate * 0.25), 880.0, sampleRate);
        writeTone(data, (int) (sampleRate * 0.32), (int) (sampleRate * 0.25), 1320.0, sampleRate);
        return data;
    }

    private void writeTone(short[] target, int offset, int length, double frequency, int sampleRate) {
        int cappedLength = Math.max(0, Math.min(length, target.length - offset));
        for (int i = 0; i < cappedLength; i++) {
            double gain = 0.45 * (1.0 - Math.cos((2.0 * Math.PI * i) / Math.max(1, cappedLength - 1))) * 0.5;
            double value = Math.sin(2.0 * Math.PI * frequency * i / sampleRate) * gain;
            target[offset + i] = (short) Math.round(value * Short.MAX_VALUE);
        }
    }

    private AudioDeviceInfo findOutputDevice(int id) {
        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.getId() == id) {
                return device;
            }
        }
        return null;
    }

    private AudioDeviceInfo findInputDevice(int id) {
        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (device.getId() == id) {
                return device;
            }
        }
        return null;
    }

    private CalibrationSnapshot readCalibrationSnapshot() {
        String manufacturer = preferences.getString(KEY_DEVICE_MANUFACTURER, "");
        String model = preferences.getString(KEY_DEVICE_MODEL, "");
        String outputIdText = preferences.getString(KEY_OUTPUT_DEVICE_ID, "");
        String inputIdText = preferences.getString(KEY_INPUT_DEVICE_ID, "");
        if (manufacturer == null || manufacturer.isEmpty() || model == null || model.isEmpty()) {
            return null;
        }
        if (outputIdText == null || outputIdText.isEmpty() || inputIdText == null || inputIdText.isEmpty()) {
            return null;
        }
        try {
            return new CalibrationSnapshot(manufacturer, model, Integer.parseInt(outputIdText), Integer.parseInt(inputIdText));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String currentDeviceKey() {
        return (Build.MANUFACTURER + "/" + Build.MODEL).toLowerCase(Locale.ROOT);
    }

    private static final class CalibrationSnapshot {
        private final String manufacturer;
        private final String model;
        private final int outputDeviceId;
        private final int inputDeviceId;

        private CalibrationSnapshot(String manufacturer, String model, int outputDeviceId, int inputDeviceId) {
            this.manufacturer = manufacturer;
            this.model = model;
            this.outputDeviceId = outputDeviceId;
            this.inputDeviceId = inputDeviceId;
        }

        private boolean matchesCurrentDevice() {
            return manufacturer.equals(Build.MANUFACTURER) && model.equals(Build.MODEL);
        }
    }

    public static final class PreparedRoute {
        private final String requestedPreset;
        private final String appliedPreset;
        private final String deviceModel;
        private final String bindingStatus;
        private final String outputBinding;
        private final String inputBinding;
        private final String errorMessage;
        private final AudioDeviceInfo outputDevice;
        private final AudioDeviceInfo inputDevice;

        private PreparedRoute(String requestedPreset,
                              String appliedPreset,
                              String deviceModel,
                              String bindingStatus,
                              String outputBinding,
                              String inputBinding,
                              String errorMessage,
                              AudioDeviceInfo outputDevice,
                              AudioDeviceInfo inputDevice) {
            this.requestedPreset = requestedPreset;
            this.appliedPreset = appliedPreset;
            this.deviceModel = deviceModel;
            this.bindingStatus = bindingStatus;
            this.outputBinding = outputBinding;
            this.inputBinding = inputBinding;
            this.errorMessage = errorMessage;
            this.outputDevice = outputDevice;
            this.inputDevice = inputDevice;
        }

        public static PreparedRoute defaultRoute(String deviceModel) {
            return new PreparedRoute("", "", deviceModel, "default", "", "", "", null, null);
        }

        public static PreparedRoute bound(String requestedPreset,
                                          String deviceModel,
                                          AudioDeviceInfo outputDevice,
                                          AudioDeviceInfo inputDevice,
                                          String outputBinding,
                                          String inputBinding) {
            return new PreparedRoute(requestedPreset, requestedPreset, deviceModel, "applied", outputBinding, inputBinding, "", outputDevice, inputDevice);
        }

        public static PreparedRoute failed(String requestedPreset, String deviceModel, String errorMessage) {
            return new PreparedRoute(requestedPreset, "", deviceModel, "failed", "", "", errorMessage, null, null);
        }

        public AudioDeviceInfo getOutputDevice() {
            return outputDevice;
        }

        public AudioDeviceInfo getInputDevice() {
            return inputDevice;
        }

        public Map<String, Object> toStatusMap(String outputName) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("output_name", outputName);
            data.put("requested_preset", requestedPreset);
            data.put("applied_preset", appliedPreset);
            data.put("device_model", deviceModel);
            data.put("binding_status", bindingStatus);
            data.put("output_binding", outputBinding);
            data.put("input_binding", inputBinding);
            data.put("error_message", errorMessage);
            return data;
        }
    }
}
