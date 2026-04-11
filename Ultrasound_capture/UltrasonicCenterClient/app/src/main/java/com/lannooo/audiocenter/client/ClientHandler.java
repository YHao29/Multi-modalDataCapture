package com.lannooo.audiocenter.client;

import static com.lannooo.audiocenter.tool.MessageUtil.fileUploadRequest;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.util.Log;

import com.lannooo.audiocenter.audio.AudioEventListener;
import com.lannooo.audiocenter.audio.ClientAudioHandler;
import com.lannooo.audiocenter.audio.UltrasonicConfig;
import com.lannooo.audiocenter.audio.UploadingFileItem;
import com.lannooo.audiocenter.tool.HandlerUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.stream.ChunkedNioFile;

public class ClientHandler extends SimpleChannelInboundHandler<Message> {
    public static final String TAG = "ClientHandler";
    private static final int CAPTURE_START_BEEP_TOTAL_MILLIS = 1000;

    private final ClientAudioHandler audioHandler;
    private final MessageListener listener;
    private final Map<String, RequestHandler> requestHandlers;
    private final ExecutorService executor;
    private final ClientService clientService;

    public ClientHandler(ClientService clientService) {
        super();
        this.clientService = clientService;
        this.audioHandler = clientService.getAudioHandler();
        this.listener = clientService.getListener();
        this.executor = clientService.getExecutor();
        this.requestHandlers = registerHandlers();
    }

