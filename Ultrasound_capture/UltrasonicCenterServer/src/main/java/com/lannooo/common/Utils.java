package com.lannooo.common;

import io.netty.channel.Channel;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Utils {
    public static void silentSleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            // ignore this
        }
    }

    public static void silentSleep(double seconds) {
        try {
            Thread.sleep((long) (seconds * 1000L));
        } catch (InterruptedException e) {
            // ignore this
        }
    }

    public static String parseAddress(Channel channel, boolean isRemote, boolean includePort) {
        SocketAddress socketAddress = isRemote ? channel.remoteAddress() : channel.localAddress();
        if (socketAddress instanceof InetSocketAddress inetAddress) {
            if (includePort) {
                return inetAddress.getHostString() + ":" + inetAddress.getPort();
            } else {
                return inetAddress.getHostString();
            }
        } else {
            return socketAddress.toString();
        }
    }


    public static String sha1Hex(String input, int length) {
        // generate a MD5 hash key
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] digest = sha.digest(input.getBytes(StandardCharsets.UTF_8));
        // convert the bytes to hex format string
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.substring(0, length);
    }

    public static String replaceLocalPath(String remotePath, String localDir, String subDir, String subSubDir) {
        Path fileName = Paths.get(remotePath).getFileName();
        List<String> subDirs = new ArrayList<>();
        if (Strings.isNotEmpty(subDir)) {
            subDirs.add(subDir);
        }
        if (Strings.isNotEmpty(subSubDir)) {
            subDirs.add(subSubDir);
        }
        subDirs.add(fileName.toString());
        return Paths.get(localDir, subDirs.toArray(new String[0])).toString();
    }

    public static Logger getLogger(Class<?> clazz) {
        return Logger.getLogger(clazz.getSimpleName());
    }

    public static void saveMap(Map<String, String> idNames, String filename) throws IOException {
        Files.writeString(Paths.get(filename),
                idNames.entrySet().stream().map(e -> e.getKey() + " " + e.getValue())
                .collect(StringBuilder::new, (sb, s) -> sb.append(s).append("\n"), StringBuilder::append)
                .toString());
    }

    public static Map<String, String> readMap(String filename) throws IOException {
        return Files.lines(Paths.get(filename))
                .map(line -> line.split(" ", 2))
                .collect(Collectors.toMap(arr -> arr[0], arr -> arr[1]));
    }
}
