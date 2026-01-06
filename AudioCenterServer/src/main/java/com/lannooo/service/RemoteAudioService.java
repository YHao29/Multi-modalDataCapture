package com.lannooo.service;

import com.lannooo.common.Utils;
import com.lannooo.device.ChannelManager;
import com.lannooo.device.FileUploadListener;
import com.lannooo.server.Message;
import com.lannooo.server.MessageRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.stream.ChunkedNioFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;


@Component
public class RemoteAudioService {
    private static final Logger logger = Utils.getLogger(RemoteAudioService.class);

    @Autowired
    private ChannelManager channelManager;

    public void captureAudio(String key,
                             String action,
                             String mode,
                             String output,
                             int duration,
                             boolean process,
                             boolean forward,
                             boolean postDelete,
                             boolean ultrasonic) {
        Channel ch = Objects.requireNonNull(channelManager.getChannel(key));
        MessageRequest request = new MessageRequest("capture");
        request.put("action", action);
        if ("start".equalsIgnoreCase(action)) {
            request.put("mode", mode);
            request.put("output", output);
            request.put("duration", duration);
            request.put("process", process);
            request.put("forward", forward);
            request.put("delete", postDelete);
            request.put("ultra", ultrasonic);
        }
        Message message = new Message(Message.MessageType.REQUEST, request.toJsonString().getBytes());
        ch.writeAndFlush(message);
    }

    public void playAudio(String key, String action, String mode, boolean enableLoop, String inputFile) {
        Channel ch = Objects.requireNonNull(channelManager.getChannel(key));
        MessageRequest request = new MessageRequest("playback");
        request.put("action", action);
        if ("start".equalsIgnoreCase(action)) {
            request.put("mode", mode);
            request.put("loop", enableLoop);
            request.put("input", inputFile);
        }
        Message message = new Message(Message.MessageType.REQUEST, request.toJsonString().getBytes());
        ch.writeAndFlush(message);
    }

    public void uploadFile(String key,
                           File file,
                           FileUploadListener listener) {
        Channel ch = Objects.requireNonNull(channelManager.getChannel(key));
        try {
            // read file data chunk by chunk and send Messages to server
            ChunkedNioFile chunkedNioFile = new ChunkedNioFile(file, 2048);
            int length = (int) chunkedNioFile.length();
            int chunks = length / 2048;
            if (length % 2048 != 0) {
                chunks++;
            }

            // request the server to prepare for file upload
            MessageRequest request = new MessageRequest("upload");
            request.put("filepath", file.toPath().getFileName().toString());
            request.put("chunks", chunks);
            request.put("length", length);
            Message message = new Message(Message.MessageType.REQUEST, request.toJsonString().getBytes());
            ch.writeAndFlush(message).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    logger.info("Upload request sent");
                    if (listener != null) {
                        listener.onStart("Upload started");
                    }
                } else {
                    logger.severe("Upload request failed: " + future.cause().getMessage());
                    if (listener != null) {
                        listener.onFailed("Upload request sent failed");
                    }
                }
            });


            ByteBufAllocator alloc = ch.alloc();
            int chunkId = 0;
            while (!chunkedNioFile.isEndOfInput()) {
                long offset = chunkedNioFile.currentOffset();
                ByteBuf byteBuf = chunkedNioFile.readChunk(alloc);
                ByteBuf tgt = alloc.buffer(byteBuf.readableBytes() + 16)
                        .writeInt(++chunkId)
                        .writeInt(chunks)
                        .writeInt((int) offset)
                        .writeInt(length)
                        .writeBytes(byteBuf);
                logger.info("Sending chunk: " + chunkId + "/" + chunks + " position: " + offset + "/" + length);
                // flush every time
                ch.writeAndFlush(new Message(Message.MessageType.DATA_TRANSFER, tgt));
                if (listener != null) {
                    listener.onProgress(chunkId, chunks);
                }
            }
            if (listener != null) {
                listener.onSuccess("Upload completed");
            }
        } catch (IOException e) {
            logger.severe("Error while reading file: " + e.getMessage());
            if (listener != null) {
                listener.onFailed("Error while reading file");
            }
            throw new RuntimeException(e);
        } catch (Exception e) {
            logger.severe("Error while sending file: " + e.getMessage());
            if (listener != null) {
                listener.onFailed("Error while sending file");
            }
            throw new RuntimeException(e);
        }
    }

    public void deleteFile(String key,
                           String path) {
        Channel ch = Objects.requireNonNull(channelManager.getChannel(key));
        MessageRequest request = new MessageRequest("delete");
        request.put("filepath", path);
        Message message = new Message(Message.MessageType.REQUEST, request.toJsonString().getBytes());
        ch.writeAndFlush(message);
    }

    public void listFiles(String key) {
        Channel ch = Objects.requireNonNull(channelManager.getChannel(key));
        MessageRequest request = new MessageRequest("list");
        Message message = new Message(Message.MessageType.REQUEST, request.toJsonString().getBytes());
        ch.writeAndFlush(message);
    }
}
