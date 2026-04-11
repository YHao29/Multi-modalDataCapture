package com.lannooo.service;

import com.lannooo.common.Utils;
import com.lannooo.device.DeviceManager;
import com.lannooo.server.ServerDecoder;
import com.lannooo.server.ServerEncoder;
import com.lannooo.server.ServerHandler;
import com.lannooo.shell.ShellHelper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

@Component
public class NettyService {
    public static final Logger logger = Utils.getLogger(NettyService.class);

    @Autowired
    private DeviceManager deviceManager;

    @Autowired
    private AsyncService asyncService;

    @Autowired
    private ShellHelper shellHelper;

    private ChannelFuture future;
    private NioEventLoopGroup boss;
    private NioEventLoopGroup worker;

    public void startServer(int port) {
        Assert.notNull(deviceManager, "DeviceManager is not initialized");

        boss = new NioEventLoopGroup();
        worker = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(4096, 8, 4, 0, 0));
                        pipeline.addLast(new ServerEncoder());
                        pipeline.addLast(new ServerDecoder());
                        pipeline.addLast(new ServerHandler(asyncService, deviceManager, shellHelper));
                    }
                });
        try {
            future = b.bind(port).sync();
            logger.info("Server started on port: " + port);
        } catch (InterruptedException e) {
            logger.severe("Failed to start server" + e);
            throw new RuntimeException(e);
        }
    }

    public void stopServer() {
        try {
            if (boss != null) {
                boss.shutdownGracefully().sync();
            }
            if (worker != null) {
                worker.shutdownGracefully().sync();
            }
            if (future != null) {
                future.channel().closeFuture().sync();
            }
            logger.info("Channel closed with event loops shutdown");
        } catch (InterruptedException e) {
            logger.severe("Failed to stop server" + e);
            throw new RuntimeException(e);
        }
    }
}
