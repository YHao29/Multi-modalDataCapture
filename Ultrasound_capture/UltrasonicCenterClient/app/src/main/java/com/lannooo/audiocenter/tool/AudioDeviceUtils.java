package com.lannooo.audiocenter.tool;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MicrophoneInfo;
import android.os.Build;
import android.util.Log;

import java.io.IOException;

public class AudioDeviceUtils {
    private static final String TAG = "AudioDeviceUtils";

    public void printDevices(AudioManager audioManager) {
        AudioDeviceInfo[] devices1 = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices1) {
            Log.i(TAG, "Output Device: " + device.getProductName());
        }
        AudioDeviceInfo[] devices2 = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo device : devices2) {
            Log.i(TAG, "Input Device: " + device.getProductName());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                for (MicrophoneInfo microphone : audioManager.getMicrophones()) {
                    Log.i(TAG, "Microphone: " + microphone.getId() + " " + microphone.getDescription());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
