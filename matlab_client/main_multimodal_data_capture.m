clear;
close all;
clc;

%% ==================== 多模态数据采集主控程序 ====================
% 文件名: main_multimodal_data_capture.m
% 功能: 批量采集毫米波雷达和超声波数据，实现精确时间同步


%% ==================== 用户配置区 ====================
% !!! 请在采集前仔细配置以下参数 !!!

% 【必填】数据存储根目录（采集员需预先手动创建好路径）
data_root_path = 'D:\data';

% 【必填】采集时长（秒）
capture_duration = 7;

% 每个场景重复采集次数
repeat_count = 3;

% 雷达启动延迟（毫秒）
RADAR_STARTUP_DELAY = 500;  % 默认1000ms

% 手机音频启动延迟（毫秒）
PHONE_STARTUP_DELAY = 2200;  % 默认2200ms

% 【可选】采集开始时间偏移控制（毫秒）
% 正值表示延后开始，负值表示提前开始
% 例如：AUDIO_START_OFFSET = -5000 表示音频提前5秒开始采集
AUDIO_START_OFFSET = -1000;      % 默认0ms（与雷达同时开始）
RADAR_START_OFFSET = 1000;      % 默认0ms（与音频同时开始）

% 服务器配置
server_ip = '127.0.0.1';      % AudioCenterServer 的 IP 地址
server_port = 8080;           % REST API 端口

% 场景文件路径
scenes_csv_file = 'radar/scenes_file_v2.csv';

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
fprintf('  - 雷达启动延迟: %d 毫秒\n', RADAR_STARTUP_DELAY);
fprintf('  - 手机启动延迟: %d 毫秒\n', PHONE_STARTUP_DELAY);
fprintf('  - 音频偏移: %+d 毫秒 ', AUDIO_START_OFFSET);
if AUDIO_START_OFFSET < 0
    fprintf('(提前 %d ms)\n', abs(AUDIO_START_OFFSET));
elseif AUDIO_START_OFFSET > 0
    fprintf('(延后 %d ms)\n', AUDIO_START_OFFSET);
else
    fprintf('(与雷达同时)\n');
end
fprintf('  - 雷达偏移: %+d 毫秒 ', RADAR_START_OFFSET);
if RADAR_START_OFFSET < 0
    fprintf('(提前 %d ms)\n', abs(RADAR_START_OFFSET));
elseif RADAR_START_OFFSET > 0
    fprintf('(延后 %d ms)\n', RADAR_START_OFFSET);
else
    fprintf('(与音频同时)\n');
end

%% ==================== 加载三层场景配置 ====================
fprintf('\n========== 加载场景配置 ==========\n');

try
    % 使用新的工具函数加载三层场景配置
    [locations, subLocations, actionScenes] = loadHierarchicalScenes('_v2');
    
    fprintf(' ✓ 场景配置加载完成\n');
    fprintf('   - 大场景: %d 个\n', length(locations));
    fprintf('   - 子场景: %d 个\n', length(subLocations));
    fprintf('   - 动作组合: %d 个\n', length(actionScenes));
    
catch ME
    error('加载场景配置失败: %s', ME.message);
end

%% ==================== 场景选择交互 ====================
fprintf('\n========== 场景选择 ==========\n');

% 第1步：选择大场景
fprintf('\n第1步：选择大场景\n');
for i = 1:length(locations)
    fprintf('  [%d] %s - %s\n', i, locations(i).location_name, locations(i).description);
end

while true
    location_choice = input(sprintf('请输入大场景编号 (1-%d): ', length(locations)), 's');
    location_idx = str2double(location_choice);
    if ~isnan(location_idx) && location_idx >= 1 && location_idx <= length(locations)
        break;
    else
        fprintf('无效输入，请重新输入\n');
    end
end

selected_location = locations(location_idx);
fprintf('\n✓ 已选择大场景: %s\n', selected_location.location_name);

% 第2步：选择子场景（过滤属于所选大场景的子场景）
fprintf('\n第2步：选择子场景（%s）\n', selected_location.location_name);

