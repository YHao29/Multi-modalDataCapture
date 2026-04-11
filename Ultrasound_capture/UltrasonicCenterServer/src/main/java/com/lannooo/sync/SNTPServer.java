package com.lannooo.sync;

import com.lannooo.common.Utils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * SNTP 服务器实现
 * 用于提供时间同步服务给客户端设备
 */
@Component
public class SNTPServer {
    private static final Logger logger = Utils.getLogger(SNTPServer.class);
    
    private static final int DEFAULT_PORT = 1123;  // 使用非特权端口
    private Channel channel;
    private EventLoopGroup group;
    private boolean running = false;

    /**
     * 启动 SNTP 服务器
     */
    public void start() throws Exception {
        start(DEFAULT_PORT);
    }

    /**
     * 启动 SNTP 服务器
     * @param port 监听端口
     */
    public void start(int port) throws Exception {
        if (running) {
            logger.warning("SNTP Server is already running");
            return;
        }

        group = new NioEventLoopGroup();
        
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel ch) {
                            ch.pipeline().addLast(new SNTPServerHandler());
                        }
                    });

            channel = bootstrap.bind(port).sync().channel();
            running = true;
            
            logger.info("SNTP Server started on UDP port: " + port);
            
        } catch (Exception e) {
            logger.severe("Failed to start SNTP Server: " + e.getMessage());
            if (group != null) {
                group.shutdownGracefully();
            }
            throw e;
        }
    }

    /**
     * 停止 SNTP 服务器
     */
    public void stop() {
        if (!running) {
            return;
        }

        try {
            if (channel != null) {
                channel.close().sync();
            }
            if (group != null) {
                group.shutdownGracefully().sync();
            }
            running = false;
            logger.info("SNTP Server stopped");
        } catch (Exception e) {
            logger.severe("Error stopping SNTP Server: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * SNTP 数据包处理器
     */
    private static class SNTPServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            ByteBuf request = packet.content();
            
            // 验证 NTP 请求格式（至少48字节）
            if (request.readableBytes() < 48) {
                logger.warning("Received invalid SNTP request");
                return;
            }

            // 获取接收时间戳
            long receiveTimestamp = System.currentTimeMillis();
            
            // 创建响应包
            ByteBuf response = ctx.alloc().buffer(48);
            
            // NTP 头部（字节 0）
            // LI=0, VN=4, Mode=4 (Server)
            response.writeByte(0x24);
            
            // Stratum (字节 1) - 设置为2（二级时间源）
            response.writeByte(0x02);
            
            // Poll Interval (字节 2)
            response.writeByte(0x06);
            
            // Precision (字节 3)
            response.writeByte(0xEC);
            
            // Root Delay (字节 4-7) - 0
            response.writeInt(0);
            
            // Root Dispersion (字节 8-11) - 0
            response.writeInt(0);
            
            // Reference ID (字节 12-15) - "LOCL"
            response.writeBytes("LOCL".getBytes());
            
            // Reference Timestamp (字节 16-23) - 当前时间
            writeNtpTimestamp(response, receiveTimestamp);
            
            // Originate Timestamp (字节 24-31) - 从请求复制
            request.readerIndex(40);
            response.writeBytes(request, 8);
            
            // Receive Timestamp (字节 32-39) - 服务器接收时间
            writeNtpTimestamp(response, receiveTimestamp);
            
            // Transmit Timestamp (字节 40-47) - 服务器发送时间
            long transmitTimestamp = System.currentTimeMillis();
            writeNtpTimestamp(response, transmitTimestamp);
            
            // 发送响应
            InetSocketAddress sender = packet.sender();
            ctx.writeAndFlush(new DatagramPacket(response, sender));
            
            logger.fine("Sent SNTP response to " + sender);
        }

        /**
         * 将 Java 时间戳转换为 NTP 时间戳并写入 ByteBuf
         * NTP 时间戳：从 1900-01-01 00:00:00 开始的秒数和小数部分
         */
        private void writeNtpTimestamp(ByteBuf buf, long javaTimestamp) {
            // Java 时间戳是从 1970-01-01 开始，NTP 从 1900-01-01 开始
            // 差值：2208988800 秒
            long ntpSeconds = (javaTimestamp / 1000) + 2208988800L;
            long ntpFraction = ((javaTimestamp % 1000) * 0x100000000L) / 1000;
            
            buf.writeInt((int) ntpSeconds);
            buf.writeInt((int) ntpFraction);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.warning("SNTP Server error: " + cause.getMessage());
        }
    }
}
