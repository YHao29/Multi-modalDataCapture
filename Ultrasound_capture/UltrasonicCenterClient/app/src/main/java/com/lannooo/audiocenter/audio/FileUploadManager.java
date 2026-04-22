package com.lannooo.audiocenter.audio;

import android.util.Log;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;

public class FileUploadManager {
    private static final String TAG = "FileUploadManager";

    // temporary store the uploading file items in the manager
    private final Map<String, UploadingFileItem> uploadingFiles;

    public FileUploadManager() {
        this.uploadingFiles = new ConcurrentHashMap<>(16);
    }

    public boolean hasOngoingTasks() {
        return !uploadingFiles.isEmpty();
    }

    public void removeTask(String key) {
        _removeAndRelease(key);
    }

    public void addTask(String key, String baseDir, String filename, long chunks, long length) {
        UploadingFileItem fileItem = new UploadingFileItem(baseDir, filename, chunks, length);
        uploadingFiles.putIfAbsent(key, fileItem);
    }

    private void _removeAndRelease(String key) {
        UploadingFileItem removed = uploadingFiles.remove(key);
        if (removed != null) {
            removed.close();
        }
    }

    public UploadingFileItem writeChunk(String key, ByteBuf chunkBuf) {
        UploadingFileItem fileItem = uploadingFiles.get(key);
        if (fileItem != null) {
            int chunkId = chunkBuf.readInt();
            int totalChunks = chunkBuf.readInt();
            int offset = chunkBuf.readInt();
            int length = chunkBuf.readInt();

            if (totalChunks != fileItem.getChunks() || length != fileItem.getLength()) {
                _removeAndRelease(key);
                Log.e(TAG, "Invalid chunk data: " + chunkId + "/" + totalChunks + " position: " + offset + "/" + length);
                return fileItem.failed();
            }

            Log.i(TAG, "File upload chunk: " + chunkId + "/" + totalChunks + " position: " + offset + "/" + length);
            try {
                byte[] bytes = new byte[chunkBuf.readableBytes()];
                chunkBuf.readBytes(bytes);
                fileItem.writeChunk(offset, bytes);
            } catch (IOException e) {
                _removeAndRelease(key);  // do not write again the next time
                Log.e(TAG, "Failed to write chunk: " + chunkId + "/" + totalChunks + " position: " + offset + "/" + length);
                throw new RuntimeException(e);
            }
            if (chunkId == totalChunks) {
                _removeAndRelease(key);
                Log.i(TAG, "File upload finished: " + fileItem.getRemoteFilename() + " -> " + fileItem.getLocalFilename());
                return fileItem.finished();
            } else {
                return fileItem;
            }
        } else {
            return null;
        }
    }
}