% 过滤子场景
available_subLocations = subLocations([]); % 初始化为空结构体数组
sub_idx_mapping = [];
for i = 1:length(subLocations)
    if strcmp(subLocations(i).location_id, selected_location.location_id)
        available_subLocations(end+1) = subLocations(i);
        sub_idx_mapping(end+1) = i;
    end
end

if isempty(available_subLocations)
    error('所选大场景 %s 没有配置子场景，请检查 sub_locations.csv', selected_location.location_name);
end

for i = 1:length(available_subLocations)
    fprintf('  [%d] %s - %s\n', i, available_subLocations(i).sub_location_name, ...
        available_subLocations(i).description);
end

while true
    sub_choice = input(sprintf('请输入子场景编号 (1-%d): ', length(available_subLocations)), 's');
    sub_idx = str2double(sub_choice);
    if ~isnan(sub_idx) && sub_idx >= 1 && sub_idx <= length(available_subLocations)
        break;
    else
        fprintf('无效输入，请重新输入\n');
    end
end

selected_subLocation = available_subLocations(sub_idx);
fprintf('\n✓ 已选择子场景: %s\n', selected_subLocation.sub_location_name);

% 第3步：确认动作组合场景
fprintf('\n第3步：确认动作组合场景\n');
fprintf('========================================\n');
fprintf('采集配置:\n');
fprintf('  大场景: %s\n', selected_location.location_name);
fprintf('  子场景: %s\n', selected_subLocation.sub_location_name);
fprintf('  动作组合: 共 %d 个场景\n', length(actionScenes));
fprintf('========================================\n');

% 显示前5个动作组合作为示例
fprintf('\n动作组合场景示例:\n');
for i = 1:min(5, length(actionScenes))
    fprintf('  [%d] %s (%s)\n', actionScenes(i).idx, ...
        actionScenes(i).intro, actionScenes(i).code);
end
if length(actionScenes) > 5
    fprintf('  ... (共 %d 个)\n', length(actionScenes));
end

% 用户最终确认
fprintf('\n');
while true
    confirm = input('确认开始采集？(y/n): ', 's');
    if strcmpi(confirm, 'y')
        break;
    elseif strcmpi(confirm, 'n')
        error('用户取消采集');
    else
        fprintf('无效输入，请输入 y 或 n\n');
    end
end

% 将动作组合赋值给scene_list（保持后续代码兼容）
scene_list = actionScenes;
total_scenes = length(scene_list);

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

% 数据保存路径（层次化目录结构）
subject_dir = fullfile(data_root_path, 'subjects', sprintf('subject_%03d', subject_id));
radar_dir = fullfile(subject_dir, 'radar');
audio_dir = fullfile(subject_dir, 'audio');

% 创建必要的目录
if ~exist(subject_dir, 'dir')
    mkdir(subject_dir);
    fprintf('  [创建] 被试目录: %s\n', subject_dir);
end
if ~exist(radar_dir, 'dir')
    mkdir(radar_dir);
    fprintf('  [创建] 雷达数据目录\n');
end
if ~exist(audio_dir, 'dir')
    mkdir(audio_dir);
    fprintf('  [创建] 音频数据目录\n');
end

save_path = subject_dir;  % 保存路径指向被试目录
fprintf('  数据ID: %d (%s)\n', subject_id, staff_combo);
fprintf('  数据保存路径: %s\n', save_path);

%% ==================== 初始化日志 ====================
fprintf('\n========== 初始化采集日志 ==========\n');

% 创建日志文件（保存在被试目录下）
log_filename = sprintf('capture_log_%d_%s.csv', subject_id, datestr(now, 'yyyymmdd_HHMMSS'));
log_filepath = fullfile(save_path, log_filename);

