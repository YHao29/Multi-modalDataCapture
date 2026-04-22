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
        if (outputName.endsWith(ext)) {
            return outputName;
        }

        int lastSlash = Math.max(outputName.lastIndexOf('/'), outputName.lastIndexOf('\\'));
        int lastDot = outputName.lastIndexOf('.');
        if (lastDot > lastSlash) {
            outputName = outputName.substring(0, lastDot);
        }
        return outputName + ext;
    }
}
