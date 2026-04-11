package com.lannooo.audiocenter.audio;

import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class SimpleAudioRecorder implements AudioRecorder {
    private static final String TAG = "SimpleAudioRecorder";

    private final ClientAudioHandler audioHandler;
    private final File outputFile;
    private final int audioSource;
    private final int durationMs;
    private final int bitRate;
    private final int sampleRate;
    private final int channels;
    private final long maxFileSize;
    private MediaRecorder mediaRecorder;

    // TODO: The concurrent access will be guaranteed in the future
    //  Since we only have one instance of Client Handler (Thread) accessing
    //  this class, we use solely an atomic reference to track the status
    private final AtomicReference<RecorderStatus> status = new AtomicReference<>(RecorderStatus.INIT);
    private AudioEventListener listener;

    public SimpleAudioRecorder(ClientAudioHandler audioHandler,
                               File file,
                               int audioSource,
                               int durationMs) {
        this.audioHandler = audioHandler;
        this.outputFile = file;
        this.audioSource = audioSource;
        this.durationMs = durationMs;
        this.bitRate = AudioConstants.AUDIO_DEFAULT_ENCODING_BIT_RATE;
        this.sampleRate = AudioConstants.AUDIO_DEFAULT_SAMPLE_RATE;
        this.channels = AudioConstants.AUDIO_DEFAULT_CHANNEL;
        this.maxFileSize = AudioConstants.AUDIO_DEFAULT_MAX_FILE_SIZE;

        configureRecorder();
    }

    private void configureRecorder() {
        if (status.get() != RecorderStatus.INIT) {
            Log.w(TAG, "Recorder is already configured!");
            return;
        }

        // check permission of audio recording

        if (audioHandler.context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mediaRecorder = new MediaRecorder(audioHandler.context);
        } else {
            mediaRecorder = new MediaRecorder();
        }
        mediaRecorder.setAudioSource(audioSource);
        // Possible Audio Encoder:
        // - AMR_WB (50Hz ~ 7kHz)
        // - AMR_NB (bandwidth only around 3kHz)
        // - AAC (~8kHz to ~96kHz, but lossy) --> we choose this one
        // AAC Encoder and MPEG-4 format result in .m4a audio files
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioEncodingBitRate(bitRate);
        mediaRecorder.setAudioSamplingRate(sampleRate);
        mediaRecorder.setAudioChannels(channels);
        mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
        mediaRecorder.setMaxDuration(durationMs);
        mediaRecorder.setMaxFileSize(maxFileSize);
        if (durationMs != -1) {
            mediaRecorder.setOnInfoListener((mr, what, extra) -> {
                // if the maximum duration is reached, stop the recording by self
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    Log.i(TAG, "MediaRecorder Maximum duration reached");
                    stop();
                } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    Log.i(TAG, "MediaRecorder Maximum file size reached");
                    // TODO: may be we can handle this more gracefully
                    stop();
                } else {
                    Log.e(TAG, "Unknown MediaRecorder Info: " + what);
                }
            });
        }
        status.set(RecorderStatus.READY);
    }

    @Override
    public void start() {
        if (status.get() != RecorderStatus.READY) {
            Log.w(TAG, "Recorder is already started!");
            return;
        }
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            status.set(RecorderStatus.RECORDING);
        } catch (IOException e) {
            Log.e(TAG, "Error starting MediaRecorder", e);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Illegal state when starting MediaRecorder", e);
            throw e;
        }
    }

    @Override
    public void stop() {
        if (status.get() == RecorderStatus.STOPPED) {
            Log.w(TAG, "Recorder already stopped!");
            return;
        }

        try {
            if (status.get() == RecorderStatus.RECORDING ||
                    status.get() == RecorderStatus.PAUSED) {
                mediaRecorder.stop();
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Illegal state when stopping MediaRecorder", e);
            throw e;
        } finally {
            mediaRecorder.release();
            mediaRecorder = null;
        }
        status.set(RecorderStatus.STOPPED);
        if (listener != null) {
            String path = outputFile.getAbsolutePath();
            Log.i(TAG, "Recording stopped, output file: " + path);
            listener.onRecordStop(new File(path));
        }
    }

    @Override
    public void pause() {
        if (status.get() != RecorderStatus.RECORDING) {
            Log.w(TAG, "Recorder is not recording!");
            return;
        }
        try {
            // this API is available since SDK 24 (Android 7.0)
            mediaRecorder.pause();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Illegal state when pausing MediaRecorder", e);
            throw e;
        }
        status.set(RecorderStatus.PAUSED);
    }

    @Override
    public void resume() {
        if (status.get() != RecorderStatus.PAUSED) {
            Log.w(TAG, "Recorder is not paused!");
            return;
        }
        try {
            // this API is available since SDK 24 (Android 7.0)
            mediaRecorder.resume();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Illegal state when resuming MediaRecorder", e);
            throw e;
        }
        status.set(RecorderStatus.RECORDING);
    }

    @Override
    public void setListener(AudioEventListener listener) {
        this.listener = listener;
    }
}
