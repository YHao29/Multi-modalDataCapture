package com.lannooo.shell;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;

@Component
public class ShellHelper {
    @Value("${shell.out.info}")
    public String infoColor;

    @Value("${shell.out.success}")
    public String successColor;

    @Value("${shell.out.warning}")
    public String warningColor;

    @Value("${shell.out.error}")
    public String errorColor;

    private final Terminal terminal;

    public ShellHelper(Terminal terminal) {
        this.terminal = terminal;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public String colored(String message, PromptColor color) {
        AttributedStringBuilder builder = new AttributedStringBuilder();
        return builder.append(message, AttributedStyle.DEFAULT.foreground(color.toJlineAttributedStyle())).toAnsi();
    }

    public String info(String message) {
        return colored(message, PromptColor.valueOf(infoColor));
    }

    public String success(String message) {
        return colored(message, PromptColor.valueOf(successColor));
    }

    public String warning(String message) {
        return colored(message, PromptColor.valueOf(warningColor));
    }

    public String error(String message) {
        return colored(message, PromptColor.valueOf(errorColor));
    }

    public void print(String message) {
        print(message, null);
    }

    public void printInfo(String message) {
        print(message, PromptColor.valueOf(infoColor));
    }

    public void printSuccess(String message) {
        print(message, PromptColor.valueOf(successColor));
    }

    public void printWarning(String message) {
        print(message, PromptColor.valueOf(warningColor));
    }

    public void printError(String message) {
        print(message, PromptColor.valueOf(errorColor));
    }

    public void print(String message, PromptColor color) {
        PrintWriter writer = terminal.writer();
        writer.println();
        if (color != null) {
            writer.print(colored(message, color));
        } else {
            writer.print(message);
        }
        terminal.flush();
    }
}