    private Map<String, RequestHandler> registerHandlers() {
        Map<String, RequestHandler> handles = new HashMap<>();
        handles.put("capture", this::handleCaptureRequest);
        handles.put("playback", this::handlePlaybackRequest);
        handles.put("download", this::handleDownloadFileRequest);
        handles.put("upload", this::handleUploadFileRequest);
        handles.put("delete", this::handleFileDeleteRequest);
        handles.put("list", this::handleFileListRequest);
        return Collections.unmodifiableMap(handles);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        if (listener != null) {
            listener.onMessageReceived(false, msg.getType(), msg.toString());
        }

        if (msg.getType() == Message.MessageType.REQUEST) {
            String payloadData = new String(msg.getPayload());
            MessageRequest request = MessageRequest.fromJsonString(payloadData);
            RequestHandler handler = requestHandlers.get(request.getSubtype());
            if (handler != null) {
                handler.handleMessage(ctx, request);
            } else {
                Log.e(TAG, "No handler found for " + request.getSubtype());
                writeShortResponse(ctx, "Oops!");
            }
            clientService.updateRequestTime();
        } else if (msg.getType() == Message.MessageType.DATA_TRANSFER) {
            byte[] payload = msg.getPayload();
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            UploadingFileItem fileItem = audioHandler.writeUploadingFile(ctx, buf);
            if (fileItem != null) {
                if (fileItem.isFinished()) {
                    writeShortResponse(ctx, "File uploaded");
                } else if (fileItem.isFailed()) {
                    writeShortResponse(ctx, "File upload failed");
                }
            }
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        audioHandler.cacheServerChannel(ctx);
        clientService.acquireWakeLock();
        clientService.updateRequestTime();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        audioHandler.clearServerChannel();
        clientService.releaseWakeLock();
    }

    private void handleFileListRequest(ChannelHandlerContext ctx, MessageRequest request) {
        String[] files = audioHandler.getBaseDir().list();
        writeShortResponse(ctx, String.join("\n", files));
    }

    private void handleFileDeleteRequest(ChannelHandlerContext ctx, MessageRequest request) {
        Map<String, Object> commands = request.getData();
        String path = (String) Objects.requireNonNull(commands.get("filepath"));
        Path filepath = Paths.get(path);
        if (!filepath.isAbsolute()) {
            filepath = audioHandler.getBaseDir().toPath().resolve(filepath);
        }
        try {
            if (Files.exists(filepath)) {
                Files.delete(filepath);
                writeShortResponse(ctx, "Deleted");
            } else {
                writeShortResponse(ctx, "Not found");
            }
        } catch (Exception e) {
            writeShortResponse(ctx, "Delete failed");
            throw new RuntimeException(e);
        }
    }

    private void handlePlaybackRequest(ChannelHandlerContext ctx, MessageRequest request) {
        Map<String, Object> commands = request.getData();
        String action = (String) commands.get("action");
        if ("start".equalsIgnoreCase(action)) {
            String playFile = (String) Objects.requireNonNull(commands.get("input"));
            String mode = (String) Objects.requireNonNull(commands.get("mode"));
            boolean loop = (boolean) Objects.requireNonNull(commands.get("loop"));
            audioHandler.configurePlayer(playFile, mode, loop, new AudioEventListener() {
                @Override
                public void onPlaybackStop() {
                    if (!loop) {
                        writeShortResponse(ctx, "Stopped Playback: " + playFile);
                    }
                }
            });
            audioHandler.startPlayer();
            writeShortResponse(ctx, "Started Playback: " + playFile);
        } else if ("stop".equalsIgnoreCase(action)) {
            audioHandler.stopPlayer();
            writeShortResponse(ctx, "Stopped Playback");
        } else if ("pause".equalsIgnoreCase(action)) {
            audioHandler.pausePlayer();
            writeShortResponse(ctx, "Paused Playback");
        } else if ("resume".equalsIgnoreCase(action)) {
            audioHandler.resumePlayer();
            writeShortResponse(ctx, "Resumed Playback");
        } else {
            writeShortResponse(ctx, "Oops! Invalid action for playback");
        }
    }

    private void handleCaptureRequest(ChannelHandlerContext ctx, MessageRequest request) {
        Map<String, Object> commands = request.getData();
        String action = (String) commands.get("action");
        if ("start".equalsIgnoreCase(action)) {
            String rawOutputName = (String) Objects.requireNonNull(commands.get("output"));
            String mode = (String) Objects.requireNonNull(commands.get("mode"));
            double duration = (double) Objects.requireNonNull(commands.get("duration"));
            boolean process = (boolean) Objects.requireNonNull(commands.get("process"));
            boolean forward = (boolean) Objects.requireNonNull(commands.get("forward"));
            boolean postDelete = (boolean) Objects.requireNonNull(commands.get("delete"));
            boolean enableUltra = (boolean) Objects.requireNonNull(commands.get("ultra"));
            UltrasonicConfig ultrasonicConfig = UltrasonicConfig.fromCommandMap(commands);
            audioHandler.setUltrasonicConfig(ultrasonicConfig);

            final boolean isCustom = !mode.equalsIgnoreCase("simple");
            final String outputName = HandlerUtil.formatOutputWavFileName(rawOutputName, isCustom ? "wav" : "m4a");
            executor.submit(() -> {
                try {
                    playCaptureStartBeep();
                    audioHandler.configureRecorder(outputName, (int) duration, process, isCustom, enableUltra, new AudioEventListener() {
                        @Override
                        public void onRecordStart() {
                            if (isCustom && duration > 0) {
                                executor.submit(() -> {
                                    try {
                                        Thread.sleep((long) (duration * 1000));
                                        audioHandler.stopRecorder();
                                    } catch (InterruptedException e) {
                                        Log.e(TAG, "Sleep interrupted", e);
                                        Thread.currentThread().interrupt();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onRecordStop(File outputFile) {
                            if (forward) {
                                executor.submit(() -> {
                                    try {
                                        uploadFileByChunk(ctx, outputFile, postDelete);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error while forwarding file: " + e.getMessage(), e);
                                    }
                                });
                            }
                        }
                    });

                    audioHandler.startRecorder();
                    writeShortResponse(ctx, "Started Recording: " + outputName);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start recording with pre-cue", e);
                    writeShortResponse(ctx, "Failed to start Recording: " + outputName);
                }
            });
        } else if ("stop".equalsIgnoreCase(action)) {
            audioHandler.stopRecorder();
            writeShortResponse(ctx, "Stopped Recording");
        } else if ("pause".equalsIgnoreCase(action)) {
            audioHandler.pauseRecorder();
            writeShortResponse(ctx, "Paused Recording");
        } else if ("resume".equalsIgnoreCase(action)) {
            audioHandler.resumeRecorder();
            writeShortResponse(ctx, "Resumed Recording");
        } else {
            writeShortResponse(ctx, "Oops! Invalid action for recording");
        }
    }

    private void playCaptureStartBeep() {
        ToneGenerator ringTone = null;
        ToneGenerator alarmTone = null;
        try {
            ringTone = new ToneGenerator(AudioManager.STREAM_RING, 100);
            alarmTone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);

            ringTone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250);
            alarmTone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250);
            Thread.sleep(320L);

            ringTone.startTone(ToneGenerator.TONE_PROP_ACK, 250);
            alarmTone.startTone(ToneGenerator.TONE_PROP_ACK, 250);
            Thread.sleep(CAPTURE_START_BEEP_TOTAL_MILLIS - 320);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Capture start beep interrupted", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to play capture start beep", e);
        } finally {
            if (ringTone != null) {
                ringTone.release();
            }
            if (alarmTone != null) {
                alarmTone.release();
            }
        }
    }

    private void handleUploadFileRequest(ChannelHandlerContext ctx, MessageRequest request) {
        audioHandler.addUploadingFile(ctx, request.getData());
        writeShortResponse(ctx, "Ready to receive chunks");
    }

    private void handleDownloadFileRequest(ChannelHandlerContext ctx, MessageRequest request) {
        Map<String, Object> commands = request.getData();
        String path = (String) Objects.requireNonNull(commands.get("file"));
        boolean postDelete = (boolean) Objects.requireNonNull(commands.get("delete"));
        Path filepath = audioHandler.getBaseDir().toPath().resolve(path);

        if (Files.exists(filepath)) {
            executor.submit(() -> uploadFileByChunk(ctx, filepath.toFile(), postDelete));
        } else {
            writeShortResponse(ctx, "Not found");
        }
    }

    private void uploadFileByChunk(ChannelHandlerContext ctx, File file, boolean postDelete) {
        try {
            ChunkedNioFile chunkedNioFile = new ChunkedNioFile(file, 2048);
            int length = (int) chunkedNioFile.length();
            int chunks = length / 2048;
            if (length % 2048 != 0) {
                chunks++;
            }

            String uploadReqPayload = fileUploadRequest(file, length, chunks);
            Message uploadReq = new Message(Message.MessageType.REQUEST, uploadReqPayload.getBytes());
            ctx.writeAndFlush(uploadReq).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess() && listener != null) {
                    listener.onMessageReceived(true, uploadReq.getType(), uploadReqPayload);
                }
            });

            ByteBufAllocator alloc = ctx.alloc();
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
                ctx.writeAndFlush(new Message(Message.MessageType.DATA_TRANSFER, tgt));
            }

            if (postDelete) {
                file.delete();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeShortResponse(ChannelHandlerContext ctx, String x) {
        Message msg = new Message(Message.MessageType.RESPONSE, x.getBytes());
        ctx.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess() && listener != null) {
                listener.onMessageReceived(true, Message.MessageType.RESPONSE, x);
            }
        });
    }

    public interface RequestHandler {
        void handleMessage(ChannelHandlerContext ctx, MessageRequest request);
    }
}