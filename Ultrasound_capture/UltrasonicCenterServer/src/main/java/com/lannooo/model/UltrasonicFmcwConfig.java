package com.lannooo.model;

public class UltrasonicFmcwConfig {
    private boolean enabled = true;
    private String mode = "fmcw";
    private String routePreset = "";
    private int sampleRateHz = 48000;
    private double startFreqHz = 18000.0;
    private double endFreqHz = 21000.0;
    private int chirpDurationMs = 30;
    private int idleDurationMs = 10;
    private double amplitude = 0.8;
    private String windowType = "hann";
    private boolean repeat = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getRoutePreset() {
        return routePreset;
    }

    public void setRoutePreset(String routePreset) {
        this.routePreset = routePreset;
    }

    public int getSampleRateHz() {
        return sampleRateHz;
    }

    public void setSampleRateHz(int sampleRateHz) {
        this.sampleRateHz = sampleRateHz;
    }

    public double getStartFreqHz() {
        return startFreqHz;
    }

    public void setStartFreqHz(double startFreqHz) {
        this.startFreqHz = startFreqHz;
    }

    public double getEndFreqHz() {
        return endFreqHz;
    }

    public void setEndFreqHz(double endFreqHz) {
        this.endFreqHz = endFreqHz;
    }

    public int getChirpDurationMs() {
        return chirpDurationMs;
    }

    public void setChirpDurationMs(int chirpDurationMs) {
        this.chirpDurationMs = chirpDurationMs;
    }

    public int getIdleDurationMs() {
        return idleDurationMs;
    }

    public void setIdleDurationMs(int idleDurationMs) {
        this.idleDurationMs = idleDurationMs;
    }

    public double getAmplitude() {
        return amplitude;
    }

    public void setAmplitude(double amplitude) {
        this.amplitude = amplitude;
    }

    public String getWindowType() {
        return windowType;
    }

    public void setWindowType(String windowType) {
        this.windowType = windowType;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }
}
