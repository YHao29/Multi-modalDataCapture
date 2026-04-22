package com.lannooo.audiocenter.client;

import android.util.Log;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ClientEncoder extends MessageToByteEncoder<Message> {
    public static final String TAG = "ClientEncoder";

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        Log.i(TAG, "encode: " + msg.toString());
        int length = msg.getPayload().length;
        out.writeInt(Message.MAGIC);
        out.writeInt(msg.getType().ordinal());
        out.writeInt(length);
        out.writeBytes(msg.getPayload());
    }
}
