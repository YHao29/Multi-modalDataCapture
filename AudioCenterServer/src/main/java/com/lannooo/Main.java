package com.lannooo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.shell.command.annotation.EnableCommand;

import com.lannooo.service.NettyService;
import com.lannooo.shell.command.AudioCommands;
import com.lannooo.shell.command.DebugCommands;
import com.lannooo.shell.command.DeviceCommands;
import com.lannooo.shell.command.ServerCommands;
import com.lannooo.shell.command.SessionCommands;
import com.lannooo.sync.SNTPServer;

@SpringBootApplication
@EnableCommand({ServerCommands.class, DeviceCommands.class,
        AudioCommands.class, SessionCommands.class, DebugCommands.class})
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    /**
     * 自动启动 Netty 服务器（用于设备连接）和 SNTP 服务器（用于时间同步）
     * 启动后可通过命令行 server start/stop 进行控制
     */
    @Bean
    public CommandLineRunner autoStartServers(NettyService nettyService, SNTPServer sntpServer) {
        return args -> {
            System.out.println("========================================");
            
            // 启动 Netty 服务器
            try {
                System.out.println("Auto-starting Netty server on port 6666...");
                nettyService.startServer(6666);
                System.out.println("Netty server started successfully!");
            } catch (Exception e) {
                System.err.println("Failed to auto-start Netty server: " + e.getMessage());
                System.err.println("You can manually start it using: server start");
            }
            
            // 启动 SNTP 服务器
            try {
                System.out.println("Auto-starting SNTP server on UDP port 1123...");
                sntpServer.start();
                System.out.println("SNTP server started successfully!");
            } catch (Exception e) {
                System.err.println("Failed to auto-start SNTP server: " + e.getMessage());
                System.err.println("Note: Time synchronization may not work properly");
            }
            
            System.out.println("REST API available at http://localhost:8080/api");
            System.out.println("========================================");
        };
    }
}