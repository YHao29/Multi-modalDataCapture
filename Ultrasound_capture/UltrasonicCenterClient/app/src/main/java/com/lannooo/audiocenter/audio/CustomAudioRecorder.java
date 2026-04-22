package com.lannooo.audiocenter.audio;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;
import android.util.Log;

import com.lannooo.audiocenter.tool.WavUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicReference;

public class CustomAudioRecorder implements AudioRecorder {
    private static final String TAG = "CustomAudioRecorder";
    private final ClientAudioHandler audioHandler;
    private final File outputFile;
    private final int audioSource;
    private final int encoding;
    private final int bitsPerSample;
    private final int sampleRate;
    private final int channels;
    private final int minBufferSize;

    private AudioRecord audioRecord;
    private final AtomicReference<RecorderStatus> status = new AtomicReference<>(RecorderStatus.INIT);
    private AudioEventListener listener;

    public CustomAudioRecorder(ClientAudioHandler audioHandler,
                               File file,
                               int audioSource,
                               int sampleRate) {
        this.audioHandler = audioHandler;
        this.outputFile = file;
        this.audioSource = audioSource;
        this.encoding = AudioFormat.ENCODING_PCM_16BIT;
        this.bitsPerSample = AudioConstants.AUDIO_BITS_PER_SAMPLE_16;
        this.sampleRate = sampleRate;
        this.channels = AudioConstants.AUDIO_DEFAULT_CHANNEL;
        this.minBufferSize = AudioRecord.getMinBufferSize(sampleRate, getChannelMask(channels), encoding);
        configureRecorder();
    }

    private int getChannelMask(int channels) {
        switch (channels) {
            case 1:
                return AudioFormat.CHANNEL_IN_MONO;
            case 2:
                return AudioFormat.CHANNEL_IN_STEREO;
            default:
                throw new IllegalArgumentException("Unsupported number of channels: " + channels);
        }
    }

    @SuppressLint("MissingPermission")
    private void configureRecorder() {
        if (status.get() != RecorderStatus.INIT) {
            Log.w(TAG, "Recorder is already configured!");
            return;
        }
        AudioRecord.Builder builder = new AudioRecord.Builder();
        builder.setAudioSource(audioSource)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(getChannelMask(channels))
                        .build())
                .setBufferSizeInBytes(minBufferSize * 2);

        if (audioHandler.context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setContext(audioHandler.context);
        }
        audioRecord = builder.build();
        status.set(RecorderStatus.READY);
    }

    @Override
    public void start() {
        if (status.get() != RecorderStatus.READY) {
            Log.w(TAG, "Recorder is already started!");
            return;
        }
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("AudioRecord is not initialized!");
        }
        audioRecord.startRecording();
        status.set(RecorderStatus.RECORDING);
        audioHandler.executor.submit(this::writeAudioData);
        if (listener != null) {
            listener.onRecordStart();
        }
    }

    private void writeAudioData() {
        byte[] buffer = new byte[minBufferSize];
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Error opening output file", e);
            fos = null;
        }
        if (fos == null) {
            return;
        }

        writeEmptyWavHeader(fos);
        while (status.get() == RecorderStatus.RECORDING || status.get() == RecorderStatus.PAUSED) {
            if (status.get() == RecorderStatus.RECORDING) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    try {
                        fos.write(buffer, 0, read);
                    } catch (IOException e) {
                        Log.e(TAG, "Error writing audio data", e);
                        stop();
                    }
                }
            } else {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Log.w(TAG, "pause waiting interrupted", e);
                }
            }
        }

        try {
            fos.flush();
            fos.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing output file", e);
        }

        writeValidWavHeader();
        if (listener != null) {
            listener.onRecordStop(outputFile);
        }
    }

    private void writeValidWavHeader() {
        long totalAudioLength = outputFile.length() - 44;
        long totalDataLength = totalAudioLength + 36;
        long byteRate = (long) sampleRate * channels * (bitsPerSample / 8);
        byte[] header = WavUtil.generateWavHeader(totalAudioLength, totalDataLength, sampleRate, channels, byteRate, bitsPerSample);
        try (RandomAccessFile wavFile = new RandomAccessFile(outputFile, "rw")) {
            wavFile.seek(0);
            wavFile.write(header);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeEmptyWavHeader(FileOutputStream fos) {
        byte[] header = new byte[44];
        try {
            fos.write(header);
            fos.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        if (status.get() != RecorderStatus.RECORDING && status.get() != RecorderStatus.PAUSED) {
            return;
        }
        status.set(RecorderStatus.STOPPED);
        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            audioRecord.stop();
        }
        audioRecord.release();
    }

    @Override
    public void pause() {
        if (status.get() != RecorderStatus.RECORDING) {
            return;
        }
        audioRecord.stop();
        status.set(RecorderStatus.PAUSED);
    }

    @Override
    public void resume() {
        if (status.get() != RecorderStatus.PAUSED) {
            return;
        }
        audioRecord.startRecording();
        status.set(RecorderStatus.RECORDING);
    }

    @Override
    public void setListener(AudioEventListener listener) {
        this.listener = listener;
    }
}
