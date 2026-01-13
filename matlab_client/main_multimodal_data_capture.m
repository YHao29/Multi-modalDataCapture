clear;
close all;
clc;

%% ==================== 多模态数据采集主控程序 ====================
% 文件名: main_multimodal_data_capture.m
% 功能: 批量采集毫米波雷达和超声波数据，实现精确时间同步


%% ==================== 用户配置区 ====================
% !!! 请在采集前仔细配置以下参数 !!!

% 【必填】数据存储根目录（采集员需预先手动创建好路径）
data_root_path = 'F:\testData';

% 【必填】采集时长（秒）
capture_duration = 5;

% 【必填】每个场景重复采集次数
repeat_count = 3;

% 【必填】雷达启动延迟（毫秒）
% !!! 采集前必须运行 test_radar_startup_delay.m 测量此参数 !!!
RADAR_STARTUP_DELAY = 1000;  % 默认1000ms

% 【必填】手机音频启动延迟（毫秒）
% 用于补偿手机启动录音的延迟，确保雷达和音频对齐yh-ssk
PHONE_STARTUP_DELAY = 2200;  % 默认1000ms

% 服务器配置
server_ip = '127.0.0.1';      % AudioCenterServer 的 IP 地址
server_port = 8080;           % REST API 端口

% 场景文件路径
scenes_csv_file = 'radar/scenes_file.csv';

% 雷达配置
RSTD_DLL_Path = 'C:\ti\mmwave_studio_02_01_01_00\mmWaveStudio\Clients\RtttNetClientController\RtttNetClientAPI.dll';

%% ==================== 参数验证 ====================
fprintf('========== 参数验证 ==========\n');

% 检查数据根目录
if ~exist(data_root_path, 'dir')
    error('数据存储根目录不存在: %s\n请手动创建该目录后再运行程序', data_root_path);
end
fprintf(' 数据根目录: %s\n', data_root_path);

% 检查场景文件
if ~exist(scenes_csv_file, 'file')
    error('场景配置文件不存在: %s', scenes_csv_file);
end
fprintf(' 场景文件: %s\n', scenes_csv_file);

% 检查雷达DLL
if ~exist(RSTD_DLL_Path, 'file')
    error('雷达DLL文件不存在: %s', RSTD_DLL_Path);
end
fprintf(' 雷达DLL: 已找到\n');

% 显示配置
fprintf('\n配置参数:\n');
fprintf('  - 采集时长: %d 秒\n', capture_duration);
fprintf('  - 每场景重复: %d 次\n', repeat_count);
fprintf('  - 雷达延迟: %d 毫秒\n', RADAR_STARTUP_DELAY);

%% ==================== 加载场景列表 ====================
fprintf('\n========== 加载场景配置 ==========\n');

try
    % 读取CSV文件，先尝试UTF-8，如果检测到乱码则切换GBK
    encoding_used = 'UTF-8';
    scenes_table = readtable(scenes_csv_file, 'Encoding', encoding_used, 'FileType', 'text');
    
    % 简单检测是否存在乱码（出现替换字符�）
    if height(scenes_table) > 0
        sample_intro = char(scenes_table.intro(1));
        if contains(sample_intro, char(65533))  % 65533是�的编码
            encoding_used = 'GBK';
            scenes_table = readtable(scenes_csv_file, 'Encoding', encoding_used, 'FileType', 'text');
        end
    end
    fprintf(' 场景文件编码: %s\n', encoding_used);
    
    % 提取场景信息
    scene_list = struct([]);
    for i = 1:height(scenes_table)
        scene_list(i).idx = scenes_table.idx(i);
        scene_list(i).intro = char(scenes_table.intro(i));
        scene_list(i).code = char(scenes_table.code(i));
    end
    
    total_scenes = length(scene_list);
    fprintf(' 已加载 %d 个场景\n', total_scenes);
    
    % 显示前3个场景作为示例
    fprintf('\n场景示例:\n');
    for i = 1:min(3, total_scenes)
        fprintf('  [%d] %s (%s)\n', scene_list(i).idx, ...
            scene_list(i).intro, scene_list(i).code);
    end
    if total_scenes > 3
        fprintf('  ...\n');
    end
    
catch ME
    error('加载场景文件失败: %s', ME.message);
end

