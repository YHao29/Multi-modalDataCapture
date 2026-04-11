package com.lannooo.server;

import com.lannooo.common.Utils;
import com.lannooo.device.DeviceManager;
import com.lannooo.device.UploadingFileItem;
import com.lannooo.service.AsyncService;
import com.lannooo.shell.ShellHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

public class ServerHandler extends SimpleChannelInboundHandler<Message> {
    public static final Logger logger = Utils.getLogger(ServerHandler.class);

    private final AsyncService asyncService;
    private final DeviceManager deviceManager;
    private final ShellHelper shellHelper;
    private final Map<String, RequestHandler> requestHandlers = Map.of(
            "register", this::handleRegisterRequest,
            "upload", this::handleUploadFileRequest
    );

    public ServerHandler(AsyncService asyncService, DeviceManager deviceManager, ShellHelper shellHelper) {
        this.asyncService = asyncService;
        this.deviceManager = deviceManager;
        this.shellHelper = shellHelper;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        logger.info("Handler received data: " + msg.toString());

        if (msg.getType() == Message.MessageType.REQUEST) {
            String payloadData = new String(msg.getPayload());
            MessageRequest request = MessageRequest.fromJsonString(payloadData);
            RequestHandler handler = requestHandlers.get(request.getSubtype());
            if (handler != null) {
                handler.handleMessage(ctx, request);
            } else {
                logger.severe("Unknown request: " + request.getSubtype());
                writeShortResponse(ctx, "Oops!");
            }
        } else if (msg.getType() == Message.MessageType.DATA_TRANSFER) {
            byte[] payload = msg.getPayload();
            // payload to byteBuf
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            UploadingFileItem fileItem = deviceManager.writeUploadingFile(ctx, buf);
            if (fileItem != null) {
                if (fileItem.isFinished()) {
                    writeShortResponse(ctx, "File uploaded");
                } else if (fileItem.isFailed()) {
                    writeShortResponse(ctx, "File upload failed");
                }
            }
        } else if (msg.getType() == Message.MessageType.RESPONSE) {
            // Display response from the client to the terminal
            asyncService.submit(() -> shellHelper.printInfo("[" + deviceManager.uniqueKey(ctx) + "] " + new String(msg.getPayload())));
        }
    }

    private void handleRegisterRequest(ChannelHandlerContext ctx, MessageRequest request) {
        // save the client information and register its connection
        deviceManager.registerOrUpdateRemoteDevice(ctx, request.getData());
        writeShortResponse(ctx, "Registered");
    }

    private void handleUploadFileRequest(ChannelHandlerContext ctx, MessageRequest request) {
        // save the file upload session
        Map<String, Object> data = request.getData();
        deviceManager.addUploadingFile(ctx, data);
        writeShortResponse(ctx, "Ready to receive chunks");
    }

    private void writeShortResponse(ChannelHandlerContext ctx, String OK) {
        ctx.writeAndFlush(new Message(Message.MessageType.RESPONSE, OK.getBytes()));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        logger.info("Client connected: " + ctx);

        deviceManager.registerOrUpdateRemoteDevice(ctx, Collections.emptyMap());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        logger.info("Client disconnected: " + ctx);

        deviceManager.unregisterRemoteDevice(ctx);
    }

    public interface RequestHandler {
        void handleMessage(ChannelHandlerContext ctx, MessageRequest request);
    }
}
