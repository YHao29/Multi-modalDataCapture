package com.lannooo.audiocenter.audio;

import java.io.File;

public interface AudioEventListener {
    default void onRecordStart() {};
    default void onRecordStop(File outputFile) {};

    default void onPlaybackStart() {};
    default void onPlaybackStop() {};
}
