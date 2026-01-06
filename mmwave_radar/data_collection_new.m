clear;
close all;

%% 关键参数设置
data_path = 'D:\\mmwave_data\\';
dir_name = 'office\\';
start_scene = 1;  % 开始场景编号
repeat_time = 3;  % 每个场景重复采集次数
adc_path = [data_path dir_name];
subject_log_file = [data_path dir_name 'subject_log.txt'];

%% ==================== 1. 用户输入被试编号并记录 ====================
% 提示用户输入被试缩写
person = input('请输入被试的名称缩写（例如：S01）: ', 's');

% 读取已有编号，生成新编号
if exist(subject_log_file, 'file')
    fid_log = fopen(subject_log_file, 'r');
    lines = textscan(fid_log, '%s', 'Delimiter', '\n');
    fclose(fid_log);
    existing_lines = lines{1};
    if ~isempty(existing_lines)
        last_line = existing_lines{end};
        % 格式为 "S01:1"
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

% 最终使用的被试标识（用编号，避免重名）
subject_id = num2str(new_id);

%% ==================== 2. 预设场景列表 ====================
% 格式：{序号, 场景描述, 文件名}
filename = 'scenes_file.csv';
dataTable = readtable(filename);
numScenes = height(dataTable);

scenes = cell(numScenes, 3);

% 填充数据
for i = 1:numScenes
    scenes{i, 1} = dataTable.idx(i);        % 序号
    scenes{i, 2} = char(dataTable.intro(i));  % 描述
    scenes{i, 3} = char(dataTable.code(i));  % 代码
end

%% ==================== 3. 连接雷达 ====================
RSTD_DLL_Path = 'C:\\ti\\mmwave_studio_02_01_01_00\\mmWaveStudio\\Clients\\RtttNetClientController\\RtttNetClientAPI.dll';
ErrStatus = Init_RSTD_Connection(RSTD_DLL_Path);

if (ErrStatus ~= 30000)
    error('Error inside Init_RSTD_Connection');
else
    disp('Connect successfully');
end

%% ==================== 4. 主采集循环 ====================
for scene_idx = start_scene:size(scenes, 1)
    scene_desc = scenes{scene_idx, 2};
    base_filename = scenes{scene_idx, 3};
    
    fprintf('\n--- 开始采集场景 %d: %s ---\n', scene_idx, scene_desc);
    
    for rep = 1:repeat_time  % 每个场景重复若干次
        rep_str = sprintf('%02d', rep);
        full_filename = [subject_id '-' base_filename '-' rep_str];
        adc_file = [adc_path full_filename '.bin'];
        
        % 等待用户确认开始采集
        confirm_start = 'n';
        while ~strcmpi(confirm_start, 'y')
            confirm_start = input(sprintf('是否开始采集数据 %s? (y/n): ', full_filename), 's');
            if strcmpi(confirm_start, 'n')
                fprintf('等待用户确认...\n');
                pause(0.5);
            elseif ~strcmpi(confirm_start, 'y')
                fprintf('请输入 y 或 n \n');
            end
        end
        
        pause(1);

        % 发送雷达采集指令
        Lua_path_config = sprintf('ar1.CaptureCardConfig_StartRecord("%s", 1)', adc_file);
        RtttNetClientAPI.RtttNetClient.SendCommand(Lua_path_config);
        RtttNetClientAPI.RtttNetClient.SendCommand('RSTD.Sleep(1000)');
        RtttNetClientAPI.RtttNetClient.SendCommand('ar1.StartFrame()');

        pause(10); % 采集时间
    end
end

disp('所有场景采集完成！');