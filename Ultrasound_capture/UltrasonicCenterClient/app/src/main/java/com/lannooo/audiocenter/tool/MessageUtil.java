package com.lannooo.audiocenter.tool;

import android.os.Build;

import com.lannooo.audiocenter.client.MessageRequest;

import java.io.File;
import java.util.Map;

public class MessageUtil {

    public static String registerRequest(Map<String, Object> extraInfo) {
        // return a json string representing the register request
        // information about this phone is sent to the server
        MessageRequest request = new MessageRequest("register");
        request.put("Model", Build.MODEL);
        request.put("Manufacturer", Build.MANUFACTURER);
        request.put("Brand", Build.BRAND);
        request.put("Display", Build.DISPLAY);
        request.put("SDK", Build.VERSION.SDK_INT);
        request.put("Release", Build.VERSION.RELEASE);
        if (extraInfo != null) {
            for (Map.Entry<String, Object> entry : extraInfo.entrySet()) {
                request.put(entry.getKey(), entry.getValue());
            }
        }

        return request.toJsonString();
    }

    public static String fileUploadRequest(File file, long length, long chunks) {
        MessageRequest request = new MessageRequest("upload");
        request.put("filepath", file.getAbsolutePath());
        request.put("chunks", chunks);
        request.put("length", length);
        return request.toJsonString();
    }
}
