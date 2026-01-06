package com.lannooo.server;

import com.lannooo.common.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.logging.Logger;

public class ServerEncoder extends MessageToByteEncoder<Message> {
    public static final Logger logger = Utils.getLogger(ServerEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Message data, ByteBuf byteBuf) throws Exception {
        logger.info("Encoding data: " + data.toString());

        int length = data.getPayload().length;
        byteBuf.writeInt(Message.MAGIC);
        byteBuf.writeInt(data.getType().ordinal());
        byteBuf.writeInt(length);
        byteBuf.writeBytes(data.getPayload());
    }
}
