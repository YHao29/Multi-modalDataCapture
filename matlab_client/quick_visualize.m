% quick_visualize.m - 快速可视化单条数据质量
%
% 用途：验证采集的雷达和音频数据质量，生成可视化图表
% 使用：修改下面的文件路径，然后运行脚本

clear;
close all;
clc;

%% ==================== 配置区 ====================
% 请修改为您的实际文件路径
radar_file = 'F:\testData\subjects\subject_001\radar\sample_001_L01_SL01_A0-B0-C0-D0-E0_Raw_0.bin';
audio_file = 'F:\testData\subjects\subject_001\audio\sample_001_L01_SL01_A0-B0-C0-D0-E0.wav';

% 雷达配置参数
num_samples = 256;   % ADC采样点数
num_chirps = 128;    % Chirp数量
num_rx = 4;          % 接收天线数量

%% ==================== 读取雷达数据 ====================
fprintf('========== 读取雷达数据 ==========\n');

if ~exist(radar_file, 'file')
    error('雷达文件不存在: %s', radar_file);
end

fid = fopen(radar_file, 'r');
data = fread(fid, 'int16');
fclose(fid);

fprintf('  文件大小: %.2f MB\n', length(data) * 2 / 1e6);
fprintf('  数据点数: %d\n', length(data));

% 重塑数据
expected_len = num_samples * num_chirps * num_rx;
if length(data) < expected_len
    warning('数据长度不足，期望 %d，实际 %d', expected_len, length(data));
    data = [data; zeros(expected_len - length(data), 1)];
end

adcData = reshape(data(1:expected_len), [num_samples, num_chirps, num_rx]);
fprintf('  数据形状: [%d, %d, %d] (samples, chirps, RX)\n', size(adcData));

%% ==================== 雷达数据可视化 ====================
fprintf('\n========== 雷达数据可视化 ==========\n');

figure('Name', '雷达数据分析', 'Position', [50, 50, 1400, 800]);

% 1. Range-Doppler Map (RX 0)
subplot(2, 4, 1);
rdMap = abs(fft2(double(adcData(:,:,1))));
rdMap_dB = 20*log10(rdMap + 1);
imagesc(rdMap_dB);
title('Range-Doppler Map (RX0)');
xlabel('Doppler Bin'); ylabel('Range Bin');
colorbar; colormap('jet');

% 2. Range Profile (所有chirp平均)
subplot(2, 4, 2);
rangeProfile = mean(abs(double(adcData(:,:,1))), 2);
plot(rangeProfile, 'LineWidth', 1.5);
title('Range Profile (Average)');
xlabel('Range Bin'); ylabel('Amplitude');
grid on;

% 3. 单个Range Bin的时间序列
subplot(2, 4, 3);
rangeBin = 50;  % 可调整
plot(squeeze(double(adcData(rangeBin,:,1))));
title(sprintf('Time Series (Range Bin %d)', rangeBin));
xlabel('Chirp Index'); ylabel('Amplitude');
grid on;

% 4. 各RX通道对比
subplot(2, 4, 4);
hold on;
for rx = 1:num_rx
    rp = mean(abs(double(adcData(:,:,rx))), 2);
    plot(rp, 'DisplayName', sprintf('RX%d', rx-1));
end
hold off;
title('Range Profile - All RX Channels');
xlabel('Range Bin'); ylabel('Amplitude');
legend; grid on;

% 5. ADC I/Q波形示例
subplot(2, 4, 5);
sample_chirp = double(adcData(:, 1, 1));
plot(1:num_samples, sample_chirp);
title('ADC Waveform (Chirp 1, RX0)');
xlabel('Sample'); ylabel('ADC Value');
grid on;

% 6. 信号功率时间序列
subplot(2, 4, 6);
powerSeries = squeeze(mean(abs(double(adcData)).^2, 1));
plot(powerSeries(:,1));
title('Signal Power Over Chirps');
xlabel('Chirp Index'); ylabel('Power');
grid on;

% 7. Range-Time Map
subplot(2, 4, 7);
rangeTime = abs(double(adcData(:,:,1)));
imagesc(20*log10(rangeTime + 1));
title('Range-Time Map');
xlabel('Chirp Index'); ylabel('Range Bin');
colorbar; colormap('jet');

% 8. 数据统计
subplot(2, 4, 8);
axis off;
stats_text = {
    '雷达数据统计:',
    sprintf('  最大值: %d', max(data)),
    sprintf('  最小值: %d', min(data)),
    sprintf('  均值: %.2f', mean(data)),
    sprintf('  标准差: %.2f', std(double(data))),
    '',
    '配置参数:',
    sprintf('  采样点数: %d', num_samples),
    sprintf('  Chirp数: %d', num_chirps),
    sprintf('  RX天线数: %d', num_rx),
    sprintf('  文件大小: %.2f MB', length(data) * 2 / 1e6)
};
text(0.1, 0.9, stats_text, 'FontSize', 10, 'VerticalAlignment', 'top');

