function [success, metadata] = syncCaptureV2(audioClient, radarObj, sceneId, duration, radarDelay, phoneDelay, audioStartOffset, radarStartOffset, radarDir, audioDir, captureOptions)
% syncCaptureV2
% V2 sync flow for multimodal capture:
% - keep V1 timing model in MATLAB
% - switch audio chain to standalone ultrasonic REST capture

    if nargin < 11 || isempty(captureOptions)
        captureOptions = struct();
    end
    captureOptions = applyDefaultCaptureOptions(captureOptions, sceneId);

    success = false;
    metadata = struct();
    metadata.capture_system_version = 'V2';
    metadata.audio_chain_version = 'V2';
    metadata.audio_server_api = '/api/ultrasonic/capture';
    metadata.radar_delay_ms = radarDelay;
    metadata.phone_delay_ms = phoneDelay;
    metadata.audio_start_offset_ms = audioStartOffset;
    metadata.radar_start_offset_ms = radarStartOffset;
    metadata.device_id = captureOptions.device_id;
    metadata.ultrasonic_config = captureOptions.ultrasonic;
    metadata.route_requested_preset = safeRoutePreset(captureOptions.ultrasonic);
    metadata.audio_capture_mode = captureOptions.mode;
    metadata.audio_format = audioFormatFromFilename(captureOptions.output_name);
    metadata.audio_sample_rate_hz = resolveAudioSampleRate(captureOptions);
    metadata.audio_processing_enabled = logical(captureOptions.process);
    metadata.success_status = 'failed';

    try
        fprintf('  [sync] syncing clocks...\n');
        [offsetMs, syncResponse] = audioClient.syncTime();
        metadata.sntp_offset_ms = offsetMs;
        if isfield(syncResponse, 'rtt_ms')
            metadata.rtt_ms = syncResponse.rtt_ms;
        else
            metadata.rtt_ms = 0;
        end
        fprintf('  [sync] offset = %.2f ms\n', offsetMs);

        currentUtcMs = posixtime(datetime('now', 'TimeZone', 'UTC')) * 1000;
        preTriggerBufferMs = 100;
        maxDelayMs = max(radarDelay, phoneDelay);
        baseTriggerTime = round(currentUtcMs + preTriggerBufferMs + maxDelayMs);
        audioTriggerTime = baseTriggerTime + audioStartOffset;
        radarTriggerTime = baseTriggerTime + radarStartOffset;
        audioCommandTime = audioTriggerTime - phoneDelay;
        radarCommandTime = radarTriggerTime - radarDelay;

        metadata.base_trigger_timestamp_utc = baseTriggerTime;
        metadata.audio_trigger_timestamp_utc = audioTriggerTime;
        metadata.radar_trigger_timestamp_utc = radarTriggerTime;

        fprintf('  [timeline] base trigger = %d ms\n', baseTriggerTime);
        fprintf('  [timeline] audio trigger = %d ms, command send = %d ms\n', audioTriggerTime, round(audioCommandTime));
        fprintf('  [timeline] radar trigger = %d ms, command send = %d ms\n', radarTriggerTime, round(radarCommandTime));

        radarFilename = [sceneId '.bin'];
        radarFilepath = fullfile(radarDir, radarFilename);
        metadata.radar_file = radarFilename;

        luaFilepath = strrep(radarFilepath, '\', '\\');
        fprintf('  [radar] pre-configuring radar capture path...\n');
        Lua_config = sprintf('ar1.CaptureCardConfig_StartRecord("%s", 1)', luaFilepath);
        RtttNetClientAPI.RtttNetClient.SendCommand(Lua_config);
        RtttNetClientAPI.RtttNetClient.SendCommand('RSTD.Sleep(1000)');

        audioSent = false;
        radarSent = false;
        captureResponse = struct();

        while true
            currentLoopUtcMs = posixtime(datetime('now', 'TimeZone', 'UTC')) * 1000;

            if ~audioSent && currentLoopUtcMs >= audioCommandTime
                fprintf('  [audio] sending ultrasonic capture command...\n');
                metadata.audio_command_sent_utc = round(currentLoopUtcMs);
                captureResponse = audioClient.startUltrasonicCapture(sceneId, duration, captureOptions);
                if isfield(captureResponse, 'request_device_id')
                    metadata.device_id = captureResponse.request_device_id;
                end
                if isfield(captureResponse, 'request_output')
                    metadata.audio_files = {captureResponse.request_output};
                else
                    metadata.audio_files = {captureOptions.output_name};
                end
                if isfield(captureResponse, 'request_capture_mode')
                    metadata.audio_capture_mode = captureResponse.request_capture_mode;
                end
                if isfield(captureResponse, 'request_audio_format')
                    metadata.audio_format = captureResponse.request_audio_format;
                end
                audioSent = true;
            end

            if ~radarSent && currentLoopUtcMs >= radarCommandTime
                fprintf('  [radar] sending StartFrame...\n');
                metadata.radar_command_sent_utc = round(currentLoopUtcMs);
                RtttNetClientAPI.RtttNetClient.SendCommand('ar1.StartFrame()');
                radarSent = true;
            end

            if audioSent && radarSent
                break;
            end
            pause(0.005);
        end

        fprintf('  [capture] recording for %.2f s...\n', duration);
        pause(duration + captureOptions.radar_stop_margin_seconds);

        fprintf('  [stop] stopping radar capture...\n');
        RtttNetClientAPI.RtttNetClient.SendCommand('ar1.StopFrame()');

        [uploadedOk, ultrasonicStatus, uploadedFile] = audioClient.waitForUltrasonicCapture( ...
            captureOptions.output_name, ...
            captureOptions.server_audio_root, ...
            metadata.device_id, ...
            captureOptions.upload_timeout_seconds);
        metadata.ultrasonic_capture_status = ultrasonicStatus;
        metadata = mergeRouteMetadataFromStatus(metadata, ultrasonicStatus);

        if ~uploadedOk
            try
                audioClient.stopUltrasonicCapture(metadata.device_id);
            catch
            end
            if strcmp(safeField(metadata, 'route_binding_status', ''), 'failed')
                metadata.success_status = 'audio_route_binding_failed';
                fprintf('  [error] ultrasonic route binding failed: %s\n', safeField(metadata, 'route_error_message', 'unknown'));
            else
                metadata.success_status = 'audio_upload_timeout';
                fprintf('  [error] ultrasonic upload did not finish in time.\n');
            end
            return;
        end

        destinationAudioPath = fullfile(audioDir, captureOptions.output_name);
        copyfile(uploadedFile, destinationAudioPath, 'f');
        metadata.audio_files = {captureOptions.output_name};
        metadata.audio_server_file = uploadedFile;
        metadata.audio_server_relative_path = makeRelativePath(uploadedFile, captureOptions.server_audio_root);
        fprintf('  [audio] archived file: %s\n', captureOptions.output_name);

        [radarOk, actualRadarFile, radarSizeMb] = validateRadarFile(radarFilepath);
        if ~radarOk
            metadata.success_status = 'radar_file_missing_or_small';
            return;
        end
        metadata.radar_file = actualRadarFile;
        fprintf('  [radar] archived BIN: %s (%.2f MB)\n', actualRadarFile, radarSizeMb);

        if isstruct(captureResponse) && isfield(captureResponse, 'request_ultrasonic')
            metadata.ultrasonic_config = captureResponse.request_ultrasonic;
        end
        metadata = mergeRouteMetadataFromStatus(metadata, ultrasonicStatus);

        metadata.success_status = 'success';
        success = true;
        fprintf('  [done] multimodal V2 capture succeeded.\n');
    catch ME
        metadata.success_status = ['error: ' ME.message];
        fprintf('  [error] %s\n', ME.message);
    end
end

function captureOptions = applyDefaultCaptureOptions(captureOptions, sceneId)
    if ~isfield(captureOptions, 'device_id')
        captureOptions.device_id = '';
    end
    if ~isfield(captureOptions, 'mode') || isempty(captureOptions.mode)
        captureOptions.mode = 'pro';
    end
    if ~isfield(captureOptions, 'process')
        captureOptions.process = false;
    end
    if ~isfield(captureOptions, 'forward')
        captureOptions.forward = true;
    end
    if ~isfield(captureOptions, 'delete_after_forward')
        captureOptions.delete_after_forward = true;
    end
    if ~isfield(captureOptions, 'output_name') || isempty(captureOptions.output_name)
        captureOptions.output_name = [sceneId audioExtensionForMode(captureOptions.mode)];
    end
    captureOptions.output_name = normalizeAudioOutputName(captureOptions.output_name, captureOptions.mode);
    if ~isfield(captureOptions, 'server_audio_root')
        captureOptions.server_audio_root = '';
    end
    if ~isfield(captureOptions, 'upload_timeout_seconds')
        captureOptions.upload_timeout_seconds = 20;
    end
    if ~isfield(captureOptions, 'radar_stop_margin_seconds')
        captureOptions.radar_stop_margin_seconds = 1.0;
    end
    if ~isfield(captureOptions, 'ultrasonic') || isempty(captureOptions.ultrasonic)
        captureOptions.ultrasonic = defaultUltrasonicConfig();
    else
        captureOptions.ultrasonic = mergeStructs(defaultUltrasonicConfig(), captureOptions.ultrasonic);
    end
end

function cfg = defaultUltrasonicConfig()
    cfg = struct( ...
        'enabled', true, ...
        'mode', 'fmcw', ...
        'routePreset', '', ...
        'sampleRateHz', 48000, ...
        'startFreqHz', 20000.0, ...
        'endFreqHz', 22000.0, ...
        'chirpDurationMs', 40, ...
        'idleDurationMs', 0, ...
        'amplitude', 0.30, ...
        'windowType', 'hann', ...
        'repeat', true);
end

function merged = mergeStructs(baseStruct, overrideStruct)
    merged = baseStruct;
    if isempty(overrideStruct)
        return;
    end

    names = fieldnames(overrideStruct);
    for idx = 1:numel(names)
        merged.(names{idx}) = overrideStruct.(names{idx});
    end
end

function [ok, actualRadarFile, radarSizeMb] = validateRadarFile(plannedRadarFilepath)
    ok = false;
    actualRadarFile = '';
    radarSizeMb = 0;

    [filepathDir, filepathBase, ~] = fileparts(plannedRadarFilepath);
    rawFile = fullfile(filepathDir, [filepathBase '_Raw_0.bin']);
    if ~exist(rawFile, 'file')
        fprintf('  [error] radar file missing: %s\n', rawFile);
        return;
    end

    fileInfo = dir(rawFile);
    radarSizeMb = fileInfo.bytes / 1e6;
    actualRadarFile = fileInfo.name;
    ok = fileInfo.bytes >= 1e6;

    if ~ok
        fprintf('  [error] radar file is too small: %.2f MB\n', radarSizeMb);
    end
end

function relPath = makeRelativePath(absPath, rootPath)
    relPath = absPath;
    if isempty(rootPath)
        return;
    end

    absPath = char(string(absPath));
    rootPath = char(string(rootPath));
    rootWithSep = [rootPath filesep];
    if startsWith(lower(absPath), lower(rootWithSep))
        relPath = absPath(numel(rootWithSep) + 1:end);
    end
end

function preset = safeRoutePreset(ultrasonicConfig)
    preset = '';
    if isstruct(ultrasonicConfig) && isfield(ultrasonicConfig, 'routePreset')
        preset = char(string(ultrasonicConfig.routePreset));
    end
end

function ext = audioExtensionForMode(modeName)
    if strcmpi(char(string(modeName)), 'simple')
        ext = '.m4a';
    else
        ext = '.wav';
    end
end

function outputName = normalizeAudioOutputName(outputName, modeName)
    ext = audioExtensionForMode(modeName);
    outputName = char(string(outputName));
    if endsWith(lower(outputName), lower(ext))
        return;
    end

    lastSlash = find(outputName == '/' | outputName == '\', 1, 'last');
    lastDot = find(outputName == '.', 1, 'last');
    if isempty(lastSlash)
        lastSlash = 0;
    end
    if ~isempty(lastDot) && lastDot > lastSlash
        outputName = outputName(1:lastDot - 1);
    end
    outputName = [outputName ext];
end

function formatName = audioFormatFromFilename(filename)
    [~, ~, ext] = fileparts(char(string(filename)));
    ext = lower(ext);
    if startsWith(ext, '.')
        ext = ext(2:end);
    end
    if isempty(ext)
        formatName = 'wav';
    else
        formatName = ext;
    end
end

function sampleRateHz = resolveAudioSampleRate(captureOptions)
    if strcmpi(char(string(captureOptions.mode)), 'simple')
        sampleRateHz = 44100;
        return;
    end

    ultrasonicEnabled = false;
    if isfield(captureOptions, 'ultrasonic') && isstruct(captureOptions.ultrasonic) && isfield(captureOptions.ultrasonic, 'enabled')
        ultrasonicEnabled = logical(captureOptions.ultrasonic.enabled);
    end

    if ultrasonicEnabled && isfield(captureOptions.ultrasonic, 'sampleRateHz')
        sampleRateHz = captureOptions.ultrasonic.sampleRateHz;
    else
        sampleRateHz = 44100;
    end
end

function metadata = mergeRouteMetadataFromStatus(metadata, ultrasonicStatus)
    if ~isstruct(ultrasonicStatus) || ~isfield(ultrasonicStatus, 'state') || ~isstruct(ultrasonicStatus.state)
        return;
    end

    state = ultrasonicStatus.state;
    metadata.route_requested_preset = safeField(state, 'route_requested_preset', safeField(metadata, 'route_requested_preset', ''));
    metadata.route_applied_preset = safeField(state, 'route_applied_preset', '');
    metadata.route_binding_status = safeField(state, 'route_binding_status', '');
    metadata.route_output_binding = safeField(state, 'route_output_binding', '');
    metadata.route_input_binding = safeField(state, 'route_input_binding', '');
    metadata.route_error_message = safeField(state, 'route_error_message', '');
    metadata.route_device_model = safeField(state, 'route_device_model', '');
end

function value = safeField(structValue, fieldName, defaultValue)
    if isstruct(structValue) && isfield(structValue, fieldName)
        value = structValue.(fieldName);
    else
        value = defaultValue;
    end
end
