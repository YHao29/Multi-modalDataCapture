package com.lannooo.shell.command;

import com.lannooo.common.AppConstants;
import com.lannooo.common.ArgsUtils;
import com.lannooo.common.Utils;
import com.lannooo.device.DeviceManager;
import com.lannooo.device.FileUploadListener;
import com.lannooo.device.SessionManager;
import com.lannooo.service.AsyncService;
import com.lannooo.service.LocalAudioService;
import com.lannooo.service.RemoteAudioService;
import com.lannooo.shell.ShellHelper;
import io.micrometer.common.util.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;

import javax.sound.sampled.Mixer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component
@Command(command = "audio", description = "Audio commands")
public class AudioCommands {

    @Autowired
    ShellHelper shellHelper;

    @Autowired
    RemoteAudioService remoteAudioService;

    @Autowired
    LocalAudioService localAudioService;

    @Autowired
    AsyncService asyncService;

    @Autowired
    DeviceManager deviceManager;

    @Autowired
    SessionManager sessionManager;

    @Command(command = "remote-list", description = "List audio files")
    public void listRemoteFiles(
            @Option(longNames = "device", shortNames = 'd', required = true) String key) {
        // check if device exists
        if (!deviceManager.isRegistered(key)) {
            System.out.println("Device not found: " + key);
            return;
        }
        // send command
        asyncService.submit(() -> remoteAudioService.listFiles(key));
    }

    @Command(command = "remote-delete", description = "Delete audio file")
    public void deleteRemoteFiles(
            @Option(longNames = "device", shortNames = 'd', required = true) String key,
            @Option(longNames = "path", shortNames = 'p', required = true) String path) {
        // check if device exists
        if (!deviceManager.isRegistered(key)) {
            shellHelper.printError("Device not found: " + key);
            return;
        }
        // send command
        asyncService.submit(() -> remoteAudioService.deleteFile(key, path));
    }

    @Command(command = "remote-upload", description = "Upload audio file to remote client")
    public void uploadFile(@Option(longNames = "device", shortNames = 'd', required = true) String key,
                           @Option(longNames = "path", shortNames = 'p', required = true) String path) {
        File filePath = new File(path);
        if (!filePath.exists()) {
            shellHelper.printError("File not found: " + path);
            return;
        }

        if (!deviceManager.isRegistered(key)) {
            shellHelper.printError("Device not found: " + key);
            return;
        }

        Set<String> uploadTargetKeys;
        if ("ALL".equals(key)) {
            if (deviceManager.getDevices().isEmpty()) {
                shellHelper.printError("No devices found");
                return;
            }
            uploadTargetKeys = deviceManager.getDeviceKeys();
        } else {
            if (!deviceManager.isRegistered(key)) {
                shellHelper.printError("Device not found: " + key);
                return;
            }
            uploadTargetKeys = Set.of(key);
        }

        List<File> filesToUpload = new ArrayList<>();
        if (filePath.isDirectory()) {
            for (File file : Objects.requireNonNull(filePath.listFiles())) {
                if (file.isFile()) {
                    filesToUpload.add(file);
                }
            }
        } else {
            filesToUpload.add(filePath);
        }

        shellHelper.printInfo("Uploading " + filesToUpload.size() + " files to " + uploadTargetKeys.size() + " devices");

        for (String k : uploadTargetKeys) {
            FileUploadListener listener = new FileUploadListener() {
                @Override
                public void onSuccess(String message) {
                    shellHelper.printSuccess(k + ": " + message);
                }
            };
            asyncService.submit(() -> {
                for (File file : filesToUpload) {
                    remoteAudioService.uploadFile(k, file, listener);
                    Utils.silentSleep(0.1);
                }
            });
        }
    }

    @Command(command = "remote-play", description = "Play audio")
    public void playRemoteAudio(@Option(longNames = "device", shortNames = 'd', required = true) String key,
                                @Option(longNames = "input", shortNames = 'i', required = true) String inputPath,
                                @Option(longNames = "action", shortNames = 'a', defaultValue = "start") String action,
                                @Option(longNames = "mode", shortNames = 'm', defaultValue = "music") String mode,
                                @Option(longNames = "loop", shortNames = 'l', defaultValue = "false") boolean enableLoop) {
        if (!ArgsUtils.checkOptionIn(action, AppConstants.PLAYBACK_VALID_ACTIONS)) {
            shellHelper.printError("Invalid action: " + action);
            return;
        }

        if (!ArgsUtils.checkOptionIn(mode, AppConstants.PLAYBACK_VALID_MODES)) {
            shellHelper.printError("Invalid mode: " + mode);
            return;
        }

        if ("start".equalsIgnoreCase(action) && !ArgsUtils.checkFilenameValid(inputPath)) {
            shellHelper.printError("Invalid path: " + inputPath);
            return;
        }

        // TODO: multiple device playback may be not needed currently
        if (!deviceManager.isRegistered(key)) {
            shellHelper.printError("Device not found: " + key);
            return;
        }
        if (!deviceManager.isPlaybackEnabled(key)) {
            shellHelper.printError("Playback is disabled for device: " + key);
            return;
        }
        asyncService.submit(() -> remoteAudioService.playAudio(key, action, mode, enableLoop, inputPath));
    }


