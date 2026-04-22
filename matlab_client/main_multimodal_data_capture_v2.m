clear;
close all;
clc;

%% Multimodal Data Capture V2
% V2 integrates the validated standalone ultrasonic capture chain back into
% the multimodal workflow while keeping the V1 MATLAB-side timing model.

%% User configuration
data_root_path = 'D:\data';
capture_duration = 7;
repeat_count = 3;

RADAR_STARTUP_DELAY = 500;
% Important: this delay must include the standalone ultrasonic app pre-cue
% beep and its recorder/player startup latency.
PHONE_STARTUP_DELAY = 2200;

AUDIO_START_OFFSET = -1000;
RADAR_START_OFFSET = 1000;

% Recording source selection on Android:
% true  -> force standard MIC path
% false -> prefer UNPROCESSED, fallback to VOICE_RECOGNITION
AUDIO_ENABLE_PROCESSING = true;

server_ip = '127.0.0.1';
server_port = 8080;
scenes_version = '_v2';
RSTD_DLL_Path = 'C:\ti\mmwave_studio_02_01_01_00\mmWaveStudio\Clients\RtttNetClientController\RtttNetClientAPI.dll';

ultrasonic_device_id = '';
script_dir = fileparts(mfilename('fullpath'));
ultrasonic_server_audio_root = resolveFirstExistingPath({ ...
    fullfile(script_dir, '..', 'Ultrasound_capture', 'UltrasonicCenterServer', 'audio'), ...
    fullfile(script_dir, '..', '..', 'UltrasonicCenterServer', 'audio'), ...
    fullfile(script_dir, '..', '..', 'AudioCenterServer', 'audio')});
ultrasonic_config = struct( ...
    'enabled', true, ...
    'mode', 'fmcw', ...
    'routePreset', 'mate40pro_bottom_speaker_bottom_mic', ...
    'sampleRateHz', 48000, ...
    'startFreqHz', 20000.0, ...
    'endFreqHz', 22000.0, ...
    'chirpDurationMs', 40, ...
    'idleDurationMs', 0, ...
    'amplitude', 0.30, ...
    'windowType', 'hann', ...
    'repeat', true);

fprintf('========== Multimodal Capture V2 ==========\n');
fprintf('Data root: %s\n', data_root_path);
fprintf('Ultrasonic server root: %s\n', ultrasonic_server_audio_root);

if ~exist(data_root_path, 'dir')
    error('Data root does not exist: %s', data_root_path);
end
if ~exist(RSTD_DLL_Path, 'file')
    error('Radar DLL not found: %s', RSTD_DLL_Path);
end

%% Load hierarchical scenes
[locations, subLocations, actionScenes] = loadHierarchicalScenes(scenes_version);
fprintf('Loaded %d locations, %d sub-locations, %d action scenes.\n', ...
    length(locations), length(subLocations), length(actionScenes));

selected_location = chooseStruct(locations, 'location_name', 'Choose location');
selected_sub_location = chooseSubLocation(subLocations, selected_location.location_id);
confirmOrAbort(selected_location, selected_sub_location, actionScenes);

%% Init ultrasonic client
audioClient = UltrasonicAudioClientV2(server_ip, server_port);
device_id = audioClient.resolveDeviceId(ultrasonic_device_id);
[route_preset, has_route_preset] = readRoutePreset(ultrasonic_config);
if has_route_preset
    preflight = audioClient.preflightRoute(device_id, route_preset);
    fprintf('Route preflight: %s\n', safeField(preflight, 'message', 'ok'));
else
    fprintf('Route preflight: default route (no preset requested)\n');
end
[offset_ms, ~] = audioClient.syncTime();
fprintf('Ultrasonic device: %s\n', device_id);
fprintf('Time sync offset: %.2f ms\n', offset_ms);
if abs(offset_ms) > 100
    warning('Time offset is greater than 100 ms. Re-check server/device clocks if needed.');
end

