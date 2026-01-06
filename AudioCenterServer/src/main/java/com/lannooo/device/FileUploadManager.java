package com.lannooo.device;

import com.lannooo.common.Utils;
import io.netty.buffer.ByteBuf;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class FileUploadManager {
    private static final Logger logger = Utils.getLogger(FileUploadManager.class);

    // temporary store the uploading file items in the manager
    private final Map<String, UploadingFileItem> uploadingFiles;
    private final Map<String, String> deviceNames;

    public FileUploadManager() {
        this.uploadingFiles = new ConcurrentHashMap<>(16);
        this.deviceNames = new ConcurrentHashMap<>(16);
    }

    public boolean hasOngoingTasks() {
        return !uploadingFiles.isEmpty();
    }

    public void removeTask(String key) {
        _removeAndRelease(key);
    }

    public void addTask(String key, String subKey, String filename, long chunks, long length) {
        try {
            String mappedKey = deviceNames.getOrDefault(key, key);
            mappedKey = mappedKey.replace('/', '_');
            UploadingFileItem fileItem = new UploadingFileItem(mappedKey, subKey, filename, chunks, length);
            uploadingFiles.putIfAbsent(key, fileItem);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void _removeAndRelease(String key) {
        UploadingFileItem removed = uploadingFiles.remove(key);
        if (removed != null) {
            removed.close();
        }
//        deviceNames.remove(key);
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
                logger.severe("Invalid chunk data: " + chunkId + "/" + totalChunks + " position: " + offset + "/" + length);
                return fileItem.failed();
            }

            logger.info("File upload chunk: " + chunkId + "/" + totalChunks + " position: " + offset + "/" + length);
            try {
                byte[] bytes = new byte[chunkBuf.readableBytes()];
                chunkBuf.readBytes(bytes);
                fileItem.writeChunk(offset, bytes);
            } catch (IOException e) {
                _removeAndRelease(key);  // do not write again the next time
                logger.severe("Failed to write chunk: " + chunkId + "/" + totalChunks + " position: " + offset + "/" + length);
                throw new RuntimeException(e);
            }
            if (chunkId == totalChunks) {
                _removeAndRelease(key);
                logger.info("File upload finished: " + fileItem.getRemoteFilename() + " -> " + fileItem.getLocalFilename());
                return fileItem.finished();
            } else {
                return fileItem;
            }
        } else {
            return null;
        }
    }

    public void registerDeviceName(String key, String name) {
        this.deviceNames.putIfAbsent(key, name);
    }

    public void unregisterDeviceName(String key) {
        this.deviceNames.remove(key);
    }
}
