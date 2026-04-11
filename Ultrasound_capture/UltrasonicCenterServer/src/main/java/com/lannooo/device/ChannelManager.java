package com.lannooo.device;

import com.lannooo.common.Utils;
import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;


@Component
public class ChannelManager {
    private static final Logger logger = Utils.getLogger(ChannelManager.class);

    // key -> channel, for remote device only
    private final Map<String, Channel> remoteChannels;
    // channel -> key
    private final Map<Channel, String> remoteKeys;

    public ChannelManager() {
        this.remoteChannels = new ConcurrentHashMap<>(16);
        this.remoteKeys = new ConcurrentHashMap<>(16);
    }

    public Channel getChannel(String key) {
        return remoteChannels.get(key);
    }

    public String getKey(Channel channel) {
        return remoteKeys.get(channel);
    }

    public void unregisterChannel(String key) {
        Channel removedCh = remoteChannels.remove(key);
        remoteKeys.remove(removedCh);
    }

    public void registerChannel(String key, Channel channel) {
        remoteChannels.putIfAbsent(key, channel);
        remoteKeys.putIfAbsent(channel, key);
    }
}