%% Init radar
fprintf('Initializing radar...\n');
ErrStatus = Init_RSTD_Connection(RSTD_DLL_Path);
if ErrStatus ~= 30000
    error('Radar connection failed with error code %d.', ErrStatus);
end
fprintf('Radar connected.\n');

%% Subject workspace
staff_combo = input('Enter staff combination (for example yh-ssk): ', 's');
if isempty(staff_combo)
    error('Staff combination cannot be empty.');
end

[subject_id, subject_dir, radar_dir, audio_dir] = ensureSubjectWorkspace(data_root_path, staff_combo);
fprintf('Subject ID: %03d\n', subject_id);
fprintf('Subject dir: %s\n', subject_dir);

%% Log setup
log_filename = sprintf('capture_log_v2_%03d_%s.csv', subject_id, datestr(now, 'yyyymmdd_HHMMSS'));
log_filepath = fullfile(subject_dir, log_filename);
log_fid = fopen(log_filepath, 'w', 'n', 'UTF-8');
fprintf(log_fid, 'timestamp,system_version,device_id,location_id,location_name,sub_location_id,sub_location_name,scene_idx,scene_code,repeat_index,success,sntp_offset_ms,rtt_ms,audio_file,radar_file,error_message\n');
fclose(log_fid);

%% Capture loop
captureOptions = struct();
captureOptions.device_id = device_id;
captureOptions.mode = 'pro';
captureOptions.process = AUDIO_ENABLE_PROCESSING;
captureOptions.server_audio_root = ultrasonic_server_audio_root;
captureOptions.ultrasonic = ultrasonic_config;
captureOptions.delete_after_forward = true;
captureOptions.upload_timeout_seconds = max(20, capture_duration + 10);
captureOptions.radar_stop_margin_seconds = 1.0;

total_captures = 0;
success_captures = 0;
failed_captures = 0;

fprintf('\n========== Ready to Capture V2 ==========\n');
fprintf('Action scenes: %d\n', length(actionScenes));
fprintf('Repeat count: %d\n', repeat_count);
fprintf('Total captures planned: %d\n', length(actionScenes) * repeat_count);
fprintf('Audio source mode: %s\n', ternaryText(AUDIO_ENABLE_PROCESSING, 'MIC', 'UNPROCESSED/VOICE_RECOGNITION'));

user_requested_exit = false;
for scene_idx = 1:length(actionScenes)
    if user_requested_exit
        break;
    end

    scene = actionScenes(scene_idx);
    fprintf('\n----------------------------------------\n');
    fprintf('Scene %d/%d\n', scene_idx, length(actionScenes));
    fprintf('Code : %s\n', scene.code);
    fprintf('Intro: %s\n', scene.intro);
    fprintf('----------------------------------------\n');

    for repeat_idx = 1:repeat_count
        prompt = sprintf('Repeat %d/%d. Enter y=start, s=skip, q=quit: ', repeat_idx, repeat_count);
        response = lower(strtrim(input(prompt, 's')));
        if strcmp(response, 'q')
            fprintf('User requested exit.\n');
            user_requested_exit = true;
            break;
        elseif strcmp(response, 's')
            appendCaptureLog(log_filepath, 'V2', device_id, selected_location, selected_sub_location, scene, repeat_idx, false, 0, 0, '', '', 'skipped_by_user');
            continue;
        elseif ~strcmp(response, 'y')
            fprintf('Invalid input. Skipping this repeat.\n');
            appendCaptureLog(log_filepath, 'V2', device_id, selected_location, selected_sub_location, scene, repeat_idx, false, 0, 0, '', '', 'invalid_prompt_input');
            continue;
        end

        sample_id = (scene_idx - 1) * repeat_count + repeat_idx;
        sceneId = sprintf('sample_%03d_%s_%s_%s', sample_id, ...
            selected_location.location_id, selected_sub_location.sub_location_id, scene.code);
        captureOptions.output_name = [sceneId '.wav'];

        fprintf('Starting capture: %s\n', sceneId);
        [success, metadata] = syncCaptureV2(audioClient, [], sceneId, ...
            capture_duration, RADAR_STARTUP_DELAY, PHONE_STARTUP_DELAY, ...
            AUDIO_START_OFFSET, RADAR_START_OFFSET, radar_dir, audio_dir, captureOptions);

        total_captures = total_captures + 1;
        if success
            success_captures = success_captures + 1;
            saveMetadata(metadata, scene, staff_combo, subject_id, subject_dir, repeat_idx, ...
                selected_location, selected_sub_location, sample_id);
            appendCaptureLog(log_filepath, 'V2', device_id, selected_location, selected_sub_location, scene, repeat_idx, true, ...
                safeField(metadata, 'sntp_offset_ms', 0), safeField(metadata, 'rtt_ms', 0), ...
                firstCellOrEmpty(safeField(metadata, 'audio_files', {})), safeField(metadata, 'radar_file', ''), '');
        else
            failed_captures = failed_captures + 1;
            appendCaptureLog(log_filepath, 'V2', device_id, selected_location, selected_sub_location, scene, repeat_idx, false, ...
                safeField(metadata, 'sntp_offset_ms', 0), safeField(metadata, 'rtt_ms', 0), ...
                firstCellOrEmpty(safeField(metadata, 'audio_files', {})), safeField(metadata, 'radar_file', ''), ...
                safeField(metadata, 'success_status', 'capture_failed'));
        end

        fprintf('Progress: success=%d, failed=%d, total=%d\n', success_captures, failed_captures, total_captures);
    end
