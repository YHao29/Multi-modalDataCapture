function [success, metadata] = syncCapture(audioClient, radarObj, sceneId, duration, radarDelay, phoneDelay, audioStartOffset, radarStartOffset, radarDir, audioDir)
% syncCapture - 多模态同步采集协调函数
%
% 输入:
%   audioClient: AudioClient 实例（用于音频采集）
%   radarObj: 未使用（保留用于兼容性，传递[]即可）
%   sceneId: 场景ID字符串（用于文件命名）
%   duration: 采集时长（秒）
%   radarDelay: 雷达启动延迟（毫秒）- 设备从收到命令到实际开始采集的延迟
%   phoneDelay: 手机音频启动延迟（毫秒）- 设备从收到命令到实际开始采集的延迟
%   audioStartOffset: 音频采集开始时间偏移（毫秒）- 正值延后，负值提前
%   radarStartOffset: 雷达采集开始时间偏移（毫秒）- 正值延后，负值提前
%   radarDir: 雷达数据保存目录
%   audioDir: 音频数据保存目录（用于记录元数据）
%
% 输出:
%   success: 采集是否成功
%   metadata: 元数据结构体，包含：
%       - trigger_timestamp_utc: 理论触发时间戳（UTC毫秒）
%       - sntp_offset_ms: SNTP时间偏移（毫秒）
%       - rtt_ms: 往返时延（毫秒）
%       - radar_delay_ms: 雷达启动延迟（毫秒）
%       - phone_delay_ms: 手机启动延迟（毫秒）
%       - radar_file: 雷达数据文件名（相对于radarDir）
%       - audio_files: 音频数据文件名（cell数组）
%       - success_status: 成功状态字符串
%
% 示例:
%   % 音频和雷达同时开始
%   [success, meta] = syncCapture(audioClient, [], 'sample_001', 10, 1000, 2200, 0, 0, radarDir, audioDir);
%   % 音频提前5秒开始
%   [success, meta] = syncCapture(audioClient, [], 'sample_001', 10, 1000, 2200, -5000, 0, radarDir, audioDir);
%   % 雷达延后3秒开始
%   [success, meta] = syncCapture(audioClient, [], 'sample_001', 10, 1000, 2200, 0, 3000, radarDir, audioDir);

    % 初始化返回值
    success = false;
    metadata = struct();
    metadata.radar_delay_ms = radarDelay;
    metadata.phone_delay_ms = phoneDelay;
    metadata.audio_start_offset_ms = audioStartOffset;
    metadata.radar_start_offset_ms = radarStartOffset;
    metadata.success_status = 'failed';
    
    try
        %% 1. 时间同步 - 获取当前时间偏移
        fprintf('  [同步] 执行时间同步...\n');
        offset_ms = audioClient.syncTime();
        metadata.sntp_offset_ms = offset_ms;
        metadata.rtt_ms = 0;  % AudioClient.syncTime() 不返回RTT
        fprintf('  [同步] 时间偏移: %.2f ms\n', offset_ms);
        
        %% 2. 计算触发时间点
        % 当前 UTC 时间（毫秒）
        current_utc_ms = posixtime(datetime('now', 'TimeZone', 'UTC')) * 1000;
        
        % 预触发缓冲时间
        PRE_TRIGGER_BUFFER = 100;  % 毫秒
        
        % 新逻辑：使用offset参数控制采集开始时间
        % 基准触发时间：以最大启动延迟为基准
        max_delay = max(radarDelay, phoneDelay);
        base_trigger_time = round(current_utc_ms + PRE_TRIGGER_BUFFER + max_delay);
        
        % 计算各自的实际触发时间（加上offset）
        % offset为正：延后开始；offset为负：提前开始
        audio_trigger_time = base_trigger_time + audioStartOffset;
        radar_trigger_time = base_trigger_time + radarStartOffset;
        
        % 记录基准触发时间
        metadata.base_trigger_timestamp_utc = base_trigger_time;
        metadata.audio_trigger_timestamp_utc = audio_trigger_time;
        metadata.radar_trigger_timestamp_utc = radar_trigger_time;
        
        fprintf('  [时序] 当前时间: %d ms (UTC)\n', round(current_utc_ms));
        fprintf('  [时序] 基准触发时间: %d ms (UTC)\n', base_trigger_time);
        fprintf('  [时序] 音频触发时间: %d ms (偏移=%+d ms)\n', audio_trigger_time, audioStartOffset);
        fprintf('  [时序] 雷达触发时间: %d ms (偏移=%+d ms)\n', radar_trigger_time, radarStartOffset);
            
        % 计算各自的命令发送时间点
        % 命令发送时间 = 触发时间 - 设备启动延迟
        audio_cmd_time = audio_trigger_time - phoneDelay;
        radar_cmd_time = radar_trigger_time - radarDelay;
        
        fprintf('  [时序] 计划音频命令发送: %d ms (触发时间%d - 启动延迟%d)\n', ...
            round(audio_cmd_time), audio_trigger_time, phoneDelay);
        fprintf('  [时序] 计划雷达命令发送: %d ms (触发时间%d - 启动延迟%d)\n', ...
            round(radar_cmd_time), radar_trigger_time, radarDelay);
        
        %% 3. 先配置雷达（不启动，仅Setup）
        fprintf('  [雷达] 配置雷达...\n');
        radar_filename = [sceneId '.bin'];
        radar_filepath = fullfile(radarDir, radar_filename);
        metadata.radar_file = radar_filename;
        
        lua_filepath = strrep(radar_filepath, '\', '\\');
        Lua_config = sprintf('ar1.CaptureCardConfig_StartRecord("%s", 1)', lua_filepath);
        RtttNetClientAPI.RtttNetClient.SendCommand(Lua_config);
        RtttNetClientAPI.RtttNetClient.SendCommand('RSTD.Sleep(1000)');  % 等待配置生效
        
        %% 4. 调度执行
        % 逻辑：当前时间可能还未到达最早的发送时间点(min_start_time)，需要等待
        % 然后先发第一个，再等，再发第二个
        
        % 修正调度逻辑：确保两个命令都按计划时间发送，而不是仅靠顺序
        
        while true
            curr_t = posixtime(datetime('now', 'TimeZone', 'UTC')) * 1000;
            
            % 检查音频是否需要发送
            if audio_cmd_time > 0 && curr_t >= audio_cmd_time
                fprintf('  [音频] 发送音频采集命令... (实际T=%d, 误差=%d ms)\n', round(curr_t), round(curr_t - audio_cmd_time));
                audio_filename = [sceneId '.wav'];
                metadata.audio_files = {audio_filename};
                
                % 注意：startRecording 调用可能会阻塞一小段时间（HTTP请求）
                audio_success = audioClient.startRecording(sceneId, duration, audio_trigger_time);
                if ~audio_success
                    warning('  [音频] 音频采集启动失败');
                    metadata.success_status = 'audio_failed';
                    return;
                end
                
                audio_cmd_time = -1; % 标记已发送
            end
            
            % 检查雷达是否需要发送
            if radar_cmd_time > 0 && curr_t >= radar_cmd_time
                fprintf('  [雷达] 发送雷达启动命令... (实际T=%d, 误差=%d ms)\n', round(curr_t), round(curr_t - radar_cmd_time));
                RtttNetClientAPI.RtttNetClient.SendCommand('ar1.StartFrame()');
                radar_cmd_time = -1; % 标记已发送
            end
            
            % 如果都已发送，退出循环
            if audio_cmd_time == -1 && radar_cmd_time == -1
                break;
            end
            
            % 短暂休眠，避免CPU占用过高
            pause(0.005); 
        end
        
        %% 5. 等待采集完成

        fprintf('  [采集] 正在同步采集数据 (%d秒)...\n', duration);
        pause(duration + 2);  % 额外等待2秒确保完成
        
        %% 7. 停止采集
        fprintf('  [停止] 停止采集...\n');
        RtttNetClientAPI.RtttNetClient.SendCommand('ar1.StopFrame()');
        audioClient.stopRecording();
        
        pause(1);  % 等待数据保存
        
        %% 8. 验证文件
        fprintf('  [验证] 检查数据文件...\n');
        
        % 检查雷达文件 - 雷达实际生成的是 *_Raw_0.bin
        % 搜索匹配的文件
        [filepath_dir, filepath_base, ~] = fileparts(radar_filepath);
        search_pattern = fullfile(filepath_dir, [filepath_base '_Raw_0.bin']);
        
        if exist(search_pattern, 'file')
            actual_radar_file = [filepath_base '_Raw_0.bin'];
            radar_size = dir(search_pattern).bytes;
            fprintf('  [验证] 雷达文件: %s (%.2f MB)\n', actual_radar_file, radar_size/1e6);
            
            % 更新元数据中的实际文件名
            metadata.radar_file = actual_radar_file;
            
            if radar_size < 1e6  % 小于1MB认为异常
                warning('  [验证] 雷达文件大小异常');
                metadata.success_status = 'radar_file_small';
                return;
            end
        else
            warning('  [验证] 雷达文件不存在: %s', search_pattern);
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
