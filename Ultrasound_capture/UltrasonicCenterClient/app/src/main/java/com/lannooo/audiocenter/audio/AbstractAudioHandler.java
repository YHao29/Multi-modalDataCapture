package com.lannooo.audiocenter.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.util.Log;

import com.lannooo.audiocenter.tool.AppUtil;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.StringUtil;

public abstract class AbstractAudioHandler {
    private static final String TAG = "AudioHandler";

    protected final Context context;
    protected final ExecutorService executor;

    protected final boolean supportUnprocess;
    protected final File baseDir;
    protected AudioRecorder recorder;
    protected AudioPlayer player;
    protected boolean enableUltrasonic;

    public AbstractAudioHandler(Context context, ExecutorService executor) {
        this.context = context;
        this.executor = executor;
        AudioManager audioManager = (AudioManager) context.getSystemService(
                Context.AUDIO_SERVICE);
        String v = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
        Log.i(TAG, "Support unprocessed audio source?: " + v);
        this.supportUnprocess = !(StringUtil.isNullOrEmpty(v) || "false".equalsIgnoreCase(v));
        this.baseDir = getRecordingBaseDir();
    }

    public abstract void configureRecorder(String outputFile,
                                           int duration,
                                           boolean enableProcess,
                                           boolean isCustom,
                                           boolean enableUltrasonic,
                                           AudioEventListener listener);


    public abstract void configurePlayer(String inputFile,
                                         String type,
                                         boolean enableLoop,
                                         AudioEventListener listener);


    public abstract UploadingFileItem writeUploadingFile(ChannelHandlerContext ctx, ByteBuf buf);

    public abstract void addUploadingFile(ChannelHandlerContext ctx, Map<String, Object> data);

    public File getBaseDir() {
        return baseDir;
    }

    private File getRecordingBaseDir() {
        File dir = AppUtil.getAppRecordingDir(context);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    protected int getAudioSource(boolean enableProcess) {
        int audioSource;
        if (enableProcess) {
            audioSource = MediaRecorder.AudioSource.MIC;
        } else if (supportUnprocess) {
            audioSource = MediaRecorder.AudioSource.UNPROCESSED;
            Log.d(TAG, "Use UNPROCESSED as audio source");
        } else {
            audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
            Log.d(TAG, "Use VOICE_RECOGNITION as audio source");
        }
        return audioSource;
    }

    protected int getAudioContentType(String type) {
        int contentType;
        switch (type) {
            case "music":
                contentType = AudioAttributes.CONTENT_TYPE_MUSIC;
                break;
            case "voice":
                contentType = AudioAttributes.CONTENT_TYPE_SPEECH;
                break;
            default:
                Log.w(TAG, "Unknown audio content type: " + type);
                contentType = AudioAttributes.CONTENT_TYPE_UNKNOWN;
                break;
        }
        return contentType;
    }

    public void startRecorder() {
        if (recorder != null) {
            if (player != null && enableUltrasonic) {
                player.start();
            }
            recorder.start();
        }
    }

    public void stopRecorder() {
        if (recorder != null) {
            recorder.stop();
            if (player != null && enableUltrasonic) {
                player.stop();
            }
        }
    }

    public void pauseRecorder() {
        if (recorder != null) {
            recorder.pause();
            if (player != null && enableUltrasonic) {
                player.pause();
            }
        }
    }

    public void resumeRecorder() {
        if (recorder != null) {
            if (player != null && enableUltrasonic) {
                player.resume();
            }
            recorder.resume();
        }
    }

    public void startPlayer() {
        if (player != null) {
            player.start();
        }
    }

    public void stopPlayer() {
        if (player != null) {
            player.stop();
        }
    }

    public void pausePlayer() {
        if (player != null) {
            player.pause();
        }
    }

    public void resumePlayer() {
        if (player != null) {
            player.resume();
        }
    }
}