%% ==================== 初始化 AudioClient ====================
fprintf('\n========== 初始化音频客户端 ==========\n');

try
    % 创建音频客户端
    audioClient = AudioClient(server_ip, server_port);
    fprintf(' 音频客户端已创建\n');
    
    % 检查设备连接
    devices = audioClient.listDevices();
    if isempty(devices)
        error('没有检测到已连接的手机设备\n请确保:\n1. AudioCenterServer 已启动\n2. 手机已连接到服务器端口 6666');
    end
    fprintf(' 已连接设备: %s\n', strjoin(devices, ', '));
    
    % 初始时间同步
    fprintf('执行初始时间同步...\n');
    offset = audioClient.syncTime();
    fprintf(' 时间同步完成 (偏移: %d ms)\n', offset);
    
    % 偏移阈值校验
    % !!! 警告：当前阈值设置较大，仅用于测试 !!!
    % !!! 正式采集前必须修正服务器时间并改回 100 ms !!!
    offset_threshold_ms = 30000000;  % 临时设置为30,000,000 ms（约8.3小时）
    if abs(offset) > offset_threshold_ms
        error('时间同步偏差过大: %d ms (阈值: %d ms)，请检查服务器系统时间或NTP同步。', offset, offset_threshold_ms);
    elseif abs(offset) > 100
        warning('时间同步偏差较大: %d ms，建议修正服务器时间以保证数据精度。', offset);
    end
    
catch ME
    error('音频客户端初始化失败: %s', ME.message);
end

%% ==================== 初始化雷达连接 ====================
fprintf('\n========== 初始化雷达连接 ==========\n');

try
    % 调用初始化函数加载DLL并建立连接
    fprintf('正在初始化雷达...请稍候...\n');
    
    % 设置超时以防止无限等待
    t_start = tic;
    timeout_sec = 30;  % 30秒超时
    
    ErrStatus = Init_RSTD_Connection(RSTD_DLL_Path);
    t_elapsed = toc(t_start);
    
    fprintf('初始化耗时: %.2f 秒\n', t_elapsed);
    
    if (ErrStatus ~= 30000)
        error('雷达连接失败，错误代码: %d', ErrStatus);
    end
    
    fprintf(' 雷达连接成功\n');
    
catch ME
    error('雷达初始化失败: %s\n请确保:\n1. mmWave Studio已启动（在Lua shell中执行: RSTD.NetStart()）\n2. 雷达设备已正确连接', ME.message);
end

%% ==================== 系统准备完毕 ====================
fprintf('\n========================================\n');
fprintf('  所有系统初始化完成！\n');
fprintf('========================================\n');
fprintf('  [已验证] 数据根目录\n');
fprintf('  [已加载] 场景配置 (%d 个场景)\n', total_scenes);
fprintf('  [已连接] AudioCenterServer\n');
fprintf('  [已连接] 手机设备 (%s)\n', strjoin(devices, ', '));
fprintf('  [已连接] 雷达设备\n');
fprintf('  [已完成] 时间同步\n');
fprintf('========================================\n\n');

%% ==================== 用户输入与ID映射 ====================
fprintf('========== 用户信息确认 ==========\n');

% 输入人员组合
staff_combo = input('请输入人员组合（例如 yh-ssk）: ', 's');
if isempty(staff_combo)
    error('人员组合不能为空');
end

% --- ID 映射逻辑 ---
mapping_file = fullfile(data_root_path, 'subject_mapping.txt');
subject_id = -1;

% 读取现有映射
if exist(mapping_file, 'file')
    fid = fopen(mapping_file, 'r');
    lines = {};
    while ~feof(fid)
        line = fgetl(fid);
        if ischar(line)
            lines{end+1} = line;
        end
    end
    fclose(fid);
    
    % 查找现有ID
    for i = 1:length(lines)
        parts = strsplit(lines{i}, ':');
        if length(parts) == 2 && strcmp(strtrim(parts{1}), staff_combo)
            subject_id = str2double(parts{2});
            fprintf('  [已有ID] %s -> %d\n', staff_combo, subject_id);
            break;
        end
    end
    
    % 如果没找到，分配新ID
    if subject_id == -1
        % 获取最大ID
        max_id = 0;
        for i = 1:length(lines)
            parts = strsplit(lines{i}, ':');
            if length(parts) == 2
                id_val = str2double(parts{2});
                if id_val > max_id
                    max_id = id_val;
                end
            end
        end
        subject_id = max_id + 1;
        
        % 追加到映射文件
        fid = fopen(mapping_file, 'a');
        fprintf(fid, '%s:%d\n', staff_combo, subject_id);
        fclose(fid);
        fprintf('  [新分配ID] %s -> %d\n', staff_combo, subject_id);
    end