end

%% Summary
fprintf('\n========== V2 Summary ==========\n');
fprintf('Total   : %d\n', total_captures);
fprintf('Success : %d\n', success_captures);
fprintf('Failed  : %d\n', failed_captures);
fprintf('Log file: %s\n', log_filepath);
fprintf('================================\n');

function selected = chooseStruct(items, labelField, titleText)
    fprintf('\n%s\n', titleText);
    for idx = 1:length(items)
        fprintf('  [%d] %s\n', idx, items(idx).(labelField));
    end
    selectedIdx = promptIndex(length(items));
    selected = items(selectedIdx);
end

function selected = chooseSubLocation(subLocations, locationId)
    mask = false(length(subLocations), 1);
    for idx = 1:length(subLocations)
        mask(idx) = strcmp(subLocations(idx).location_id, locationId);
    end
    available = subLocations(mask);
    if isempty(available)
        error('No sub-locations configured for location %s.', locationId);
    end

    fprintf('\nChoose sub-location\n');
    for idx = 1:length(available)
        fprintf('  [%d] %s\n', idx, available(idx).sub_location_name);
    end
    selectedIdx = promptIndex(length(available));
    selected = available(selectedIdx);
end

function confirmOrAbort(locationInfo, subLocationInfo, actionScenes)
    fprintf('\nCapture plan\n');
    fprintf('  Location     : %s\n', locationInfo.location_name);
    fprintf('  Sub-location : %s\n', subLocationInfo.sub_location_name);
    fprintf('  Action scenes: %d\n', length(actionScenes));
    response = lower(strtrim(input('Enter y to continue, any other key to abort: ', 's')));
    if ~strcmp(response, 'y')
        error('User aborted before capture.');
    end
end

function idx = promptIndex(maxValue)
    while true
        raw = strtrim(input(sprintf('Choose index [1-%d]: ', maxValue), 's'));
        idx = str2double(raw);
        if ~isnan(idx) && idx >= 1 && idx <= maxValue
            return;
        end
        fprintf('Invalid selection.\n');
    end
end

