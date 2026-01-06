classdef AudioClient < handle
    % AudioClient - MATLAB客户端用于与AudioCenterServer通信
    % 提供录制控制、设备管理和时间同步功能
    
    properties
        serverUrl       % 服务器URL
        timeout         % 请求超时时间（秒）
        connected       % 连接状态
    end
    
    methods
        % 构造函数
        function obj = AudioClient(serverIp, serverPort)
            % 初始化客户端
            % serverIp: 服务器IP地址
            % serverPort: REST API端口（默认8080）
            
            if nargin < 2
                serverPort = 8080;
            end
            
            obj.serverUrl = sprintf('http://%s:%d', serverIp, serverPort);
            obj.timeout = 10;  % 默认10秒超时
            obj.connected = false;
            
            % 测试连接
            try
                obj.checkConnection();
                obj.connected = true;
                fprintf('成功连接到服务器: %s\n', obj.serverUrl);
            catch ME
                warning('无法连接到服务器: %s\n错误: %s', obj.serverUrl, ME.message);
            end
        end
        
        % 检查服务器连接
        function status = checkConnection(obj)
            url = sprintf('%s/api/devices/status', obj.serverUrl);
            options = weboptions('Timeout', obj.timeout, 'ContentType', 'json');
            
            try
                response = webread(url, options);
                status = strcmp(response.status, 'success');
                obj.connected = status;
            catch ME
                status = false;
                obj.connected = false;
                error('服务器连接失败: %s', ME.message);
            end
        end
        
        % 获取已连接的设备列表
        function devices = listDevices(obj)
            url = sprintf('%s/api/devices/list', obj.serverUrl);
            options = weboptions('Timeout', obj.timeout, 'ContentType', 'json');
            
            try
                response = webread(url, options);
                if strcmp(response.status, 'success')
                    devices = response.devices;
                    fprintf('已连接设备数: %d\n', response.device_count);
                else
                    devices = {};
                    warning('获取设备列表失败');
                end
            catch ME
                devices = {};
                error('获取设备列表错误: %s', ME.message);
            end
        end
        
        % 同步时间
        function offset = syncTime(obj)
            url = sprintf('%s/api/time/sync', obj.serverUrl);
            options = weboptions('Timeout', obj.timeout, ...
                                'ContentType', 'json', ...
                                'MediaType', 'application/json');
            
            % 获取客户端时间戳（毫秒）
            clientTime = round(posixtime(datetime('now')) * 1000);
            
            % 发送同步请求
            requestData = struct('client_timestamp', clientTime);
            
            try
                response = webwrite(url, requestData, options);
                if strcmp(response.status, 'success')
                    serverTime = response.server_timestamp;
                    offset = response.offset_ms;
                    fprintf('时间同步成功\n');
                    fprintf('  客户端时间: %d\n', clientTime);
                    fprintf('  服务器时间: %d\n', serverTime);
                    fprintf('  时间偏移: %d ms\n', offset);
                else
                    offset = 0;
                    warning('时间同步失败');
                end
            catch ME
                offset = 0;
                error('时间同步错误: %s', ME.message);
            end
        end
        
        % 开始录制
        function success = startRecording(obj, sceneId, duration, timestamp)
            % startRecording 开始录制音频
            % sceneId: 场景ID（文件名前缀）
            % duration: 录制时长（秒）
            % timestamp: 时间戳（可选，默认使用当前时间）
            
            if nargin < 4
                timestamp = round(posixtime(datetime('now')) * 1000);
            end
            if nargin < 3
                duration = 10;
            end
            
            url = sprintf('%s/api/recording/start', obj.serverUrl);
            options = weboptions('Timeout', obj.timeout, ...
                                'ContentType', 'json', ...
                                'MediaType', 'application/json');
            
            requestData = struct('scene_id', sceneId, ...
                               'timestamp', timestamp, ...
                               'duration', duration);
            
            try
                response = webwrite(url, requestData, options);
                success = strcmp(response.status, 'success');
                
                if success
                    fprintf('开始录制: %s (时长: %d秒)\n', sceneId, duration);
                else
                    warning('录制启动失败: %s', response.message);
                end
            catch ME
                success = false;
                error('开始录制错误: %s', ME.message);
            end
        end
        
        % 停止录制
        function success = stopRecording(obj)
            url = sprintf('%s/api/recording/stop', obj.serverUrl);
            options = weboptions('Timeout', obj.timeout, ...
                                'ContentType', 'json', ...
                                'MediaType', 'application/json', ...
                                'RequestMethod', 'post');
            
            try
                response = webwrite(url, struct(), options);
                success = strcmp(response.status, 'success');
                
                if success
                    fprintf('停止录制成功\n');
                else
                    warning('停止录制失败: %s', response.message);
                end
            catch ME
                success = false;
                error('停止录制错误: %s', ME.message);
            end
        end
        
        % 获取录制状态
        function status = getRecordingStatus(obj)
            url = sprintf('%s/api/recording/status', obj.serverUrl);
            options = weboptions('Timeout', obj.timeout, 'ContentType', 'json');
            
            try
                response = webread(url, options);
                if strcmp(response.status, 'success')
                    status = response;
                    fprintf('录制状态: %s\n', ...
                        iif(response.is_recording, '录制中', '未录制'));
                    if response.is_recording
                        fprintf('  当前场景: %s\n', response.current_scene);
                    end
                else
                    status = struct();
                    warning('获取状态失败');
                end
            catch ME
                status = struct();
                error('获取状态错误: %s', ME.message);
            end
        end
        
        % 等待录制完成
        function waitForRecording(obj, duration, checkInterval)
            % waitForRecording 等待录制完成
            % duration: 预期录制时长（秒）
            % checkInterval: 检查间隔（秒，默认0.5秒）
            
            if nargin < 3
                checkInterval = 0.5;
            end
            
            % 额外等待缓冲时间
            totalWait = duration + 2;
            fprintf('等待录制完成 (%d秒)...\n', totalWait);
            
            elapsed = 0;
            while elapsed < totalWait
                pause(checkInterval);
                elapsed = elapsed + checkInterval;
                
                % 每秒显示进度
                if mod(elapsed, 1) < checkInterval
                    fprintf('  已等待: %.0f/%.0f 秒\n', elapsed, totalWait);
                end
            end
            
            fprintf('录制完成\n');
        end
    end
end

% 辅助函数
function result = iif(condition, trueVal, falseVal)
    if condition
        result = trueVal;
    else
        result = falseVal;
    end
end
