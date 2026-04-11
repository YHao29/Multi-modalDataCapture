package com.lannooo.common;

public class AppConstants {
    public static final String[] CAPTURE_VALID_ACTIONS = {"start", "stop", "pause", "resume"};
    public static final String[] CAPTURE_VALID_MODES = {"simple", "pro"};
    public static final String[] PLAYBACK_VALID_ACTIONS = {"start", "stop", "pause", "resume"};
    public static final String[] PLAYBACK_VALID_MODES = {"music", "voice"};

    public static final String[] VALID_DEVICE_CAPABILITIES = {"capture", "playback", "all"};
    public static final String[] VALID_ENABLE_ACTIONS = {"on", "off"};
    // supports only alphanumeric as well as '.'/'_'/'-'
    public static final String FILENAME_REGEX_PATTERN = "^[a-zA-Z0-9._-]+$";

    public static final String AUDIO_BASE_PATH = "audio";
}
