package com.lannooo.audiocenter.client;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class ClientDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Data Frame = Magic number (4 bytes) + Type (4 bytes) + Length (4 bytes) + Payload
        // check if the magic number is correct
        if (in.readInt() != Message.MAGIC) {
            throw new IllegalArgumentException("Invalid magic number");
        }
        // read the message type
        int typeOrdinal = in.readInt();
        // read the length of the message
        int length = in.readInt();
        // read the payload
        ByteBuf payload = in.readBytes(length);

        // create a new message object
        Message message = new Message(Message.MessageType.fromOrdinal(typeOrdinal), payload);
        out.add(message);
    }
}