function [subject_id, subject_dir, radar_dir, audio_dir] = ensureSubjectWorkspace(data_root_path, staff_combo)
    mapping_file = fullfile(data_root_path, 'subject_mapping.txt');
    subject_id = -1;

    if exist(mapping_file, 'file')
        fid = fopen(mapping_file, 'r');
        entries = {};
        while ~feof(fid)
            line = strtrim(fgetl(fid));
            if ischar(line) && ~isempty(line)
                entries{end + 1} = line; %#ok<AGROW>
            end
        end
        fclose(fid);

        max_id = 0;
        for idx = 1:length(entries)
            parts = strsplit(entries{idx}, ':');
            if numel(parts) ~= 2
                continue;
            end
            current_name = strtrim(parts{1});
            current_id = str2double(strtrim(parts{2}));
            if strcmp(current_name, staff_combo)
                subject_id = current_id;
            end
            if ~isnan(current_id)
                max_id = max(max_id, current_id);
            end
        end

        if subject_id == -1
            subject_id = max_id + 1;
            fid = fopen(mapping_file, 'a');
            fprintf(fid, '%s:%d\n', staff_combo, subject_id);
            fclose(fid);
        end
    else
        subject_id = 1;
        fid = fopen(mapping_file, 'w');
        fprintf(fid, '%s:%d\n', staff_combo, subject_id);
        fclose(fid);
    end

    subject_dir = fullfile(data_root_path, 'subjects', sprintf('subject_%03d', subject_id));
    radar_dir = fullfile(subject_dir, 'radar');
    audio_dir = fullfile(subject_dir, 'audio');

    if ~exist(subject_dir, 'dir')
        mkdir(subject_dir);
    end
    if ~exist(radar_dir, 'dir')
        mkdir(radar_dir);
    end
    if ~exist(audio_dir, 'dir')
        mkdir(audio_dir);
    end
end

function appendCaptureLog(log_filepath, versionLabel, device_id, locationInfo, subLocationInfo, sceneInfo, repeat_idx, successFlag, sntpOffset, rttMs, audioFile, radarFile, errorMessage)
    fid = fopen(log_filepath, 'a', 'n', 'UTF-8');
    fprintf(fid, '%s,%s,%s,%s,%s,%s,%s,%d,%s,%d,%s,%.3f,%.3f,%s,%s,%s\n', ...
        datestr(now, 'yyyy-mm-dd HH:MM:SS'), ...
        versionLabel, ...
        device_id, ...
        locationInfo.location_id, ...
        locationInfo.location_name, ...
        subLocationInfo.sub_location_id, ...
        subLocationInfo.sub_location_name, ...
        sceneInfo.idx, ...
        sceneInfo.code, ...
        repeat_idx, ...
        logicalToString(successFlag), ...
        sntpOffset, ...
        rttMs, ...
        audioFile, ...
        radarFile, ...
        sanitizeCsv(errorMessage));
    fclose(fid);
end

function value = safeField(structValue, fieldName, defaultValue)
    if isstruct(structValue) && isfield(structValue, fieldName)
        value = structValue.(fieldName);
    else
        value = defaultValue;
    end
end

function text = logicalToString(flag)
    if flag
        text = 'true';
    else
        text = 'false';
    end
end

function text = firstCellOrEmpty(cellValue)
    if iscell(cellValue) && ~isempty(cellValue)
        text = cellValue{1};
    else
        text = '';
    end
end

function text = sanitizeCsv(text)
    text = strrep(char(string(text)), ',', ';');
end

function text = ternaryText(flag, trueText, falseText)
    if flag
        text = trueText;
    else
        text = falseText;
    end
end

function [routePreset, hasPreset] = readRoutePreset(ultrasonic_config)
    routePreset = '';
    hasPreset = false;
    if isstruct(ultrasonic_config) && isfield(ultrasonic_config, 'routePreset')
        routePreset = char(string(ultrasonic_config.routePreset));
        hasPreset = ~isempty(strtrim(routePreset));
    end
end

function resolvedPath = resolveFirstExistingPath(candidates)
    resolvedPath = '';
    for idx = 1:numel(candidates)
        currentPath = char(string(candidates{idx}));
        if exist(currentPath, 'dir')
            resolvedPath = currentPath;
            return;
        end
    end

    error('Could not locate ultrasonic server audio root. Checked paths: %s', strjoin(candidates, ', '));
end
