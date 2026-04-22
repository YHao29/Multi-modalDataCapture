package com.lannooo.audiocenter.audio;


import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class UploadingFileItem {
    private final String filename;
    private final long chunks;
    private final long length;
    private final String localFilename;
    private RandomAccessFile file;
    private UploadingStatus status;

    public UploadingFileItem(String basedir,
                             String filename,
                             long chunks,
                             long length) {
        this.filename = filename;
        String name = Paths.get(filename).getFileName().toString();
        this.localFilename = Paths.get(basedir, "server", name).toString();
        this.chunks = chunks;
        this.length = length;
        this.status = UploadingStatus.UPLOADING;
    }

    public void close() {
        if (file == null) return;
        try {
            file.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getLocalFilename() {
        return localFilename;
    }

    public String getRemoteFilename() {
        return filename;
    }

    public long getChunks() {
        return chunks;
    }

    public long getLength() {
        return length;
    }

    public boolean isFinished() {
        return status == UploadingStatus.FINISHED;
    }

    public boolean isFailed() {
        return status == UploadingStatus.FAILED;
    }

    public UploadingFileItem failed() {
        this.status = UploadingStatus.FAILED;
        return this;
    }

    public UploadingFileItem finished() {
        this.status = UploadingStatus.FINISHED;
        return this;
    }

    public void writeChunk(long offset, byte[] data) throws IOException {
        if (file == null) {
            Path localFile = Paths.get(localFilename);
            Path localFileDir = localFile.getParent();
            if (!Files.exists(localFileDir)) {
                Files.createDirectories(localFileDir);
            }
            if (Files.exists(localFile)) {
                Files.delete(localFile);
            }
            file = new RandomAccessFile(localFile.toFile(), "rw");
        }
        file.seek(offset);
        file.write(data);
    }

    public enum UploadingStatus {
        UPLOADING,
        FINISHED,
        FAILED
    }
}
