package com.lannooo.server;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;

public class Message {
    public static final int MAGIC = 0xACC5CCFA;

    public enum MessageType {
        REQUEST,
        RESPONSE,
        DATA_TRANSFER,
        NOTIFICATION;

        // parse from ordinal to type
        public static MessageType fromOrdinal(int ordinal) {
            return MessageType.values()[ordinal];
        }
    }

    private MessageType type;
    private byte[] payload;

    public Message(MessageType type, byte[] payload) {
        this.type = type;
        this.payload = payload;
    }

    public Message(MessageType type, ByteBuf payload) {
        this.type = type;
        if (payload.hasArray()) {
            this.payload = payload.array();
        } else {
            this.payload = new byte[payload.readableBytes()];
            payload.readBytes(this.payload);
        }
    }

    public MessageType getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        // if payload is too long to display, only show the first 64 bytes
        String payloadStr;
        if (type == MessageType.DATA_TRANSFER) {
            payloadStr = "[...]";
        } else {
            payloadStr = new String(payload);
        }
        return "Message{" +
                "type=" + type +
                ", payload=" + payloadStr +
                '}';
    }
}
