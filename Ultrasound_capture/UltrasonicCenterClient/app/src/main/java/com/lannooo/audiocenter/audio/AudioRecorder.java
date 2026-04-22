package com.lannooo.audiocenter.audio;

public interface AudioRecorder {
    enum RecorderStatus {
        INIT,
        READY,
        RECORDING,
        PAUSED,
        STOPPED
    }
    void start();
    void stop();
    void pause();
    void resume();

    default void setListener(AudioEventListener listener) {
        // do nothing
    }
}