else
    % 文件不存在，创建第一个ID
    subject_id = 1;
    fid = fopen(mapping_file, 'w');
    fprintf(fid, '%s:%d\n', staff_combo, subject_id);
    fclose(fid);
    fprintf('  [新分配ID] %s -> %d\n', staff_combo, subject_id);
end

% 数据保存路径（直接使用根目录）
save_path = data_root_path;
fprintf('  数据ID: %d (%s)\n', subject_id, staff_combo);
fprintf('  数据保存路径: %s\n', save_path);

%% ==================== 初始化日志 ====================
fprintf('\n========== 初始化采集日志 ==========\n');

% 创建日志文件
log_filename = sprintf('capture_log_%d_%s.csv', subject_id, datestr(now, 'yyyymmdd_HHMMSS'));
log_filepath = fullfile(save_path, log_filename);

% 写入日志头
log_fid = fopen(log_filepath, 'w', 'n', 'UTF-8');
fprintf(log_fid, 'timestamp,scene_idx,scene_code,repeat_index,success,sntp_offset,rtt,error_message\n');
fclose(log_fid);
fprintf(' 日志文件: %s\n', log_filename);

%% ==================== 开始批量采集 ====================
fprintf('\n========================================\n');
fprintf('  开始批量采集\n');
fprintf('  总场景数: %d\n', total_scenes);
fprintf('  每场景重复: %d 次\n', repeat_count);
fprintf('  预计总采集次数: %d\n', total_scenes * repeat_count);
fprintf('========================================\n\n');

pause(2);  % 给操作员2秒准备时间

% 统计变量
total_captures = 0;
success_captures = 0;
failed_captures = 0;