% 写入日志头（新增location和sub_location列）
log_fid = fopen(log_filepath, 'w', 'n', 'UTF-8');
fprintf(log_fid, 'timestamp,location_id,location_name,sub_location_id,sub_location_name,scene_idx,scene_code,repeat_index,success,sntp_offset,rtt,error_message\n');
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
                % 记录跳过到日志（包含三层场景信息）
                log_fid = fopen(log_filepath, 'a', 'n', 'UTF-8');
                fprintf(log_fid, '%s,%s,%s,%s,%s,%d,%s,%d,false,0,0,skipped by user\n', ...
                    datestr(now, 'yyyy-mm-dd HH:MM:SS'), ...
                    selected_location.location_id, selected_location.location_name, ...
                    selected_subLocation.sub_location_id, selected_subLocation.sub_location_name, ...
                    scene.idx, scene.code, repeat_idx);
                fclose(log_fid);
                break;
            else
                fprintf('无效输入，请输入 y 或 s\n');
            end
        end
        
        if strcmpi(response, 's')
            continue;  % 跳过此次采集
        end
        
        % 构建场景ID（包含三层场景信息）
        % 格式: sample_{样本ID}_{LocationID}_{SubLocationID}_{ActionCode}
        sample_id = (scene_idx - 1) * repeat_count + repeat_idx;
        sceneId = sprintf('sample_%03d_%s_%s_%s', sample_id, ...
            selected_location.location_id, selected_subLocation.sub_location_id, scene.code);
        fprintf('\n场景ID: %s\n', sceneId);
        
        try
            % 执行同步采集（传递radar和audio子目录路径以及offset参数）
            fprintf('\n开始同步采集...\n');
            [success, metadata] = syncCapture(audioClient, [], sceneId, ...
                capture_duration, RADAR_STARTUP_DELAY, PHONE_STARTUP_DELAY, ...
                AUDIO_START_OFFSET, RADAR_START_OFFSET, radar_dir, audio_dir);
            
            total_captures = total_captures + 1;
            
            if success
                success_captures = success_captures + 1;
                fprintf('\n>>> 采集成功 <<<\n');
                
                % 保存元数据（传递三层场景信息）
                saveMetadata(metadata, scene, staff_combo, subject_id, save_path, repeat_idx, ...
                    selected_location, selected_subLocation, sample_id);
                
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
                                dest_f = fullfile(audio_dir, found_wavs(fw).name);  % 保存到audio子目录
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

                % 记录成功到日志（包含三层场景信息）
                log_fid = fopen(log_filepath, 'a', 'n', 'UTF-8');
                fprintf(log_fid, '%s,%s,%s,%s,%s,%d,%s,%d,true,%.2f,%.2f,\n', ...
                    datestr(now, 'yyyy-mm-dd HH:MM:SS'), ...
                    selected_location.location_id, selected_location.location_name, ...
                    selected_subLocation.sub_location_id, selected_subLocation.sub_location_name, ...
                    scene.idx, scene.code, repeat_idx, ...
                    metadata.sntp_offset_ms, metadata.rtt_ms);
                fclose(log_fid);
            else
                failed_captures = failed_captures + 1;
                fprintf('\n>>> 采集失败：%s <<<\n', metadata.success_status);
                
                % 记录失败到日志（包含三层场景信息）
                log_fid = fopen(log_filepath, 'a', 'n', 'UTF-8');
                fprintf(log_fid, '%s,%s,%s,%s,%s,%d,%s,%d,false,%.2f,%.2f,%s\n', ...
                    datestr(now, 'yyyy-mm-dd HH:MM:SS'), ...
                    selected_location.location_id, selected_location.location_name, ...
                    selected_subLocation.sub_location_id, selected_subLocation.sub_location_name, ...
                    scene.idx, scene.code, repeat_idx, ...
                    metadata.sntp_offset_ms, metadata.rtt_ms, metadata.success_status);
                fclose(log_fid);
            end
            
        catch ME
            failed_captures = failed_captures + 1;
            fprintf('\n>>> 采集异常：%s <<<\n', ME.message);
            
            % 记录异常到日志（包含三层场景信息）
            log_fid = fopen(log_filepath, 'a', 'n', 'UTF-8');
            fprintf(log_fid, '%s,%s,%s,%s,%s,%d,%s,%d,false,0,0,%s\n', ...
                datestr(now, 'yyyy-mm-dd HH:MM:SS'), ...
                selected_location.location_id, selected_location.location_name, ...
                selected_subLocation.sub_location_id, selected_subLocation.sub_location_name, ...
                scene.idx, scene.code, repeat_idx, ...
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
