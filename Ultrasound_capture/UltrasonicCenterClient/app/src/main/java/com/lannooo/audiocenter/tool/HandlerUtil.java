package com.lannooo.audiocenter.tool;

import org.jetbrains.annotations.NotNull;

public class HandlerUtil {
    public static final String TAG = "HandlerUtil";

    public static String formatOutputWavFileName(@NotNull String outputName, @NotNull String ext) {
        if (ext.isEmpty()) {
            ext = ".wav";
        }
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        if (!outputName.endsWith(ext)) {
            outputName += ext;
        }
        return outputName;
    }
}
