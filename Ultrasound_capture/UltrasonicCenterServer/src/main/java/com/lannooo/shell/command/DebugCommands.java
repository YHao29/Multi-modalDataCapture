package com.lannooo.shell.command;

import com.lannooo.service.RemoteAudioService;
import com.lannooo.device.DeviceManager;
import com.lannooo.shell.ShellHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

import java.io.*;
import java.util.Properties;


@Command(command = "test", description = "for test usage")
public class DebugCommands {
    @Autowired
    ShellHelper shellHelper;

    @Autowired
    DeviceManager deviceManager;

    @Autowired
    RemoteAudioService remoteAudioService;

    @Command(command = "xxx", description = "test")
    public String xxx(
            @Option(longNames = "longname",
                    shortNames = 's',
                    required = true,
                    defaultValue = "abc",
                    description = "a suffix") String arg1,
            @Option(arity = CommandRegistration.OptionArity.ONE_OR_MORE) String names) {
        System.out.println(names);
        System.out.println(names.getClass());
        return "xxx" + arg1;
    }

    @Command(command = "print", description = "test print in console")
    public String print() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                shellHelper.printSuccess("Success message");
                shellHelper.print("test test");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
        return "in test";
    }

    @Command(command = "di", description = "test DI")
    public String testDI() {
        return "deviceManager: " + deviceManager + "\naudioDeviceService: " + remoteAudioService;
    }

    @Command(command = "resource")
    public String testResource() {
        Properties properties = new Properties();
        try (InputStream inputStream = new FileInputStream("audio/audio.properties")) {
            // load properties
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties.toString();
    }
}
