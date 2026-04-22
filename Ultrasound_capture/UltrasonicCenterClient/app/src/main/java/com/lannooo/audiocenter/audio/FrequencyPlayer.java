package com.lannooo.audiocenter.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class FrequencyPlayer implements AudioPlayer {
    public static final String TAG = "FrequencyPlayer";

    private static volatile short[] SAMPLE_CACHE;

    private final ClientAudioHandler audioHandler;
    private final double frequency;
    private final double duration;
    private final RoutePresetManager.PreparedRoute preparedRoute;
    private AudioTrack audioTrack;

    private final AtomicReference<PlayerStatus> status = new AtomicReference<>(PlayerStatus.INIT);
    private AudioEventListener listener;

    public FrequencyPlayer(ClientAudioHandler audioHandler,
                           double frequency,
                           double duration,
                           RoutePresetManager.PreparedRoute preparedRoute) {
        this.audioHandler = audioHandler;
        this.frequency = frequency;
        this.duration = duration;
        this.preparedRoute = preparedRoute;

        configurePlayer();
    }

    private void configurePlayer() {
        if (status.get() != PlayerStatus.INIT) {
            Log.w(TAG, "Frequency Player is already configured!");
            return;
        }
//        int bufferSize = AudioTrack.getMinBufferSize(
//                AudioConstants.AUDIO_SAMPLE_RATE_44100,
//                AudioFormat.CHANNEL_OUT_MONO,
//                AudioFormat.ENCODING_PCM_16BIT);
        short[] samples = getTone(this.frequency, this.duration);

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(AudioConstants.AUDIO_SAMPLE_RATE_44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(samples.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        if (preparedRoute != null && preparedRoute.getOutputDevice() != null && !audioTrack.setPreferredDevice(preparedRoute.getOutputDevice())) {
            throw new IllegalStateException("Failed to bind preferred frequency output device.");
        }
        audioTrack.write(samples, 0, samples.length);
        status.set(PlayerStatus.READY);
    }

    private short[] getTone(double frequency, double duration) {
        int n = (int) (AudioConstants.AUDIO_SAMPLE_RATE_44100 * duration);
        if (SAMPLE_CACHE != null && SAMPLE_CACHE.length > n) {
            // copy and return a sub-array of the sample cache from 0 to new length
            return Arrays.copyOf(SAMPLE_CACHE, n+1);
        }
        // generate new samples and cache it
        short[] a = new short[n+1];
        for (int i = 0; i <= n; i++) {
            double v = Math.sin(2 * Math.PI * i * frequency / AudioConstants.AUDIO_SAMPLE_RATE_44100);
            a[i] = (short) (v * Short.MAX_VALUE);
        }
        SAMPLE_CACHE = a;
        return a;
    }

    @Override
    public void start() {
        if (status.get() != PlayerStatus.READY) {
            Log.w(TAG, "Frequency Player is not ready to start!");
            return;
        }
        try {
            audioTrack.play();
            status.set(PlayerStatus.PLAYING);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to start Frequency Player!", e);
            throw e;
        }
    }

    @Override
    public void stop() {
        if (status.get() == PlayerStatus.STOPPED) {
            Log.w(TAG, "Frequency Player is already stopped!");
            return;
        }
        try {
            if (status.get() == PlayerStatus.PLAYING || status.get() == PlayerStatus.PAUSED) {
                audioTrack.stop();
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to stop Frequency Player!", e);
            throw e;
        } finally {
            audioTrack.release();
            audioTrack = null;
        }
        status.set(PlayerStatus.STOPPED);
        if (listener != null) {
            listener.onPlaybackStop();
        }
    }

    @Override
    public void pause() {
        if (status.get() != PlayerStatus.PLAYING) {
            Log.w(TAG, "Frequency Player is not playing!");
            return;
        }
        try {
            audioTrack.pause();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to pause Frequency Player!", e);
            throw e;
        }
        status.set(PlayerStatus.PAUSED);
    }

    @Override
    public void resume() {
        if (status.get() != PlayerStatus.PAUSED) {
            Log.w(TAG, "Frequency Player is not paused!");
            return;
        }
        try {
            audioTrack.play();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to resume Frequency Player!", e);
            throw e;
        }
        status.set(PlayerStatus.PLAYING);
    }

    @Override
    public void setListener(AudioEventListener listener) {
        this.listener = listener;
    }
}
