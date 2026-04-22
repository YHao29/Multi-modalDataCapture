package com.lannooo.audiocenter.audio;

import android.media.MediaRecorder;

public class AudioConstants {
    public static final int AUDIO_SOURCE_MIC = MediaRecorder.AudioSource.MIC;
    public static final int AUDIO_SOURCE_UNPROCESSED = MediaRecorder.AudioSource.UNPROCESSED;
    public static final int AUDIO_SOURCE_UNPROCESSED_VOICE = MediaRecorder.AudioSource.VOICE_RECOGNITION;

    public static final int AUDIO_MONO_CHANNEL = 1;
    public static final int AUDIO_STEREO_CHANNEL = 2;
    public static final int AUDIO_DEFAULT_CHANNEL = AUDIO_MONO_CHANNEL;

    public static final int AUDIO_SAMPLE_RATE_44100 = 44100;
    public static final int AUDIO_SAMPLE_RATE_48000 = 48000;
    public static final int AUDIO_DEFAULT_SAMPLE_RATE = AUDIO_SAMPLE_RATE_44100;

    public static final int AUDIO_BITS_PER_SAMPLE_16 = 16;
    public static final int AUDIO_BITS_PER_SAMPLE_8 = 8;
    public static final int AUDIO_DEFAULT_BITS_PER_SAMPLE = AUDIO_BITS_PER_SAMPLE_16;

    public static final int AUDIO_ENCODING_BIT_RATE_96000 = 96000;
    public static final int AUDIO_ENCODING_BIT_RATE_128000 = 128000;
    public static final int AUDIO_ENCODING_BIT_RATE_192000 = 192000;
    public static final int AUDIO_ENCODING_BIT_RATE_256000 = 256000;
    public static final int AUDIO_DEFAULT_ENCODING_BIT_RATE = AUDIO_ENCODING_BIT_RATE_128000;

    public static final long AUDIO_DEFAULT_MAX_FILE_SIZE = 8L * 1024 * 1024 * 1024; // 1 GB in bits
}
