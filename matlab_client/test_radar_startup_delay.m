clear;
close all;
clc;

%% ==================== 雷达启动延迟测量脚本 ====================
% 文件名: test_radar_startup_delay.m
% 功能: 测量雷达从收到 StartFrame 命令到实际开始采集的延迟时间
% 用途: 为主控程序提供精确的 RADAR_STARTUP_DELAY 参数
% 
% !!! 重要说明 !!!
% 在正式采集前必须运行此脚本，将测量结果填入 main_multimodal_data_capture.m
% 的 RADAR_STARTUP_DELAY 配置参数中

fprintf('========================================\n');
fprintf('  雷达启动延迟测量工具\n');
fprintf('========================================\n\n');

%% ==================== 配置参数 ====================
% 雷达DLL路径
RSTD_DLL_Path = 'C:\ti\mmwave_studio_02_01_01_00\mmWaveStudio\Clients\RtttNetClientController\RtttNetClientAPI.dll';

% 测试数据保存路径（临时）
<<<<<<< HEAD
test_data_path = 'D:\temp_radar_test\';
=======
test_data_path = 'F:\temp_radar_test\';
>>>>>>> 10eacfa (完成毫米波雷达和手机端音频采集联调)

% 测试次数
test_iterations = 10;

% 测试采集时长（秒）- 短暂采集即可
test_duration = 2;

%% ==================== 参数验证 ====================
fprintf('========== 参数验证 ==========\n');

% 检查雷达DLL
if ~exist(RSTD_DLL_Path, 'file')
    error('雷达DLL文件不存在: %s\n请修改脚本中的 RSTD_DLL_Path 变量', RSTD_DLL_Path);
end
fprintf('  雷达DLL: 已找到\n');

% 创建临时测试目录
if ~exist(test_data_path, 'dir')
    mkdir(test_data_path);
    fprintf('  已创建测试目录: %s\n', test_data_path);
else
    fprintf('  测试目录: %s\n', test_data_path);
end

%% ==================== 初始化雷达连接 ====================
fprintf('\n========== 初始化雷达连接 ==========\n');

try
<<<<<<< HEAD
    % 加载雷达DLL
    if ~exist('RtttNetClientAPI.RtttNetClient', 'class')
        ErrStatus = Init_RSTD_Connection(RSTD_DLL_Path);
        if ErrStatus ~= 0
            error('雷达DLL加载失败，错误代码: %d', ErrStatus);
        end
    end
    fprintf('  雷达DLL已加载\n');
    
    % 连接雷达
    strRsp = RtttNetClientAPI.RtttNetClient.Init();
    if strcmp(strRsp, 'Init_Done')
        fprintf('  雷达连接成功\n');
    else
        error('雷达连接失败: %s', strRsp);
    end
    
    % 获取雷达API对象引用
    ar1 = RtttNetClientAPI.RtttNetClient;
    
catch ME
    error('雷达初始化失败: %s\n请确保:\n1. mmWave Studio已启动\n2. 雷达设备已正确连接', ME.message);
=======
    % 调用初始化函数加载DLL并建立连接
    ErrStatus = Init_RSTD_Connection(RSTD_DLL_Path);
    
    if (ErrStatus ~= 30000)
        error('雷达连接失败，错误代码: %d', ErrStatus);
    end
    
    fprintf('  雷达连接成功\n');
    
catch ME
    error('雷达初始化失败: %s\n请确保:\n1. mmWave Studio已启动（在Lua shell中执行: RSTD.NetStart()）\n2. 雷达设备已正确连接', ME.message);
>>>>>>> 10eacfa (完成毫米波雷达和手机端音频采集联调)
end

%% ==================== 执行延迟测量 ====================
fprintf('\n========================================\n');
fprintf('  开始测量雷达启动延迟\n');
fprintf('  测试次数: %d\n', test_iterations);
fprintf('========================================\n\n');

delays = zeros(test_iterations, 1);

for i = 1:test_iterations
    fprintf('---------- 测试 %d/%d ----------\n', i, test_iterations);
    
    try
        % 生成测试文件名
        test_filename = sprintf('test_delay_%02d.bin', i);
        test_filepath = fullfile(test_data_path, test_filename);
        