    @Command(command = "remote-capture", description = "Capture audio in the remote clients")
    public void captureRemoteAudio(
            @Option(longNames = "device", shortNames = 'd', required = true, defaultValue = "ALL") String key,
            @Option(longNames = "action", shortNames = 'a', defaultValue = "start") String action,
            @Option(longNames = "output", shortNames = 'o', defaultValue = "output.wav") String output,
            @Option(longNames = "duration", shortNames = 't', defaultValue = "-1") int duration,
            @Option(longNames = "mode", shortNames = 'm', defaultValue = "pro") String mode,
            @Option(longNames = "process", shortNames = 'p', defaultValue = "false") boolean process,
            @Option(longNames = "forward", shortNames = 'f', defaultValue = "false") boolean forward,
            @Option(longNames = "delete", shortNames = 'x', defaultValue = "false") boolean postDelete,
            @Option(longNames = "ultrasonic", shortNames = 'u', defaultValue = "false") boolean ultrasonic) {
        // check args are valid
        if (!ArgsUtils.checkOptionIn(mode, AppConstants.CAPTURE_VALID_MODES)) {
            shellHelper.printError("Invalid mode: " + mode);
            return;
        }
        if (!ArgsUtils.checkOptionIn(action, AppConstants.CAPTURE_VALID_ACTIONS)) {
            shellHelper.printError("Invalid action: " + action);
            return;
        }
        if (!ArgsUtils.checkFilenameValid(output)) {
            shellHelper.printError("Invalid output file name: " + output);
            return;
        }
        if (!ArgsUtils.checkValidDuration(duration)) {
            shellHelper.printError("Invalid duration: " + duration);
            return;
        }
        if ("ALL".equals(key)) {
            if (deviceManager.getDevices().isEmpty()) {
                shellHelper.printError("No devices found");
                return;
            }
            // parallel stream or ordered stream?
            deviceManager.getCaptureKeys().forEach(k ->
                    asyncService.submit(() ->
                            remoteAudioService.captureAudio(k, action, mode, output, duration,
                                    process, forward, postDelete, ultrasonic)
                    )
            );
        } else {
            // check if device exists
            if (!deviceManager.isRegistered(key)) {
                shellHelper.printError("Device not found: " + key);
                return;
            }
            if (!deviceManager.isCaptureEnabled(key)) {
                shellHelper.printError("Capture is disabled for device: " + key);
                return;
            }
            // send command
            asyncService.submit(() ->
                remoteAudioService.captureAudio(key, action, mode, output, duration,
                        process, forward, postDelete, ultrasonic)
            );
        }

    }

    @Command(command = "local-play", description = "Play audio with local speaker")
    public void playLocalAudio(@Option(longNames = "input", shortNames = 'i', required = true) String inputFile) {
        File file = new File(inputFile);
        if (!file.exists()) {
            shellHelper.printError("File not found: " + inputFile);
        } else {
            asyncService.submit(() -> localAudioService.playAudio(file));
        }
    }


    @Command(command = "local-capture", description = "Capture audio with local microphone")
    public void captureLocalAudio(@Option(longNames = "output", shortNames = 'o', required = true) String outputFile,
                                  @Option(longNames = "duration", shortNames = 't', required = true) int duration,
                                  @Option(longNames = "saveDir", shortNames = 'd', defaultValue = "local") String saveDir,
                                  @Option(longNames = "mixer", shortNames = 'k', defaultValue = "") String mixerKey) {
        if (!ArgsUtils.checkFilenameValid(outputFile)) {
            shellHelper.printError("Invalid output file name: " + outputFile);
            return;
        }
        // save to the session directory if exists
        String expKey = sessionManager.getExpKey();
        Path baseDir = Paths.get(AppConstants.AUDIO_BASE_PATH);
        if (StringUtils.isNotEmpty(saveDir)) {
            baseDir = baseDir.resolve(saveDir);
        }
        if (StringUtils.isNotEmpty(expKey)) {
            baseDir = baseDir.resolve(expKey);
        }
        if (!Files.exists(baseDir)) {
            try {
                Files.createDirectories(baseDir);
            } catch (IOException e) {
                shellHelper.printError("Failed to create directory: " + baseDir);
                throw new RuntimeException(e);
            }
        }
        File output = baseDir.resolve(outputFile).toFile();
        Mixer mixer = localAudioService.getMicMixer(mixerKey);
        if (mixer == null) {
            shellHelper.printError("Mixer not found: " + mixerKey);
        } else {
            shellHelper.printInfo("Mixer info: " + mixer.getMixerInfo().toString());
        }
        asyncService.submit(() -> localAudioService.captureAudio(output, duration, mixer));
    }


