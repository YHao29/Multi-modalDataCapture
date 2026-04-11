package com.lannooo.audiocenter.audio;

import java.util.Map;

public class UltrasonicConfig {
    private boolean enabled = true;
    private String mode = "fmcw";
    private int sampleRateHz = AudioConstants.AUDIO_SAMPLE_RATE_48000;
    private double startFreqHz = 20000.0;
    private double endFreqHz = 22000.0;
    private int chirpDurationMs = 40;
    private int idleDurationMs = 0;
    private double amplitude = 0.30;
    private String windowType = "hann";
    private boolean repeat = true;

    public static UltrasonicConfig fromCommandMap(Map<String, Object> commands) {
        UltrasonicConfig config = new UltrasonicConfig();
        if (commands == null) {
            return config;
        }
        config.enabled = readBoolean(commands, "ultra", true);
        config.mode = readString(commands, "ultra_mode", config.mode);
        config.sampleRateHz = readInt(commands, "ultra_sample_rate_hz", config.sampleRateHz);
        config.startFreqHz = readDouble(commands, "ultra_start_freq_hz", config.startFreqHz);
        config.endFreqHz = readDouble(commands, "ultra_end_freq_hz", config.endFreqHz);
        config.chirpDurationMs = readInt(commands, "ultra_chirp_duration_ms", config.chirpDurationMs);
        config.idleDurationMs = readInt(commands, "ultra_idle_duration_ms", config.idleDurationMs);
        config.amplitude = readDouble(commands, "ultra_amplitude", config.amplitude);
        config.windowType = readString(commands, "ultra_window_type", config.windowType);
        config.repeat = readBoolean(commands, "ultra_repeat", config.repeat);
        return config;
    }

    public static UltrasonicConfig manual(boolean enabled,
                                          int sampleRateHz,
                                          double startFreqHz,
                                          double endFreqHz,
                                          int chirpDurationMs,
                                          int idleDurationMs,
                                          double amplitude,
                                          String windowType,
                                          boolean repeat) {
        UltrasonicConfig config = new UltrasonicConfig();
        config.enabled = enabled;
        config.sampleRateHz = sampleRateHz;
        config.startFreqHz = startFreqHz;
        config.endFreqHz = endFreqHz;
        config.chirpDurationMs = chirpDurationMs;
        config.idleDurationMs = idleDurationMs;
        config.amplitude = amplitude;
        config.windowType = windowType;
        config.repeat = repeat;
        return config;
    }

    public UltrasonicConfig copy() {
        return manual(enabled, sampleRateHz, startFreqHz, endFreqHz, chirpDurationMs, idleDurationMs, amplitude, windowType, repeat);
    }

    private static int readInt(Map<String, Object> commands, String key, int defaultValue) {
        Object value = commands.get(key);
        return value instanceof Number ? ((Number) value).intValue() : defaultValue;
    }

    private static double readDouble(Map<String, Object> commands, String key, double defaultValue) {
        Object value = commands.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : defaultValue;
    }

    private static boolean readBoolean(Map<String, Object> commands, String key, boolean defaultValue) {
        Object value = commands.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private static String readString(Map<String, Object> commands, String key, String defaultValue) {
        Object value = commands.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public int getSampleRateHz() { return sampleRateHz; }
    public void setSampleRateHz(int sampleRateHz) { this.sampleRateHz = sampleRateHz; }
    public double getStartFreqHz() { return startFreqHz; }
    public void setStartFreqHz(double startFreqHz) { this.startFreqHz = startFreqHz; }
    public double getEndFreqHz() { return endFreqHz; }
    public void setEndFreqHz(double endFreqHz) { this.endFreqHz = endFreqHz; }
    public int getChirpDurationMs() { return chirpDurationMs; }
    public void setChirpDurationMs(int chirpDurationMs) { this.chirpDurationMs = chirpDurationMs; }
    public int getIdleDurationMs() { return idleDurationMs; }
    public void setIdleDurationMs(int idleDurationMs) { this.idleDurationMs = idleDurationMs; }
    public double getAmplitude() { return amplitude; }
    public void setAmplitude(double amplitude) { this.amplitude = amplitude; }
    public String getWindowType() { return windowType; }
    public void setWindowType(String windowType) { this.windowType = windowType; }
    public boolean isRepeat() { return repeat; }
    public void setRepeat(boolean repeat) { this.repeat = repeat; }
}