<<<<<<< HEAD
=======
        % 将路径转换为适合Lua的格式（反斜杠转义或用正斜杠）
        lua_filepath = strrep(test_filepath, '\', '\\');
        
>>>>>>> 10eacfa (完成毫米波雷达和手机端音频采集联调)
        % 删除旧文件（如果存在）
        if exist(test_filepath, 'file')
            delete(test_filepath);
        end
        
<<<<<<< HEAD
        % 配置雷达采集
        Lua_config = sprintf('ar1.CaptureCardConfig_StartRecord("%s", 1)', test_filepath);
        ar1.SendCommand(Lua_config);
        ar1.SendCommand('RSTD.Sleep(500)');
        
        % 记录命令发送时间
        t_cmd = posixtime(datetime('now')) * 1000;  % 毫秒
        fprintf('命令发送时间: %d ms\n', round(t_cmd));
        
        % 发送启动命令
        ar1.SendCommand('ar1.StartFrame()');
=======
        fprintf('生成测试文件: %s\n', test_filepath);
        fprintf('Lua命令路径: %s\n', lua_filepath);
        
        % 配置雷达采集
        Lua_config = sprintf('ar1.CaptureCardConfig_StartRecord("%s", 1)', lua_filepath);
        fprintf('发送命令: %s\n', Lua_config);
        RtttNetClientAPI.RtttNetClient.SendCommand(Lua_config);
        fprintf('已发送配置命令\n');
        
        % 等待1000ms确保雷达准备就绪
        RtttNetClientAPI.RtttNetClient.SendCommand('RSTD.Sleep(1000)');
        fprintf('等待雷达准备完成\n');
        
        % 记录命令发送时间
        t_cmd = posixtime(datetime('now')) * 1000;  % 毫秒
        fprintf('发送StartFrame命令，时间戳: %.0f ms\n', t_cmd);
        
        % 发送启动命令
        RtttNetClientAPI.RtttNetClient.SendCommand('ar1.StartFrame()');
        
        % 立即等待一下让文件有时间创建
        pause(0.1);
>>>>>>> 10eacfa (完成毫米波雷达和手机端音频采集联调)
        
        % 轮询等待文件创建
        file_created = false;
        max_wait_time = 5000;  % 最多等待5秒
<<<<<<< HEAD
        poll_interval = 10;    % 每10ms检查一次
        
        for wait_ms = 0:poll_interval:max_wait_time
            if exist(test_filepath, 'file')
                file_info = dir(test_filepath);
                if file_info.bytes > 0  % 文件有内容
                    t_file = posixtime(datetime('now')) * 1000;
                    delay_ms = t_file - t_cmd;
                    delays(i) = delay_ms;
                    fprintf('文件创建延迟: %.0f ms\n', delay_ms);
                    file_created = true;
=======
        poll_interval = 50;    % 每50ms检查一次
        waited_ms = 0;
        
        % 文件名前缀（雷达会自动添加_Raw_0.bin等后缀）
        filename_prefix = sprintf('test_delay_%02d', i);
        
        while waited_ms < max_wait_time
            % 列出目录中的所有文件
            if exist(test_data_path, 'dir')
                dir_contents = dir(test_data_path);
                % 查找匹配前缀的文件
                for f = 1:length(dir_contents)
                    fname = dir_contents(f).name;
                    % 检查文件是否以前缀开头且是Raw数据文件
                    if startsWith(fname, filename_prefix) && contains(fname, '_Raw_0.bin')
                        full_path = fullfile(test_data_path, fname);
                        file_info = dir(full_path);
                        if ~isempty(file_info) && file_info.bytes > 0
                            t_file = posixtime(datetime('now')) * 1000;
                            delay_ms = t_file - t_cmd;
                            delays(i) = delay_ms;
                            fprintf('检测到文件: %s，检测延迟: %.0f ms\n', fname, delay_ms);
                            file_created = true;
                            break;
                        end
                    end
                end
                if file_created
>>>>>>> 10eacfa (完成毫米波雷达和手机端音频采集联调)
                    break;
                end
            end
            pause(poll_interval / 1000);
<<<<<<< HEAD
        end
        
        if ~file_created
            warning('测试 %d: 超时未检测到文件创建', i);
=======
            waited_ms = waited_ms + poll_interval;
        end
        
        if ~file_created
            warning('测试 %d: 超时未检测到文件创建，检查文件路径: %s', i, test_filepath);
            % 尝试列出目录内容帮助调试
            if exist(test_data_path, 'dir')
                dir_contents = dir(test_data_path);
                fprintf('测试数据目录内容: ');
                for f = 1:length(dir_contents)
                    fprintf('%s ', dir_contents(f).name);
                end
                fprintf('\n');
            else
                fprintf('注意: 测试数据目录不存在或无法访问: %s\n', test_data_path);
            end
>>>>>>> 10eacfa (完成毫米波雷达和手机端音频采集联调)
            delays(i) = NaN;
        end
        
        % 等待采集完成
        pause(test_duration);
        
        % 停止采集
<<<<<<< HEAD
        ar1.SendCommand('ar1.StopFrame()');
=======
        RtttNetClientAPI.RtttNetClient.SendCommand('ar1.StopFrame()');
>>>>>>> 10eacfa (完成毫米波雷达和手机端音频采集联调)
        pause(0.5);
        
        fprintf('  测试 %d 完成\n\n', i);
        
    catch ME
        warning('测试 %d 失败: %s', i, ME.message);
        delays(i) = NaN;
    end
end

%% ==================== 数据分析 ====================
fprintf('\n========================================\n');
fprintf('  延迟测量结果\n');
fprintf('========================================\n\n');

% 移除无效数据
valid_delays = delays(~isnan(delays));

if isempty(valid_delays)
    error('所有测试均失败，无法计算延迟');
end

% 统计分析
mean_delay = mean(valid_delays);
median_delay = median(valid_delays);
std_delay = std(valid_delays);
min_delay = min(valid_delays);
max_delay = max(valid_delays);

fprintf('有效测试次数: %d / %d\n', length(valid_delays), test_iterations);
fprintf('\n延迟统计:\n');
fprintf('  平均值: %.2f ms\n', mean_delay);
fprintf('  中位数: %.2f ms\n', median_delay);
fprintf('  标准差: %.2f ms\n', std_delay);
fprintf('  最小值: %.2f ms\n', min_delay);
fprintf('  最大值: %.2f ms\n', max_delay);

% 推荐值（使用中位数向上取整到最近的50ms）
recommended_delay = ceil(median_delay / 50) * 50;
fprintf('\n========================================\n');
fprintf('【推荐配置值】\n');
fprintf('========================================\n');
fprintf('RADAR_STARTUP_DELAY = %d;  %% 毫秒\n', recommended_delay);
fprintf('========================================\n\n');

fprintf('请将上述配置值复制到 main_multimodal_data_capture.m\n');
fprintf('文件的用户配置区中的 RADAR_STARTUP_DELAY 变量\n\n');

%% ==================== 绘图 ====================
if length(valid_delays) > 1
    figure('Name', '雷达启动延迟测量结果', 'NumberTitle', 'off');
    
    subplot(2, 1, 1);
    plot(1:length(valid_delays), valid_delays, 'bo-', 'LineWidth', 1.5, 'MarkerSize', 8);
    hold on;
    yline(mean_delay, 'r--', sprintf('平均: %.2f ms', mean_delay), 'LineWidth', 1.5);
    yline(median_delay, 'g--', sprintf('中位数: %.2f ms', median_delay), 'LineWidth', 1.5);
    yline(recommended_delay, 'm--', sprintf('推荐: %d ms', recommended_delay), 'LineWidth', 2);
    grid on;
    xlabel('测试次数');
    ylabel('延迟 (ms)');
    title('雷达启动延迟测量结果');
    legend('测量值', '平均值', '中位数', '推荐值', 'Location', 'best');
    
    subplot(2, 1, 2);
    histogram(valid_delays, 10, 'FaceColor', [0.2 0.6 0.8], 'EdgeColor', 'k');
    hold on;
    xline(mean_delay, 'r--', sprintf('平均: %.2f', mean_delay), 'LineWidth', 1.5);
    xline(median_delay, 'g--', sprintf('中位数: %.2f', median_delay), 'LineWidth', 1.5);
    grid on;
    xlabel('延迟 (ms)');
    ylabel('频次');
    title('延迟分布直方图');
end

%% ==================== 清理 ====================
fprintf('\n========== 清理测试文件 ==========\n');
response = input('是否删除测试数据文件？(y/n): ', 's');
if strcmpi(response, 'y')
    try
        rmdir(test_data_path, 's');
        fprintf('  已删除测试目录\n');
    catch
        warning('无法删除测试目录，请手动删除: %s', test_data_path);
    end
else
    fprintf('测试文件保留在: %s\n', test_data_path);
end

fprintf('\n测量完成！\n\n');