fprintf('  ✓ 雷达数据可视化完成\n');

%% ==================== 读取音频数据 ====================
fprintf('\n========== 读取音频数据 ==========\n');

if ~exist(audio_file, 'file')
    warning('音频文件不存在: %s', audio_file);
    return;
end

[audioData, fs] = audioread(audio_file);
fprintf('  采样率: %d Hz\n', fs);
fprintf('  时长: %.2f 秒\n', length(audioData) / fs);
fprintf('  通道数: %d\n', size(audioData, 2));

% 转为单声道
if size(audioData, 2) > 1
    audioData = mean(audioData, 2);
end

%% ==================== 音频数据可视化 ====================
fprintf('\n========== 音频数据可视化 ==========\n');

figure('Name', '音频数据分析', 'Position', [100, 100, 1400, 800]);

% 1. 时域波形
subplot(3, 3, [1, 2]);
t = (0:length(audioData)-1) / fs;
plot(t, audioData);
title('Audio Waveform');
xlabel('Time (s)'); ylabel('Amplitude');
grid on;

% 2. 全频段频谱图
subplot(3, 3, [4, 5]);
spectrogram(audioData, 256, 250, 256, fs, 'yaxis');
title('Full Spectrum (0-22 kHz)');
ylim([0, 22]);

% 3. 超声波频段频谱图 (18-22 kHz)
subplot(3, 3, [7, 8]);
[S, F, T] = spectrogram(audioData, 256, 250, 256, fs);
freq_idx = F >= 18000 & F <= 22000;
imagesc(T, F(freq_idx)/1000, 20*log10(abs(S(freq_idx, :)) + eps));
title('Ultrasonic Spectrum (18-22 kHz)');
xlabel('Time (s)'); ylabel('Frequency (kHz)');
colorbar; colormap('jet');
axis xy;

% 4. FFT频谱 (全频段)
subplot(3, 3, 3);
L = length(audioData);
Y = fft(audioData);
P2 = abs(Y/L);
P1 = P2(1:L/2+1);
P1(2:end-1) = 2*P1(2:end-1);
f = fs*(0:(L/2))/L;
plot(f/1000, 20*log10(P1));
title('FFT Spectrum');
xlabel('Frequency (kHz)'); ylabel('Magnitude (dB)');
grid on; xlim([0, 22]);

% 5. 超声波频段FFT
subplot(3, 3, 6);
ultra_idx = f >= 18000 & f <= 22000;
plot(f(ultra_idx)/1000, 20*log10(P1(ultra_idx)));
title('Ultrasonic FFT (18-22 kHz)');
xlabel('Frequency (kHz)'); ylabel('Magnitude (dB)');
grid on;

% 6. 音频统计信息
subplot(3, 3, 9);
axis off;

% 计算超声波能量占比
total_energy = sum(abs(audioData).^2);
ultra_freq_range = (f >= 18000) & (f <= 22000);
ultra_energy = sum(abs(P1(ultra_freq_range)).^2);
ultra_ratio = ultra_energy / sum(abs(P1).^2);

% 削波检测
clipping_count = sum(abs(audioData) > 0.99);
clipping_ratio = clipping_count / length(audioData) * 100;

audio_stats = {
    '音频数据统计:',
    sprintf('  时长: %.2f 秒', length(audioData)/fs),
    sprintf('  采样率: %d Hz', fs),
    sprintf('  最大幅度: %.4f', max(abs(audioData))),
    sprintf('  RMS: %.4f', sqrt(mean(audioData.^2))),
    '',
    '超声波分析:',
    sprintf('  能量占比: %.2f%%', ultra_ratio * 100),
    sprintf('  峰值频率: %.1f kHz', f(find(P1 == max(P1(ultra_freq_range)), 1))/1000),
    '',
    '质量检查:',
    sprintf('  削波样本: %d (%.3f%%)', clipping_count, clipping_ratio)
};

if clipping_ratio > 1
    audio_stats{end+1} = '  ⚠ 警告: 检测到削波！';
end

text(0.1, 0.9, audio_stats, 'FontSize', 10, 'VerticalAlignment', 'top');

fprintf('  ✓ 音频数据可视化完成\n');
fprintf('\n========== 可视化完成 ==========\n');
fprintf('✓ 所有图表已生成\n');
fprintf('\n提示: 可以关闭图窗或保存图片\n');
