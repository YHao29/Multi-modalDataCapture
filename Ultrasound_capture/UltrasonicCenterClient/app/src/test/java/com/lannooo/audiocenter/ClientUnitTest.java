package com.lannooo.audiocenter;

import com.lannooo.audiocenter.client.Message;
import com.lannooo.audiocenter.tool.AppUtil;

import org.junit.Assert;
import org.junit.Test;

public class ClientUnitTest {
    @Test
    public void testMessage() {
        byte[] payload = new byte[100];
        for (int i = 0; i < 100; i++) {
            payload[i] = (byte) i;
        }
        Message message = new Message(Message.MessageType.REQUEST, payload);
        System.out.println(message);
        System.out.println(Message.MAGIC);
        Assert.assertEquals(Message.MessageType.REQUEST.ordinal(), 0);
        Assert.assertEquals(Message.MessageType.REQUEST, Message.MessageType.fromOrdinal(0));
    }

    @Test
    public void testTimeFormat() {
        String time = AppUtil.currentDateTime();
        System.out.println(time);
    }
}
