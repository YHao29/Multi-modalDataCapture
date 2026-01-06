clear;
close all;

%% ==================== 多模态数据采集系统 ====================
% 集成毫米波雷达和超声波数据采集
% 支持时间同步和自动化批量采集

%% ==================== 1. 配置参数 ====================
% 数据存储路径
data_path = 'D:\\multimodal_data\\';
dir_name = 'office\\';
adc_path = [data_path dir_name];
subject_log_file = [data_path dir_name 'subject_log.txt'];

% 采集参数
start_scene = 1;        % 开始场景编号
repeat_time = 3;        % 每个场景重复采集次数
capture_duration = 10;  % 采集时长（秒）

% 服务器配置（REST API）
server_ip = '127.0.0.1';      % AudioCenterServer 的 IP 地址（本机或修改为实际IP）
server_port = 8080;           % REST API 端口（默认8080）
% 注意：手机需要连接到 server_ip:6666（Netty服务器端口）

% 雷达配置
RSTD_DLL_Path = 'C:\\ti\\mmwave_studio_02_01_01_00\\mmWaveStudio\\Clients\\RtttNetClientController\\RtttNetClientAPI.dll';

% 创建数据目录
if ~exist(adc_path, 'dir')
    mkdir(adc_path);
    fprintf('创建数据目录: %s\n', adc_path);
end

%% ==================== 2. 初始化 AudioClient ====================
fprintf('\n========== 初始化音频客户端 ==========\n');
try
    % 添加 matlab_client 到路径
    addpath('../matlab_client');
    
    % 创建音频客户端
    audioClient = AudioClient(server_ip, server_port);
    
    % 检查设备连接
    devices = audioClient.listDevices();
    if isempty(devices)
        error('没有检测到已连接的手机设备，请先连接手机到 AudioCenterServer');
    end
    
    % 时间同步
    fprintf('\n执行时间同步...\n');
    time_offset = audioClient.syncTime();
    fprintf('时间同步完成，偏移量: %d ms\n', time_offset);
    
catch ME
    error('音频客户端初始化失败: %s\n请检查:\n1. AudioCenterServer 是否已启动\n2. 服务器IP地址是否正确\n3. 手机是否已连接', ME.message);
end

%% ==================== 3. 用户输入被试编号并记录 ====================
fprintf('\n========== 被试信息录入 ==========\n');
person = input('请输入被试的名称缩写（例如：S01）: ', 's');

% 读取已有编号，生成新编号
if exist(subject_log_file, 'file')
    fid_log = fopen(subject_log_file, 'r');
    lines = textscan(fid_log, '%s', 'Delimiter', '\n');
    fclose(fid_log);
    existing_lines = lines{1};
    if ~isempty(existing_lines)
        last_line = existing_lines{end};
        parts = strsplit(last_line, ':');
        last_id = str2double(parts{end});
        new_id = last_id + 1;
    else
        new_id = 1;
    end
else
    new_id = 1;
end

% 显示并确认
fprintf('检测到新编号为 %s:%d\n', person, new_id);
confirm = input('确认正确吗？(y/n): ', 's');
if ~strcmpi(confirm, 'y')
    error('用户取消操作。');
end

% 写入日志文件
fid_log = fopen(subject_log_file, 'a');
fprintf(fid_log, '%s:%d\n', person, new_id);
fclose(fid_log);

subject_id = num2str(new_id);
fprintf('被试编号确认: %s\n', subject_id);

%% ==================== 4. 加载场景列表 ====================
fprintf('\n========== 加载场景配置 ==========\n');
scenes_file = 'scenes_file.csv';
dataTable = readtable(scenes_file);
numScenes = height(dataTable);

scenes = cell(numScenes, 3);
for i = 1:numScenes
    scenes{i, 1} = dataTable.idx(i);
    scenes{i, 2} = char(dataTable.intro(i));
    scenes{i, 3} = char(dataTable.code(i));
end
fprintf('加载了 %d 个场景\n', numScenes);

%% ==================== 5. 连接雷达 ====================
fprintf('\n========== 连接毫米波雷达 ==========\n');
ErrStatus = Init_RSTD_Connection(RSTD_DLL_Path);

