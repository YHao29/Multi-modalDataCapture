package com.lannooo;

import com.lannooo.device.ChannelManager;
import com.lannooo.device.FileUploadManager;
import com.lannooo.device.SessionManager;
import com.lannooo.service.AsyncService;
import com.lannooo.service.RemoteAudioService;
import com.lannooo.device.DeviceManager;
import com.lannooo.service.LocalAudioService;
import com.lannooo.service.NettyService;
import com.lannooo.shell.ShellHelper;
import org.jline.terminal.Terminal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.concurrent.Executors;

@Configuration
public class SprintShellConfig {

    @Bean
    public ShellHelper shellHelper(@Lazy Terminal terminal) {
        return new ShellHelper(terminal);
    }

    @Bean
    public RemoteAudioService remoteAudioService() {
        return new RemoteAudioService();
    }

    @Bean
    public LocalAudioService localAudioService() {
        return new LocalAudioService();
    }

    @Bean
    public DeviceManager deviceManager() {
        return new DeviceManager();
    }

    @Bean
    public NettyService nettyServer() {
        return new NettyService();
    }

    @Bean
    public FileUploadManager fileUploadManager() {
        return new FileUploadManager();
    }

    @Bean
    public ChannelManager channelManager() {
        return new ChannelManager();
    }

    @Bean
    public AsyncService asyncService() {
        return new AsyncService(
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2)
        );
    }

    @Bean
    public SessionManager sessionManager() {
        return new SessionManager();
    }
}
