package com.lannooo.audiocenter.client;

public interface MessageListener {
    void onMessageReceived(boolean fromMe, Message.MessageType type, String shortContent);
}
