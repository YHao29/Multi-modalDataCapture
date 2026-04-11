package com.lannooo.shell.command;

import com.lannooo.service.NettyService;
import com.lannooo.shell.ShellHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.shell.Availability;
import org.springframework.shell.AvailabilityProvider;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.CommandAvailability;
import org.springframework.shell.command.annotation.Option;

@Command(command = "server", description = "Server commands")
public class ServerCommands {

    @Autowired
    ShellHelper shellHelper;

    @Autowired
    NettyService nettyService;

    private boolean serverStarted = false;

    @Command(command = "start", description = "Start the server")
    public String startServer(
            @Option(longNames = "port",
                    shortNames = 'p',
                    defaultValue = "6666",
                    required = true) int port) {
        nettyService.startServer(port);
        serverStarted = true;
        return "Server started!";
    }

    @Command(command = "stop", description = "Stop the server")
    @CommandAvailability(provider = "serverAvailability")
    public String stopServer() {
        nettyService.stopServer();
        serverStarted = false;
        return "Server stopped!";
    }

    @Bean
    public AvailabilityProvider serverAvailability() {
        return () -> serverStarted
                ? Availability.available()
                : Availability.unavailable("Server not started");
    }
}
