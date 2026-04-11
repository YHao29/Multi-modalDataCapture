package com.lannooo.audiocenter.audio;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class SimpleAudioPlayer implements AudioPlayer {
    private static final String TAG = "SimpleAudioPlayer";

    private final ClientAudioHandler audioHandler;
    private final File audioFile;
    private final int audioType;
    private final boolean enableLoop;
    private MediaPlayer mediaPlayer;

    private final AtomicReference<PlayerStatus> status = new AtomicReference<>(PlayerStatus.INIT);
    private AudioEventListener listener;

    public SimpleAudioPlayer(ClientAudioHandler audioHandler,
                             File audioFile,
                             int audioType,
                             boolean enableLoop) {
        this.audioHandler = audioHandler;
        this.audioFile = audioFile;
        this.audioType = audioType;
        this.enableLoop = enableLoop;

        configurePlayer();

    }

    private void configurePlayer() {
        if (status.get() != PlayerStatus.INIT) {
            Log.w(TAG, "Player is already configured!");
            return;
        }

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(this.audioType) // Music or Speech may have different effect
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
        );
        mediaPlayer.setWakeMode(audioHandler.context, PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setLooping(this.enableLoop);
        if (!this.enableLoop) {
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.i(TAG, "Playback completed!");
                stop();
            });
        }
        try {
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to set data source: " + audioFile.getAbsolutePath(), e);
            throw new RuntimeException(e);
        }
        status.set(PlayerStatus.READY);
    }

    @Override
    public void start() {
        if (status.get() != PlayerStatus.READY) {
            Log.w(TAG, "Player is not ready to start!");
            return;
        }
        try {
            mediaPlayer.prepare();
            mediaPlayer.start();
            status.set(PlayerStatus.PLAYING);
        } catch (IOException e) {
            Log.e(TAG, "Failed to prepare media player!", e);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Illegal state exception!", e);
            throw e;
        }
    }

    @Override
    public void stop() {
        if (status.get() == PlayerStatus.STOPPED) {
            Log.w(TAG, "Player already stopped!");
            return;
        }

        try {
            if (status.get() == PlayerStatus.PLAYING ||
                    status.get() == PlayerStatus.PAUSED) {
                mediaPlayer.stop();
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Illegal state when stopping media player!", e);
            throw e;
        } finally {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        status.set(PlayerStatus.STOPPED);
        if (listener != null) {
            listener.onPlaybackStop();
        }
    }

    @Override
    public void pause() {
        if (status.get() != PlayerStatus.PLAYING) {
            Log.w(TAG, "Player is not playing!");
            return;
        }
        try {
            mediaPlayer.pause();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Illegal state when pausing media player!", e);
            throw e;
        }
        status.set(PlayerStatus.PAUSED);
    }

    @Override
    public void resume() {
        if (status.get() != PlayerStatus.PAUSED) {
            Log.w(TAG, "Player is not paused!");
            return;
        }
        try {
            mediaPlayer.start();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Illegal state when resuming media player!", e);
            throw e;
        }
        status.set(PlayerStatus.PLAYING);
    }

    @Override
    public void setListener(AudioEventListener listener) {
        this.listener = listener;
    }
}
