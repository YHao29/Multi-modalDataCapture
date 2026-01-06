function [offset_ms, rtt_ms] = syncTimeNTP(server_ip, server_port, timeout)
% syncTimeNTP - 使用 SNTP 协议与服务器同步时间
% 
% 输入:
%   server_ip: SNTP 服务器 IP 地址
%   server_port: SNTP 服务器端口（默认1123）
%   timeout: 超时时间（秒，默认5）
%
% 输出:
%   offset_ms: 时间偏移（毫秒），正值表示本地时间快于服务器
%   rtt_ms: 往返时延（毫秒）
%
% 示例:
%   [offset, rtt] = syncTimeNTP('127.0.0.1', 1123, 5);

    if nargin < 2
        server_port = 1123;
    end
    if nargin < 3
        timeout = 5;
    end

    try
        % 使用 Java DatagramSocket 实现 UDP 通信（兼容所有 MATLAB 版本）
        socket = java.net.DatagramSocket();
        socket.setSoTimeout(timeout * 1000);  % 毫秒
        
        % 服务器地址
        server_addr = java.net.InetAddress.getByName(server_ip);
        
        % 构建 SNTP 请求包（48字节）
        request = zeros(48, 1, 'uint8');
        
        % NTP 头部：LI=0, VN=4, Mode=3 (Client)
        request(1) = uint8(hex2dec('23'));
        
        % 记录发送时间（T1）- 使用 UTC 时间
        t1 = posixtime(datetime('now', 'TimeZone', 'UTC')) * 1000;  % 毫秒
        
        % 将发送时间写入请求包（字节 40-47）
        ntp_timestamp = javaTimeToNtp(t1);
        request(41:44) = typecast(uint32(ntp_timestamp(1)), 'uint8');
        request(45:48) = typecast(uint32(ntp_timestamp(2)), 'uint8');
        
        % 创建发送数据包
        send_packet = java.net.DatagramPacket(int8(request), length(request), ...
                                               server_addr, server_port);
        
        % 发送请求
        socket.send(send_packet);
        
        % 创建接收缓冲区
        receive_buffer = zeros(48, 1, 'int8');
        receive_packet = java.net.DatagramPacket(receive_buffer, 48);
        
        % 接收响应
        socket.receive(receive_packet);
        
        % 记录接收时间（T4）- 使用 UTC 时间
        t4 = posixtime(datetime('now', 'TimeZone', 'UTC')) * 1000;  % 毫秒
        
        % 提取响应数据
        response = typecast(receive_packet.getData(), 'uint8');
        response = response(1:48);
        
        % 解析响应包
        % 接收时间戳（字节 33-40）
        t2_ntp = ntpToJavaTime(response(33:40));
        
        % 发送时间戳（字节 41-48）
        t3_ntp = ntpToJavaTime(response(41:48));
        
        % 计算时间偏移和往返时延
        % offset = ((t2 - t1) + (t3 - t4)) / 2
        % rtt = (t4 - t1) - (t3 - t2)
        offset_ms = ((t2_ntp - t1) + (t3_ntp - t4)) / 2;
        rtt_ms = (t4 - t1) - (t3_ntp - t2_ntp);
        
        % 关闭 socket
        socket.close();
        
    catch ME
        error('SNTP同步失败: %s', ME.message);
    end
end

function ntp_time = javaTimeToNtp(java_ms)
    % 将 Java 时间戳（从1970开始）转换为 NTP 时间戳（从1900开始）
    % 差值：2208988800 秒
    
    java_seconds = floor(java_ms / 1000);
    java_fraction = mod(java_ms, 1000);
    
    ntp_seconds = java_seconds + 2208988800;
    ntp_fraction = floor((java_fraction * 2^32) / 1000);
    
    ntp_time = [ntp_seconds, ntp_fraction];
end

function java_ms = ntpToJavaTime(ntp_bytes)
    % 将 NTP 时间戳字节数组转换为 Java 时间戳（毫秒）
    
    % 读取秒数（大端序）
    ntp_seconds = double(typecast(uint8(ntp_bytes(1:4)), 'uint32'));
    ntp_seconds = swapbytes(uint32(ntp_seconds));
    
    % 读取小数部分（大端序）
    ntp_fraction = double(typecast(uint8(ntp_bytes(5:8)), 'uint32'));
    ntp_fraction = swapbytes(uint32(ntp_fraction));
    
    % 转换为 Java 时间戳
    java_seconds = double(ntp_seconds) - 2208988800;
    java_ms_fraction = floor((double(ntp_fraction) * 1000) / 2^32);
    
    java_ms = (java_seconds * 1000) + java_ms_fraction;
end
