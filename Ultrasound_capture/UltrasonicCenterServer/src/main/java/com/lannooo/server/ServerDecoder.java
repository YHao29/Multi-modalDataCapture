package com.lannooo.server;

import com.lannooo.common.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;
import java.util.logging.Logger;

public class ServerDecoder extends ByteToMessageDecoder {
    public static final Logger logger = Utils.getLogger(ServerDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        // Data Frame = Magic number (4 bytes) + Type (4 bytes) + Length (4 bytes) + Payload
        // check if the magic number is correct
        if (byteBuf.readInt() != Message.MAGIC) {
            throw new IllegalArgumentException("Invalid magic number");
        }
        // read the message type
        int typeOrdinal = byteBuf.readInt();
        // read the length of the message
        int length = byteBuf.readInt();
        // read the payload
        byte[] payload = new byte[length];
        byteBuf.readBytes(payload);

        // create a new message object
        Message message = new Message(Message.MessageType.fromOrdinal(typeOrdinal), payload);
        list.add(message);
    }
}
