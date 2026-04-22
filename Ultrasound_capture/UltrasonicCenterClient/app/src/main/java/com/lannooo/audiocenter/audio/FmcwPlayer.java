package com.lannooo.audiocenter.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import java.util.concurrent.atomic.AtomicReference;

public class FmcwPlayer implements AudioPlayer {
    public static final String TAG = "FmcwPlayer";

    private final UltrasonicConfig config;
    private final double durationSeconds;
    private AudioTrack audioTrack;
    private final AtomicReference<PlayerStatus> status = new AtomicReference<>(PlayerStatus.INIT);
    private AudioEventListener listener;

    public FmcwPlayer(UltrasonicConfig config, double durationSeconds) {
        this.config = config;
        this.durationSeconds = durationSeconds;
        configurePlayer();
    }

    private void configurePlayer() {
        short[] samples = buildFmcwSignal();
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(config.getSampleRateHz())
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(samples.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        audioTrack.write(samples, 0, samples.length);
        status.set(PlayerStatus.READY);
    }

    private short[] buildFmcwSignal() {
        int totalSamples = Math.max(1, (int) Math.round(config.getSampleRateHz() * durationSeconds));
        int chirpSamples = Math.max(1, (int) Math.round(config.getSampleRateHz() * config.getChirpDurationMs() / 1000.0));
        int idleSamples = Math.max(0, (int) Math.round(config.getSampleRateHz() * config.getIdleDurationMs() / 1000.0));
        int periodSamples = Math.max(chirpSamples + idleSamples, 1);
        short[] data = new short[totalSamples];
        int index = 0;
        while (index < totalSamples) {
            int usableChirpSamples = Math.min(chirpSamples, totalSamples - index);
            fillChirp(data, index, usableChirpSamples);
            index += usableChirpSamples;
            int usableIdleSamples = Math.min(idleSamples, totalSamples - index);
            index += usableIdleSamples;
            if (!config.isRepeat()) {
                break;
            }
            if (periodSamples <= 0) {
                break;
            }
        }
        return data;
    }

    private void fillChirp(short[] data, int offset, int chirpSamples) {
        double sampleRate = config.getSampleRateHz();
        double chirpSeconds = chirpSamples / sampleRate;
        double slope = (config.getEndFreqHz() - config.getStartFreqHz()) / Math.max(chirpSeconds, 1e-6);
        for (int i = 0; i < chirpSamples; i++) {
            double t = i / sampleRate;
            double phase = 2.0 * Math.PI * (config.getStartFreqHz() * t + 0.5 * slope * t * t);
            double windowGain = windowGain(i, chirpSamples);
            double v = Math.sin(phase) * config.getAmplitude() * windowGain;
            data[offset + i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, (int) Math.round(v * Short.MAX_VALUE)));
        }
    }

    private double windowGain(int index, int total) {
        if (!"hann".equalsIgnoreCase(config.getWindowType()) || total <= 1) {
            return 1.0;
        }
        return 0.5 * (1.0 - Math.cos((2.0 * Math.PI * index) / (total - 1)));
    }

    @Override
    public void start() {
        if (status.get() != PlayerStatus.READY) {
            Log.w(TAG, "FMCW Player is not ready to start");
            return;
        }
        audioTrack.play();
        status.set(PlayerStatus.PLAYING);
    }

    @Override
    public void stop() {
        if (audioTrack == null) {
            return;
        }
        try {
            if (status.get() == PlayerStatus.PLAYING || status.get() == PlayerStatus.PAUSED) {
                audioTrack.stop();
            }
        } finally {
            audioTrack.release();
            audioTrack = null;
            status.set(PlayerStatus.STOPPED);
            if (listener != null) {
                listener.onPlaybackStop();
            }
        }
    }

    @Override
    public void pause() {
        if (audioTrack != null && status.get() == PlayerStatus.PLAYING) {
            audioTrack.pause();
            status.set(PlayerStatus.PAUSED);
        }
    }

    @Override
    public void resume() {
        if (audioTrack != null && status.get() == PlayerStatus.PAUSED) {
            audioTrack.play();
            status.set(PlayerStatus.PLAYING);
        }
    }

    @Override
    public void setListener(AudioEventListener listener) {
        this.listener = listener;
    }
}
