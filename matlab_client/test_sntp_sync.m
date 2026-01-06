% 测试 SNTP 时间同步功能
clear;
close all;

fprintf('========================================\n');
fprintf('SNTP 时间同步测试\n');
fprintf('========================================\n\n');

%% 配置
server_ip = input('请输入服务器IP地址（默认: 127.0.0.1）: ', 's');
if isempty(server_ip)
    server_ip = '127.0.0.1';
end

server_port = 1123;  % SNTP 服务器端口
test_count = 5;       % 测试次数

%% 添加路径
current_dir = fileparts(mfilename('fullpath'));
addpath(current_dir);

%% 执行多次测试
fprintf('执行 %d 次 SNTP 同步测试...\n\n', test_count);

offsets = zeros(test_count, 1);
rtts = zeros(test_count, 1);

for i = 1:test_count
    fprintf('[测试 %d/%d] ', i, test_count);
    
    try
        [offset, rtt] = syncTimeNTP(server_ip, server_port, 5);
        offsets(i) = offset;
        rtts(i) = rtt;
        
        fprintf('偏移: %+.2f ms, RTT: %.2f ms\n', offset, rtt);
        
        pause(0.5);  % 短暂延迟
        
    catch ME
        fprintf('失败: %s\n', ME.message);
        offsets(i) = NaN;
        rtts(i) = NaN;
    end
end

%% 统计分析
valid_offsets = offsets(~isnan(offsets));
valid_rtts = rtts(~isnan(rtts));

fprintf('\n========================================\n');
fprintf('测试结果统计\n');
fprintf('========================================\n');
fprintf('成功次数: %d/%d\n', length(valid_offsets), test_count);

if ~isempty(valid_offsets)
    fprintf('\n时间偏移统计:\n');
    fprintf('  平均值: %+.2f ms\n', mean(valid_offsets));
    fprintf('  中位数: %+.2f ms\n', median(valid_offsets));
    fprintf('  标准差: %.2f ms\n', std(valid_offsets));
    fprintf('  最小值: %+.2f ms\n', min(valid_offsets));
    fprintf('  最大值: %+.2f ms\n', max(valid_offsets));
    
    fprintf('\n往返时延 (RTT) 统计:\n');
    fprintf('  平均值: %.2f ms\n', mean(valid_rtts));
    fprintf('  中位数: %.2f ms\n', median(valid_rtts));
    fprintf('  标准差: %.2f ms\n', std(valid_rtts));
    fprintf('  最小值: %.2f ms\n', min(valid_rtts));
    fprintf('  最大值: %.2f ms\n', max(valid_rtts));
    
    fprintf('\n同步质量评估:\n');
    avg_offset = abs(mean(valid_offsets));
    avg_rtt = mean(valid_rtts);
    
    if avg_offset < 10 && avg_rtt < 50
        fprintf('  等级: 优秀 (偏移<10ms, RTT<50ms)\n');
    elseif avg_offset < 50 && avg_rtt < 100
        fprintf('  等级: 良好 (偏移<50ms, RTT<100ms)\n');
    elseif avg_offset < 100 && avg_rtt < 200
        fprintf('  等级: 一般 (偏移<100ms, RTT<200ms)\n');
    else
        fprintf('  等级: 较差 (需要改善网络条件)\n');
    end
    
    % 绘制结果
    if test_count > 1
        figure('Name', 'SNTP同步测试结果');
        
        subplot(2,1,1);
        plot(1:length(valid_offsets), valid_offsets, '-o', 'LineWidth', 2);
        grid on;
        xlabel('测试次数');
        ylabel('时间偏移 (ms)');
        title('时间偏移趋势');
        yline(0, '--r', '零偏移');
        
        subplot(2,1,2);
        plot(1:length(valid_rtts), valid_rtts, '-o', 'LineWidth', 2, 'Color', [0.8500 0.3250 0.0980]);
        grid on;
        xlabel('测试次数');
        ylabel('往返时延 (ms)');
        title('网络延迟趋势');
    end
else
    fprintf('\n所有测试失败，请检查:\n');
    fprintf('  1. AudioCenterServer 是否已启动\n');
    fprintf('  2. SNTP 服务器是否运行在 UDP 端口 1123\n');
    fprintf('  3. 防火墙是否阻止了 UDP 通信\n');
end

fprintf('\n========================================\n');
fprintf('测试完成\n');
fprintf('========================================\n');
