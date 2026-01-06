package com.lannooo.device;

public interface FileUploadListener {
    default void onProgress(int progress, int total){};
    default void onStart(String message){};
    default void onFailed(String message){};
    default void onSuccess(String message){};
}
