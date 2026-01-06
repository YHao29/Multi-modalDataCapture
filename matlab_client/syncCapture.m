function [success, metadata] = syncCapture(audioClient, radarObj, sceneId, duration, radarDelay, savePath)
% syncCapture - 多模态同步采集协调函数
%
% 输入:
%   audioClient: AudioClient 实例（用于音频采集）
%   radarObj: 雷达 API 对象（RtttNetClientAPI.RtttNetClient）
%   sceneId: 场景ID字符串（用于文件命名）
%   duration: 采集时长（秒）
%   radarDelay: 雷达启动延迟（毫秒）
%   savePath: 数据保存路径
%
% 输出:
%   success: 采集是否成功
%   metadata: 元数据结构体，包含：
%       - trigger_timestamp_utc: 理论触发时间戳（UTC毫秒）
%       - sntp_offset_ms: SNTP时间偏移（毫秒）
%       - rtt_ms: 往返时延（毫秒）
%       - radar_delay_ms: 雷达启动延迟（毫秒）
%       - radar_file: 雷达数据文件名
%       - audio_files: 音频数据文件名（cell数组）
%       - success_status: 成功状态字符串
%
% 示例:
%   [success, meta] = syncCapture(audioClient, ar1, 'yh-ssk-A1-B1-C1-D1-E1-01', 10, 1000, 'D:\data\');

    % 初始化返回值
    success = false;
    metadata = struct();
    metadata.radar_delay_ms = radarDelay;
    metadata.success_status = 'failed';
    
    try
        %% 1. 时间同步 - 获取当前时间偏移
        fprintf('  [同步] 执行时间同步...\n');
        [offset_ms, rtt_ms] = audioClient.syncTime();
        metadata.sntp_offset_ms = offset_ms;
        metadata.rtt_ms = rtt_ms;
        fprintf('  [同步] 时间偏移: %.2f ms, RTT: %.2f ms\n', offset_ms, rtt_ms);
        
        %% 2. 计算触发时间点
        % 当前 UTC 时间（毫秒）
        current_utc_ms = posixtime(datetime('now', 'TimeZone', 'UTC')) * 1000;
        
        % 预触发缓冲时间
        PRE_TRIGGER_BUFFER = 100;  % 毫秒
        
        % 计算理论同步触发时间点
        % T_trigger = T_now + PRE_TRIGGER_BUFFER + radarDelay
        trigger_timestamp_utc = round(current_utc_ms + PRE_TRIGGER_BUFFER + radarDelay);
        metadata.trigger_timestamp_utc = trigger_timestamp_utc;
        
        fprintf('  [时序] 当前时间: %d ms (UTC)\n', round(current_utc_ms));
        fprintf('  [时序] 触发时间: %d ms (UTC)\n', trigger_timestamp_utc);
        fprintf('  [时序] 延迟计算: 预触发=%dms + 雷达延迟=%dms\n', PRE_TRIGGER_BUFFER, radarDelay);
        
        %% 3. 先启动雷达（提前 radarDelay 毫秒）
        fprintf('  [雷达] 发送雷达启动命令...\n');
        
        % 配置雷达数据文件
        radar_filename = [sceneId '.bin'];
        radar_filepath = fullfile(savePath, radar_filename);
        metadata.radar_file = radar_filename;
        
        % 配置雷达采集
        Lua_config = sprintf('ar1.CaptureCardConfig_StartRecord("%s", 1)', radar_filepath);
        radarObj.SendCommand(Lua_config);
        radarObj.SendCommand('RSTD.Sleep(500)');  % 短暂等待配置生效
        
        % 发送启动命令（此时雷达需要约 radarDelay 毫秒才真正开始）
        t_radar_cmd = posixtime(datetime('now', 'TimeZone', 'UTC')) * 1000;
        radarObj.SendCommand('ar1.StartFrame()');
        fprintf('  [雷达] 启动命令已发送 (T=%d ms)\n', round(t_radar_cmd));
        
        %% 4. 等待到音频启动时间点（T_trigger - PRE_TRIGGER_BUFFER）
        audio_trigger_time = trigger_timestamp_utc - PRE_TRIGGER_BUFFER;
        
        while true
            current_time = posixtime(datetime('now', 'TimeZone', 'UTC')) * 1000;
            time_diff = audio_trigger_time - current_time;
            
            if time_diff <= 0
                break;
            end
            
            % 如果还有时间，短暂休眠
            if time_diff > 50
                pause(time_diff / 2000);  % 休眠一半时间，避免过冲
            end
        end
        
        %% 5. 启动音频采集
        fprintf('  [音频] 发送音频采集命令...\n');
        audio_filename = [sceneId '.wav'];
        metadata.audio_files = {audio_filename};
        
        audio_success = audioClient.startRecording(sceneId, duration, trigger_timestamp_utc);
        t_audio_cmd = posixtime(datetime('now', 'TimeZone', 'UTC')) * 1000;
        fprintf('  [音频] 启动命令已发送 (T=%d ms)\n', round(t_audio_cmd));
        
        if ~audio_success
            warning('  [音频] 音频采集启动失败');
            metadata.success_status = 'audio_failed';
            return;
        end
        
        %% 6. 等待采集完成
        fprintf('  [采集] 正在同步采集数据 (%d秒)...\n', duration);
        pause(duration + 2);  % 额外等待2秒确保完成
        
        %% 7. 停止采集
        fprintf('  [停止] 停止采集...\n');
        radarObj.SendCommand('ar1.StopFrame()');
        audioClient.stopRecording();
        
        pause(1);  % 等待数据保存
        
        %% 8. 验证文件
        fprintf('  [验证] 检查数据文件...\n');
        
        % 检查雷达文件
        if exist(radar_filepath, 'file')
            radar_size = dir(radar_filepath).bytes;
            fprintf('  [验证] 雷达文件: %s (%.2f MB)\n', radar_filename, radar_size/1e6);
            
            if radar_size < 1e6  % 小于1MB认为异常
                warning('  [验证] 雷达文件大小异常');
                metadata.success_status = 'radar_file_small';
                return;
            end
        else
            warning('  [验证] 雷达文件不存在');
            metadata.success_status = 'radar_file_missing';
            return;
        end
        
        % 检查音频文件（在服务器端，这里只记录）
        fprintf('  [验证] 音频文件将由服务器保存\n');
        
        %% 9. 成功
        success = true;
        metadata.success_status = 'success';
        fprintf('  [完成] 同步采集成功！\n');
        
    catch ME
        fprintf('  [错误] 采集失败: %s\n', ME.message);
        metadata.success_status = sprintf('error: %s', ME.message);
        success = false;
    end
end
