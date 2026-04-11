package com.lannooo.audiocenter.tool;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import io.netty.channel.Channel;

public class AppUtil {
    private static final String TAG = "AppUtil";
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    // Checks if a volume containing external storage is available
    // for read and write.
    public static boolean isExternalStorageWritable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    // Checks if a volume containing external storage is available to at least read.
    public static boolean isExternalStorageReadable() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ||
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY);
    }

    public static String parseAddress(Channel channel, boolean isRemote, boolean includePort) {
        SocketAddress socketAddress = isRemote ? channel.remoteAddress() : channel.localAddress();
        if (socketAddress instanceof InetSocketAddress) {
            InetSocketAddress inetAddress = (InetSocketAddress) socketAddress;
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

    public static File getAppRecordingDir(Context context) {
        File dir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS);
        } else {
            dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        }
        if (dir == null) {
            if (isExternalStorageWritable()) {
                dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath(),
                        "Recordings");
                Log.i(TAG, "External storage is writable, use " +
                        dir.getAbsolutePath() + " as recording directory");
            } else {
                dir = context.getFilesDir();
                Log.w(TAG, "External storage is not writable, use internal storage instead");
            }
        } else {
            Log.i(TAG, "Use " + dir.getAbsolutePath() + " as recording directory");
        }
        return dir;
    }

    public static String currentDateTime() {
        return timeFormatter.format(Instant.now());
    }
}