% 双层循环：场景 × 重复次数
for scene_idx = 1:total_scenes
    scene = scene_list(scene_idx);
    
    fprintf('\n========================================\n');
    fprintf('场景 %d/%d\n', scene_idx, total_scenes);
    fprintf('========================================\n');
    fprintf('描述: %s\n', scene.intro);
    fprintf('代码: %s\n', scene.code);
    fprintf('========================================\n\n');
    
    for repeat_idx = 1:repeat_count
        fprintf('\n---------- 第 %d/%d 次采集 ----------\n', repeat_idx, repeat_count);
        
        % 等待用户确认
        while true
            response = input('输入 y 开始采集，输入 s 跳过: ', 's');
            if strcmpi(response, 'y')
                break;
            elseif strcmpi(response, 's')
                fprintf('已跳过此次采集\n');
                % 记录跳过到日志
                log_fid = fopen(log_filepath, 'a', 'n', 'UTF-8');
                fprintf(log_fid, '%s,%d,%s,%d,false,0,0,skipped by user\n', ...
                    datestr(now, 'yyyy-mm-dd HH:MM:SS'), scene.idx, scene.code, repeat_idx);
                fclose(log_fid);
                break;
            else
                fprintf('无效输入，请输入 y 或 s\n');
            end
        end
        
        if strcmpi(response, 's')
            continue;  % 跳过此次采集
        end
        
        % 构建场景ID（使用数字ID）
        sceneId = sprintf('%d-%s-%02d', subject_id, scene.code, repeat_idx);
        fprintf('\n场景ID: %s\n', sceneId);
        
        try
            % 执行同步采集
            fprintf('\n开始同步采集...\n');
            [success, metadata] = syncCapture(audioClient, [], sceneId, ...
                capture_duration, RADAR_STARTUP_DELAY, PHONE_STARTUP_DELAY, save_path);
            
            total_captures = total_captures + 1;
            
            if success
                success_captures = success_captures + 1;
                fprintf('\n>>> 采集成功 <<<\n');
                
                % 保存元数据
                saveMetadata(metadata, scene, staff_combo, subject_id, save_path, repeat_idx);
                
                % --- 自动移动音频文件 (.wav) ---
                try
                    % 定位服务器音频目录 (假设相对于当前 matlab_client 目录)
                    audio_server_dir = fullfile('..', 'AudioCenterServer', 'audio');
                    target_wav = [sceneId, '.wav'];
                    wav_moved = false;
                    
                    % 轮询查找文件 (等待上传完成，最多5秒)
                    for attempt = 1:5
                        % 递归搜索 (** 支持子文件夹)
                        found_wavs = dir(fullfile(audio_server_dir, '**', target_wav));
                        
                        if ~isempty(found_wavs)
                            for fw = 1:length(found_wavs)
                                src_f = fullfile(found_wavs(fw).folder, found_wavs(fw).name);
                                dest_f = fullfile(save_path, found_wavs(fw).name);
                                movefile(src_f, dest_f);
                                fprintf('  [文件] 已归档音频: %s\n', found_wavs(fw).name);
                            end
                            wav_moved = true;
                            break;
                        else
                            if attempt < 5
                                pause(1); % 未找到，等待1秒重试
                            end
                        end
                    end
                    
                    if ~wav_moved
                        fprintf('  [提示] 未自动归档音频 (可能仍在上传，请检查 AudioCenterServer/audio)\n');
                    end
                catch ME_Move
                    warning('  [警告] 移动音频文件出错: %s', ME_Move.message);
                end
                % ------------------------------------

                % 记录成功到日志
                log_fid = fopen(log_filepath, 'a', 'n', 'UTF-8');
                fprintf(log_fid, '%s,%d,%s,%d,true,%.2f,%.2f,\n', ...
                    datestr(now, 'yyyy-mm-dd HH:MM:SS'), scene.idx, scene.code, repeat_idx, ...
                    metadata.sntp_offset_ms, metadata.rtt_ms);
                fclose(log_fid);
            else
                failed_captures = failed_captures + 1;
                fprintf('\n>>> 采集失败：%s <<<\n', metadata.success_status);
                
                % 记录失败到日志
                log_fid = fopen(log_filepath, 'a', 'n', 'UTF-8');
                fprintf(log_fid, '%s,%d,%s,%d,false,%.2f,%.2f,%s\n', ...
                    datestr(now, 'yyyy-mm-dd HH:MM:SS'), scene.idx, scene.code, repeat_idx, ...
                    metadata.sntp_offset_ms, metadata.rtt_ms, metadata.success_status);
                fclose(log_fid);
            end
            
        catch ME
            failed_captures = failed_captures + 1;
            fprintf('\n>>> 采集异常：%s <<<\n', ME.message);
            
            % 记录异常到日志
            log_fid = fopen(log_filepath, 'a', 'n', 'UTF-8');
            fprintf(log_fid, '%s,%d,%s,%d,false,0,0,%s\n', ...
                datestr(now, 'yyyy-mm-dd HH:MM:SS'), scene.idx, scene.code, repeat_idx, ...
                strrep(ME.message, ',', ';'));  % 替换逗号避免CSV格式错误
            fclose(log_fid);
        end
        
        % 显示统计
        fprintf('\n当前统计: 成功 %d / 失败 %d / 总计 %d\n', ...
            success_captures, failed_captures, total_captures);
        
        % 短暂休息
        if repeat_idx < repeat_count
            fprintf('\n准备下一次采集...\n');
            pause(1);
        end
    end
    
    % 场景间休息
    if scene_idx < total_scenes
        fprintf('\n========================================\n');
        fprintf('场景 %d 完成，准备下一场景...\n', scene_idx);
        fprintf('========================================\n');
        pause(2);
    end
end

%% ==================== 采集完成 ====================
fprintf('\n\n========================================\n');
fprintf('  批量采集完成！\n');
fprintf('========================================\n');
fprintf('总采集次数: %d\n', total_captures);
fprintf('成功次数: %d\n', success_captures);
fprintf('失败次数: %d\n', failed_captures);
fprintf('成功率: %.1f%%\n', 100 * success_captures / max(total_captures, 1));
fprintf('========================================\n');
fprintf('数据保存位置: %s\n', save_path);
fprintf('日志文件: %s\n', log_filename);
fprintf('========================================\n\n');


fprintf('采集任务结束！\n\n');
