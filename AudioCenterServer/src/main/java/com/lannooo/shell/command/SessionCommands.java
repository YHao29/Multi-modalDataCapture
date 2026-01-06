package com.lannooo.shell.command;

import com.lannooo.device.SessionManager;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;

@Component
@Command(command = "session", description = "Experiment session setup")
public class SessionCommands {

    @Autowired
    SessionManager sessionManager;

    @Command(command = "create", description = "create a new session")
    public String createSession(
            @Option(longNames = "key", shortNames = 'k', required = true) String sessionKey
    ) {
        if (Strings.isEmpty(sessionKey)) {
            return "Invalid session key";
        }

        sessionManager.create(sessionKey);
        return "Session created";
    }

    @Command(command = "close", description = "clear current session setup")
    public String clearSession() {
        sessionManager.close();
        return "Session cleared";
    }

    @Command(command = "list", description = "show current session setup")
    public String showSessionSetup() {
        return "Session: " + sessionManager.getExpKey();
    }
}
