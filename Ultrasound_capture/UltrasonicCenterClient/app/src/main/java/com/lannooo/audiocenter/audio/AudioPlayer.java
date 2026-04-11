package com.lannooo.audiocenter.audio;

public interface AudioPlayer {
    enum PlayerStatus {
        INIT,
        READY,
        PLAYING,
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
