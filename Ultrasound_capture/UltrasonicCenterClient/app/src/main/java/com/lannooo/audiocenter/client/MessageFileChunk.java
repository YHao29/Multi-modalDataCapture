package com.lannooo.audiocenter.client;

public class MessageFileChunk {
    private final int chunkId;
    private final int totalChunks;
    private final int currentOffset;
    private final int totalSize;
    private final byte[] data;

    public MessageFileChunk(int chunkId, int totalChunks, int currentOffset, int totalSize, byte[] data) {
        this.chunkId = chunkId;
        this.totalChunks = totalChunks;
        this.currentOffset = currentOffset;
        this.totalSize = totalSize;
        this.data = data;
    }

    public byte[] writeAsPayload() {
        return new byte[0];
    }

    public int getChunkId() {
        return chunkId;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getCurrentOffset() {
        return currentOffset;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public byte[] getData() {
        return data;
    }
}
