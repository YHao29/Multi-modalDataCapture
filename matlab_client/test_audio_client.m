% 测试脚本 - 验证 AudioClient 功能
% 在运行完整采集程序前，先测试各个组件是否正常工作

clear;
close all;

fprintf('========================================\n');
fprintf('AudioClient 功能测试\n');
fprintf('========================================\n\n');

%% 测试1: 连接服务器
fprintf('[测试1] 连接服务器测试\n');
fprintf('---------------------------------------\n');

% 请根据实际情况修改服务器IP
server_ip = input('请输入服务器IP地址（默认: 127.0.0.1）: ', 's');
if isempty(server_ip)
    server_ip = '127.0.0.1';
end

try
    % 添加路径
    current_dir = fileparts(mfilename('fullpath'));
    addpath(current_dir);
    
    % 创建客户端（REST API默认端口8080）
    client = AudioClient(server_ip);
    
    if client.connected
        fprintf('结果: 通过\n');
        fprintf('服务器连接成功\n\n');
    else
        error('服务器连接失败');
    end
catch ME
    fprintf('结果: 失败\n');
    fprintf('错误: %s\n\n', ME.message);
    fprintf('请检查:\n');
    fprintf('  1. AudioCenterServer 是否已启动\n');
    fprintf('  2. IP 地址是否正确\n');
    fprintf('  3. 端口 6666 是否开放\n');
    return;
end

%% 测试2: 获取设备列表
fprintf('[测试2] 获取设备列表\n');
fprintf('---------------------------------------\n');

try
    devices = client.listDevices();
    fprintf('结果: 通过\n');
    fprintf('设备数量: %d\n', length(devices));
    
    if isempty(devices)
        fprintf('警告: 没有检测到已连接的设备\n');
        fprintf('请确保手机已连接到 AudioCenterServer\n\n');
    else
        fprintf('已连接设备:\n');
        for i = 1:length(devices)
            fprintf('  %d. %s\n', i, devices{i});
        end
        fprintf('\n');
    end
catch ME
    fprintf('结果: 失败\n');
    fprintf('错误: %s\n\n', ME.message);
end

%% 测试3: 时间同步
fprintf('[测试3] 时间同步测试\n');
fprintf('---------------------------------------\n');

try
    offset = client.syncTime();
    fprintf('结果: 通过\n');
    fprintf('时间偏移: %d ms\n', offset);
    
    if abs(offset) > 100
        fprintf('警告: 时间偏移较大（>100ms）\n');
        fprintf('建议检查网络延迟\n');
    end
    fprintf('\n');
catch ME
    fprintf('结果: 失败\n');
    fprintf('错误: %s\n\n', ME.message);
end

%% 测试4: 录制状态查询
fprintf('[测试4] 获取录制状态\n');
fprintf('---------------------------------------\n');

try
    status = client.getRecordingStatus();
    fprintf('结果: 通过\n');
    
    if isfield(status, 'is_recording')
        if status.is_recording
            fprintf('当前状态: 录制中\n');
            fprintf('场景: %s\n', status.current_scene);
        else
            fprintf('当前状态: 未录制\n');
        end
    end
    fprintf('\n');
catch ME
    fprintf('结果: 失败\n');
    fprintf('错误: %s\n\n', ME.message);
end

%% 测试5: 录制功能测试
if ~isempty(devices)
    fprintf('[测试5] 录制功能测试\n');
    fprintf('---------------------------------------\n');
    
    confirm = input('是否进行录制测试？(y/n): ', 's');
    
    if strcmpi(confirm, 'y')
        test_scene = 'test-recording';
        test_duration = 5;
        
        fprintf('开始测试录制...\n');
        fprintf('场景ID: %s\n', test_scene);
        fprintf('时长: %d 秒\n', test_duration);
        
        try
            % 开始录制
            success = client.startRecording(test_scene, test_duration);
            
            if success
                fprintf('录制已启动\n');
                
                % 等待录制完成
                fprintf('等待录制完成...\n');
                for t = 1:test_duration
                    fprintf('  进度: %d/%d 秒\r', t, test_duration);
                    pause(1);
                end
                fprintf('\n');
                
                % 等待额外2秒
                pause(2);
                
                % 检查状态
                status = client.getRecordingStatus();
                fprintf('结果: 通过\n');
                fprintf('录制测试完成\n');
                fprintf('请检查手机端是否生成了数据文件\n\n');
            else
                fprintf('结果: 失败\n');
                fprintf('录制启动失败\n\n');
            end
            
        catch ME
            fprintf('结果: 失败\n');
            fprintf('错误: %s\n\n', ME.message);
        end
    else
        fprintf('跳过录制测试\n\n');
    end
else
    fprintf('[测试5] 录制功能测试\n');
    fprintf('---------------------------------------\n');
    fprintf('跳过: 没有已连接的设备\n\n');
end

%% 测试总结
fprintf('========================================\n');
fprintf('测试完成\n');
fprintf('========================================\n\n');

fprintf('下一步操作:\n');
fprintf('1. 如果所有测试通过，可以运行完整采集程序\n');
fprintf('2. 如果有测试失败，请根据错误提示进行排查\n');
fprintf('3. 确保手机设备已连接后再开始数据采集\n\n');

% 清理
clear client;
