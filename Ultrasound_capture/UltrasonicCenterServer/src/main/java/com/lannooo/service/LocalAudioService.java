package com.lannooo.service;

import com.lannooo.common.ArgsUtils;
import com.lannooo.common.Utils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

@Component
public class LocalAudioService {
    private static final Logger logger = Utils.getLogger(LocalAudioService.class);
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LocalAudioService.class);

    @Autowired
    private AsyncService asyncService;

    public void playAudio(File file) {
        try {
            AudioInputStream inputStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    AudioSystem.NOT_SPECIFIED, 16, 1,  2,
                    AudioSystem.NOT_SPECIFIED, false);
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.open(inputStream);
            clip.addLineListener(event -> {
                logger.info("Audio event: " + event);
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        logger.severe("Error closing audio input stream: " + file.getName());
                        throw new RuntimeException(e);
                    }
                    // restart
                    // clip.setFramePosition(0);
                    // clip.start();
                }
            });
            clip.start();
        } catch (UnsupportedAudioFileException e) {
            logger.severe("Unsupported audio file: " + file.getName());
            throw new RuntimeException(e);
        } catch (IOException e) {
            logger.severe("Error reading audio file: " + file.getName());
            throw new RuntimeException(e);
        } catch (LineUnavailableException e) {
            logger.severe("Audio Line unavailable!");
            throw new RuntimeException(e);
        }
    }

    public Mixer getMicMixer(String mixerKey) {
        if (ArgsUtils.isEmpty(mixerKey)) {
            return null;
        }
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (Mixer.Info mixerInfo : mixerInfos) {
            String name = mixerInfo.getName();
            if (name.contains(mixerKey) && !name.contains("Port")) { // match name keys
                return AudioSystem.getMixer(mixerInfo);
            }
        }
        return null;
    }

    public void captureAudio(File outputFile, int duration, Mixer mixer) {
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                44100, 16, 1, 2, 44100, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (mixer != null) {
            if (!mixer.isLineSupported(info)) {
                logger.severe("Line not supported for mixer: " + mixer.getMixerInfo().getName());
                throw new RuntimeException("Line not supported");
            }
        } else {
            if (!AudioSystem.isLineSupported(info)) {
                logger.severe("Line not supported");
                throw new RuntimeException("Line not supported");
            }
        }

        TargetDataLine line;
        try {
            if (mixer != null) {
                line = (TargetDataLine) mixer.getLine(info);
            } else {
                line = (TargetDataLine) AudioSystem.getLine(info);
            }
            line.open(format);
            AudioInputStream ais = new AudioInputStream(line);
            line.start();
            logger.info("Start capturing audio: " + outputFile.getAbsolutePath());
            asyncService.submit(() -> {
                try {
                    Thread.sleep(duration * 1000L);
                } catch (InterruptedException e) {
                    logger.severe("Error sleeping thread");
                    throw new RuntimeException(e);
                } finally {
                    line.stop();
                    line.close();
                }
            });
            int written = AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
            logger.info("Audio written: " + written);
            ais.close();
        } catch (LineUnavailableException e) {
            logger.severe("Audio Line unavailable!" + e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException e) {
            logger.severe("Error writing audio file: " + outputFile.getName());
            throw new RuntimeException(e);
        }
    }

    public double calculateAudioDuration(File file) {
        try {
            AudioInputStream inputStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = inputStream.getFormat();
            long frames = inputStream.getFrameLength();
            return (0.0 + frames) / format.getFrameRate();
        } catch (UnsupportedAudioFileException e) {
            logger.severe("Unsupported audio file: " + file.getName());
            throw new RuntimeException(e);
        } catch (IOException e) {
            logger.severe("Error reading audio file: " + file.getName());
            throw new RuntimeException(e);
        }
    }
}
