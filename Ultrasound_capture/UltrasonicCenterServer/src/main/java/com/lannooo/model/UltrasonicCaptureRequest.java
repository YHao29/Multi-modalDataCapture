package com.lannooo.model;

public class UltrasonicCaptureRequest {
    private String deviceId = "ALL";
    private String output = "ultrasonic_capture.wav";
    private int durationSeconds = 5;
    private boolean process = false;
    private boolean forward = true;
    private boolean deleteAfterForward = false;
    private String mode = "pro";
    private UltrasonicFmcwConfig ultrasonic = new UltrasonicFmcwConfig();

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public boolean isProcess() { return process; }
    public void setProcess(boolean process) { this.process = process; }
    public boolean isForward() { return forward; }
    public void setForward(boolean forward) { this.forward = forward; }
    public boolean isDeleteAfterForward() { return deleteAfterForward; }
    public void setDeleteAfterForward(boolean deleteAfterForward) { this.deleteAfterForward = deleteAfterForward; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public UltrasonicFmcwConfig getUltrasonic() { return ultrasonic; }
    public void setUltrasonic(UltrasonicFmcwConfig ultrasonic) { this.ultrasonic = ultrasonic; }
}