if (ErrStatus ~= 30000)
    error('雷达连接失败，错误代码: %d', ErrStatus);
else
    fprintf('雷达连接成功\n');
end

%% ==================== 6. 主采集循环 ====================
fprintf('\n========== 开始数据采集 ==========\n');
fprintf('将采集 %d 个场景，每个场景重复 %d 次\n', numScenes - start_scene + 1, repeat_time);
fprintf('每次采集时长: %d 秒\n\n', capture_duration);

total_captures = 0;
failed_captures = 0;

for scene_idx = start_scene:size(scenes, 1)
    scene_desc = scenes{scene_idx, 2};
    base_filename = scenes{scene_idx, 3};
    
    fprintf('\n========================================\n');
    fprintf('场景 %d/%d: %s\n', scene_idx, numScenes, scene_desc);
    fprintf('========================================\n');
    
    for rep = 1:repeat_time
        rep_str = sprintf('%02d', rep);
        full_filename = [subject_id '-' base_filename '-' rep_str];
        adc_file = [adc_path full_filename '.bin'];
        
        fprintf('\n[%d/%d] 准备采集: %s\n', rep, repeat_time, full_filename);
        
        % 等待用户确认
        confirm_start = 'n';
        while ~strcmpi(confirm_start, 'y')
            confirm_start = input('是否开始采集? (y/n): ', 's');
            if strcmpi(confirm_start, 'n')
                fprintf('等待用户确认...\n');
                pause(0.5);
            elseif ~strcmpi(confirm_start, 'y')
                fprintf('请输入 y 或 n\n');
            end
        end
        
        try
            % 获取统一时间戳
            timestamp = round(posixtime(datetime('now')) * 1000);
            
            fprintf('\n>>> 同步触发采集开始 <<<\n');
            fprintf('时间戳: %d\n', timestamp);
            
            % 1. 发送音频采集指令
            fprintf('  [1/3] 启动音频采集...\n');
            audio_success = audioClient.startRecording(full_filename, capture_duration, timestamp);
            
            if ~audio_success
                warning('音频采集启动失败');
            end
            
            pause(0.5);  % 短暂等待确保音频已准备
            
            % 2. 启动雷达采集
            fprintf('  [2/3] 启动雷达采集...\n');
            Lua_path_config = sprintf('ar1.CaptureCardConfig_StartRecord("%s", 1)', adc_file);
            RtttNetClientAPI.RtttNetClient.SendCommand(Lua_path_config);
            RtttNetClientAPI.RtttNetClient.SendCommand('RSTD.Sleep(1000)');
            RtttNetClientAPI.RtttNetClient.SendCommand('ar1.StartFrame()');
            
            % 3. 等待采集完成
            fprintf('  [3/3] 正在采集数据 (%d秒)...\n', capture_duration);
            
            % 显示进度
            for t = 1:capture_duration
                fprintf('    进度: %d/%d 秒\r', t, capture_duration);
                pause(1);
            end
            fprintf('\n');
            
            % 额外等待2秒确保数据传输完成
            fprintf('  等待数据传输完成...\n');
            pause(2);
            
            fprintf('>>> 采集完成: %s <<<\n', full_filename);
            total_captures = total_captures + 1;
            
        catch ME
            fprintf('!!! 采集失败: %s !!!\n', ME.message);
            failed_captures = failed_captures + 1;
            
            % 尝试停止音频录制
            try
                audioClient.stopRecording();
            catch
                % 忽略停止失败
            end
        end
        
        % 场景间短暂休息
        if rep < repeat_time
            fprintf('\n短暂休息 3 秒...\n');
            pause(3);
        end
    end
end

%% ==================== 7. 采集完成总结 ====================
fprintf('\n========================================\n');
fprintf('数据采集完成！\n');
fprintf('========================================\n');
fprintf('成功采集: %d 次\n', total_captures);
fprintf('失败次数: %d 次\n', failed_captures);
fprintf('数据保存在: %s\n', adc_path);
fprintf('\n请检查数据文件是否完整：\n');
fprintf('  - 雷达数据: *.bin 文件\n');
fprintf('  - 音频数据: *.wav 或 *.pcm 文件\n');
fprintf('========================================\n');

% 清理
clear audioClient;