    @Command(command = "play-record", description = "Play audio and record in one command")
    public void playAudioAndRecordSchedule(
            @Option(longNames = "session", shortNames = 's', required = true) String session,
            @Option(longNames = "input", shortNames = 'i', required = true) String inputPath,
            @Option(longNames = "speaker", shortNames = 'k', defaultValue = "LOCAL") String spk,   // LOCAL, <REMOTE-key>
            @Option(longNames = "microphone", shortNames = 'm', defaultValue = "ALL") String mic,  // ALL, REMOTE, LOCAL
            @Option(longNames = "output", shortNames = 'o') String outputFile,
            @Option(longNames = "process", shortNames = 'p', defaultValue = "false") boolean process,
            @Option(longNames = "ultrasonic", shortNames = 'u', defaultValue = "false") boolean ultrasonic,
            @Option(longNames = "delay", shortNames = 'd', defaultValue = "0") int delay) {
        // All the args should be read from resources/audio.properties
        Properties properties = new Properties();
        try (InputStream inputStream = new FileInputStream("audio/audio.properties")) {
            // load properties
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
//        String playDeviceKey = properties.getProperty("playback.device", "LOCAL");
//        String recordDeviceKey = properties.getProperty("record.device", "ALL");
        String recordMode = properties.getProperty("record.mode", "pro");
//        int recordDuration = Integer.parseInt(properties.getProperty("record.duration", "-1"));
        boolean recordProcess = Boolean.parseBoolean(properties.getProperty("record.process", "false"));
        boolean recordForward = Boolean.parseBoolean(properties.getProperty("record.forward", "true"));
        boolean recordDelete = Boolean.parseBoolean(properties.getProperty("record.delete", "true"));
        String localDevices = properties.getProperty("record.devices", null);

        File localAudio = new File(inputPath);
        if (!localAudio.exists()) {
            shellHelper.printError("Audio file not found: " + inputPath);
            return;
        }
        File[] audioFiles;
        if (localAudio.isDirectory()) {
            audioFiles = Optional.ofNullable(localAudio.listFiles()).orElseGet(() -> new File[0]);
        } else {
            audioFiles = new File[]{localAudio};
        }

        boolean captureLocal = "LOCAL".equalsIgnoreCase(mic) || "ALL".equalsIgnoreCase(mic);
        boolean captureRemote = "REMOTE".equalsIgnoreCase(mic) || "ALL".equalsIgnoreCase(mic);

        Map<String, String> mixerDirKeys = new HashMap<>();
        if (captureLocal) {
            if (Strings.isEmpty(localDevices)) {
                shellHelper.printError("No local devices found");
                return;
            }
            String[] mixerConfigs = localDevices.split(",");
            for (String mc: mixerConfigs) {
                String[] splits = mc.split(":");
                mixerDirKeys.put(splits[0], splits[1]);  // local dir -> mixer name key
            }
        }

        asyncService.submit(() -> {
            sessionManager.create(session);
            if (delay > 0) {
                Utils.silentSleep(delay);
            }
            int cur = 0;
            int total = audioFiles.length;
            for (File audioFile : audioFiles) {
                cur++;
                double duration = localAudioService.calculateAudioDuration(audioFile);
                // duration to integer seconds
                int recordDurationSeconds = (int) Math.ceil(duration);
                String forwardAudioFile;
                if (Strings.isEmpty(outputFile)) {
                    forwardAudioFile = audioFile.getName();
                } else {
                    forwardAudioFile = outputFile;
                }
                if (captureRemote) {
                    this.captureRemoteAudio("ALL", "start", forwardAudioFile,
                            recordDurationSeconds, recordMode, process, recordForward, recordDelete, ultrasonic);
                }
                if (captureLocal) {
                    mixerDirKeys.forEach((localMicDir, localMicMixer) ->
                        this.captureLocalAudio(forwardAudioFile, recordDurationSeconds, localMicDir, localMicMixer)
                    );
                }

                if (spk.equalsIgnoreCase("LOCAL")) {
                    this.playLocalAudio(audioFile.getPath());
                } else {
                    this.playRemoteAudio(spk, audioFile.getName(), "start", "music", false);
                }
                // wait for the file to be uploaded
                // 2 seconds might be enough to wait all of them to finish
                Utils.silentSleep(recordDurationSeconds + 2);
                while (deviceManager.hasFileInUploading()) {
                    Utils.silentSleep(1);
                }
                shellHelper.printInfo("Progress: " + cur + "/" + total);
            }
            sessionManager.close();
        });
    }
}
