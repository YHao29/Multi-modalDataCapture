classdef UltrasonicAudioClientV2 < handle
    % UltrasonicAudioClientV2
    % MATLAB client for the standalone ultrasonic REST chain.
    % The timing model stays on the MATLAB side, matching V1:
    % 1. sync time
    % 2. compute command send moments with startup-delay compensation
    % 3. send ultrasonic capture command and radar command at planned times

    properties
        serverUrl
        timeout
        connected
    end

    methods
        function obj = UltrasonicAudioClientV2(serverIp, serverPort)
            if nargin < 2
                serverPort = 8080;
            end

            obj.serverUrl = sprintf('http://%s:%d', serverIp, serverPort);
            obj.timeout = 10;
            obj.connected = false;

            try
                obj.checkConnection();
                obj.connected = true;
                fprintf('Connected to ultrasonic server: %s\n', obj.serverUrl);
            catch ME
                warning('Ultrasonic server connection failed: %s', ME.message);
            end
        end

        function status = checkConnection(obj)
            url = sprintf('%s/api/devices/status', obj.serverUrl);
            response = webread(url, UltrasonicAudioClientV2.jsonOptions(obj.timeout));
            status = isfield(response, 'status') && strcmp(response.status, 'success');
            obj.connected = status;
            if ~status
                error('Server status check failed.');
            end
        end

        function devices = listDevices(obj)
            url = sprintf('%s/api/devices/list', obj.serverUrl);
            response = webread(url, UltrasonicAudioClientV2.jsonOptions(obj.timeout));
            if ~(isfield(response, 'status') && strcmp(response.status, 'success'))
                error('Failed to list devices.');
            end
            devices = UltrasonicAudioClientV2.normalizeDeviceList(response.devices);
        end

        function deviceId = resolveDeviceId(obj, requestedDeviceId)
            if nargin < 2
                requestedDeviceId = '';
            end

            devices = obj.listDevices();
            if ~isempty(requestedDeviceId)
                if ~any(strcmp(devices, requestedDeviceId))
                    error('Requested device %s is not online.', requestedDeviceId);
                end
                deviceId = requestedDeviceId;
                return;
            end

            if numel(devices) ~= 1
                error('Need exactly one online device for auto selection. Found %d.', numel(devices));
            end
            deviceId = devices{1};
        end

        function [offsetMs, response] = syncTime(obj)
            url = sprintf('%s/api/time/sync', obj.serverUrl);
            payload = struct('client_timestamp', java.lang.System.currentTimeMillis());
            response = webwrite(url, payload, UltrasonicAudioClientV2.jsonOptions(obj.timeout));
            if ~(isfield(response, 'status') && strcmp(response.status, 'success'))
                error('Time sync failed.');
            end

            if isfield(response, 'offset_ms')
                offsetMs = response.offset_ms;
            else
                offsetMs = 0;
            end
        end

        function response = preflightRoute(obj, deviceId, routePreset)
            if nargin < 3 || isempty(routePreset)
                response = struct('status', 'success', 'route_preset', '', 'message', 'default route');
                return;
            end
            if nargin < 2 || isempty(deviceId)
                deviceId = 'ALL';
            end

            url = sprintf('%s/api/ultrasonic/route/preflight', obj.serverUrl);
            payload = struct('deviceId', deviceId, 'routePreset', routePreset);
            response = webwrite(url, payload, UltrasonicAudioClientV2.jsonOptions(obj.timeout));
            if ~(isfield(response, 'status') && strcmp(response.status, 'success'))
                error('Ultrasonic route preflight failed.');
            end
        end

        function response = startUltrasonicCapture(obj, sceneId, durationSeconds, captureOptions)
            if nargin < 4 || isempty(captureOptions)
                captureOptions = struct();
            end

            payload = UltrasonicAudioClientV2.defaultCapturePayload();

            if isfield(captureOptions, 'device_id')
                payload.deviceId = obj.resolveDeviceId(captureOptions.device_id);
            else
                payload.deviceId = obj.resolveDeviceId('');
            end

            if isfield(captureOptions, 'output_name') && ~isempty(captureOptions.output_name)
                outputName = captureOptions.output_name;
            else
                outputName = [sceneId '.wav'];
            end
            if numel(outputName) < 4 || ~strcmpi(outputName(end-3:end), '.wav')
                outputName = [outputName '.wav'];
            end

            if isfield(captureOptions, 'mode') && ~isempty(captureOptions.mode)
                payload.mode = captureOptions.mode;
            end
            if isfield(captureOptions, 'process')
                payload.process = logical(captureOptions.process);
            end
            if isfield(captureOptions, 'forward')
                payload.forward = logical(captureOptions.forward);
            end
            if isfield(captureOptions, 'delete_after_forward')
                payload.deleteAfterForward = logical(captureOptions.delete_after_forward);
            end

            payload.output = outputName;
            payload.durationSeconds = durationSeconds;

            if isfield(captureOptions, 'ultrasonic')
                payload.ultrasonic = UltrasonicAudioClientV2.mergeStructs(payload.ultrasonic, captureOptions.ultrasonic);
            end

            url = sprintf('%s/api/ultrasonic/capture/start', obj.serverUrl);
            response = webwrite(url, payload, UltrasonicAudioClientV2.jsonOptions(max(20, obj.timeout)));
            response.request_device_id = payload.deviceId;
            response.request_output = payload.output;
            response.request_ultrasonic = payload.ultrasonic;
        end

        function response = stopUltrasonicCapture(obj, deviceId)
            if nargin < 2 || isempty(deviceId)
                deviceId = 'ALL';
            end

            url = sprintf('%s/api/ultrasonic/capture/stop?deviceId=%s', obj.serverUrl, deviceId);
            response = webwrite(url, struct(), UltrasonicAudioClientV2.jsonOptions(obj.timeout));
        end

        function status = getUltrasonicCaptureStatus(obj)
            url = sprintf('%s/api/ultrasonic/capture/status', obj.serverUrl);
            status = webread(url, UltrasonicAudioClientV2.jsonOptions(obj.timeout));
        end

        function [ok, status, uploadedFile] = waitForUltrasonicCapture(obj, outputName, serverAudioRoot, deviceId, timeoutSeconds)
            if nargin < 5 || isempty(timeoutSeconds)
                timeoutSeconds = 20;
            end
            if nargin < 4
                deviceId = '';
            end
            if nargin < 3
                serverAudioRoot = '';
            end

            startTick = tic;
            status = struct();
            uploadedFile = '';
            ok = false;

            while toc(startTick) < timeoutSeconds
                try
                    status = obj.getUltrasonicCaptureStatus();
                catch
                end

                uploadedFile = UltrasonicAudioClientV2.findUploadedFile(serverAudioRoot, outputName, deviceId);
                capturing = false;
                if isfield(status, 'capturing')
                    capturing = logical(status.capturing);
                end
                if UltrasonicAudioClientV2.hasRouteBindingFailed(status)
                    return;
                end

                if ~isempty(uploadedFile) && ~capturing
                    ok = true;
                    return;
                end
                pause(1.0);
            end

            uploadedFile = UltrasonicAudioClientV2.findUploadedFile(serverAudioRoot, outputName, deviceId);
            if ~isempty(uploadedFile)
                ok = true;
            end
        end
    end

    methods (Static)
        function outputFile = findUploadedFile(serverAudioRoot, outputName, deviceId)
            outputFile = '';
            if nargin < 3
                deviceId = '';
            end
            if isempty(serverAudioRoot) || ~exist(serverAudioRoot, 'dir')
                return;
            end

            matches = [];
            if ~isempty(deviceId)
                matches = dir(fullfile(serverAudioRoot, deviceId, '**', outputName));
            end
            if isempty(matches)
                matches = dir(fullfile(serverAudioRoot, '**', outputName));
            end
            if isempty(matches)
                return;
            end

            [~, newestIdx] = max([matches.datenum]);
            outputFile = fullfile(matches(newestIdx).folder, matches(newestIdx).name);
        end

        function failed = hasRouteBindingFailed(status)
            failed = false;
            if ~isstruct(status) || ~isfield(status, 'state') || ~isstruct(status.state)
                return;
            end
            if isfield(status.state, 'route_binding_status')
                failed = strcmpi(string(status.state.route_binding_status), "failed");
            end
        end
    end

    methods (Static, Access = private)
        function options = jsonOptions(timeoutSeconds)
            options = weboptions( ...
                'Timeout', timeoutSeconds, ...
                'ContentType', 'json', ...
                'MediaType', 'application/json');
        end

        function payload = defaultCapturePayload()
            payload = struct();
            payload.deviceId = 'ALL';
            payload.output = 'ultrasonic_capture.wav';
            payload.durationSeconds = 5;
            payload.process = false;
            payload.forward = true;
            payload.deleteAfterForward = true;
            payload.mode = 'pro';
            payload.ultrasonic = UltrasonicAudioClientV2.defaultUltrasonicConfig();
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

            overrideFields = fieldnames(overrideStruct);
            for idx = 1:numel(overrideFields)
                merged.(overrideFields{idx}) = overrideStruct.(overrideFields{idx});
            end
        end

        function devices = normalizeDeviceList(rawDevices)
            if isempty(rawDevices)
                devices = {};
            elseif iscell(rawDevices)
                devices = rawDevices;
            elseif isstring(rawDevices)
                devices = cellstr(rawDevices(:));
            elseif ischar(rawDevices)
                devices = {rawDevices};
            else
                error('Unsupported device list payload type.');
            end
        end
    end
end
