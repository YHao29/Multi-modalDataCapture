clear 
close all;

%% 雷达系统参数设置
B = 152.1e6;       % 调频带宽 (Hz)
K = 1.69e12;       % 调频斜率 (Hz/s)
Tc = 90e-6;        % chirp总周期 (s)
fs_ADC = 4e6;      % 采样率 (Hz)
numsample = 256;   % 每个chirp的采样点数
numframe = 200;    % 总帧数
TFrame = 0.025;    % 帧周期 (s)
numchirp = 128;    % 每帧包含的chirp数
n_RX = 1;          % 接收天线通道数

%% 系统常数和派生参数
c = 3.0e8;         % 光速 (m/s)
f0 = 77e9;         % 载频 (Hz)
lambda = c/f0;     % 雷达波长 (m)
d = lambda/2;      % 天线间距 (m)
T = B/K;           % 有效采样时间 (s)
NFFT = 2^nextpow2(numsample);  % 距离向FFT点数
M = 2^nextpow2(numchirp);      % 多普勒向FFT点数

%% 计算关键参数
duration = numframe*TFrame;    % 总采集时间 (s)
doppler_PRF = numchirp/TFrame; % 等效脉冲重复频率 (Hz)
Fupper = doppler_PRF/2;        % 奈奎斯特频率上限 (Hz)
Rangedf = c/2/B;               % 距离分辨率 (m)
lim_num = 10;                  % 距离门限索引

%% 数据加载和预处理
bin_pth = "D:\\mmwave_data\\office\\6-A1-B2-C0-D0-E0-02_Raw_0.bin";
raw_data = readDCA1000(bin_pth, numsample);

% 提取第一个接收天线的数据
data = reshape(raw_data(1,:), [numsample, numchirp*numframe]);

%% 距离向FFT处理
win = hamming(numsample);      % 汉明窗
all_profile = zeros(NFFT, numchirp*numframe);

% 对每个chirp进行距离向FFT
for i = 1:numchirp*numframe
    temp = data(:,i) .* win;
    all_profile(:,i) = fft(temp, NFFT);
end

% 目标检测：寻找最大能量点
absdata = abs(all_profile);
energydata = sum(absdata, 2);  % 沿chirp方向求和，得到距离向能量分布
[~, max_num] = max(energydata(1:lim_num));
disp(['最大能量索引: ', num2str(max_num)]);

%% 计算距离轴
range_axis = (0:NFFT-1) * c / (2 * B); % 距离轴 (m)

%% 1. 单个chirp的距离FFT频谱图
figure('Name', '单个Chirp距离FFT频谱', 'Position', [100, 100, 1200, 800]);
chirp_index = 1; % 选择第一个chirp
single_chirp_data = data(:, chirp_index);
single_chirp_fft = fft(single_chirp_data .* win, NFFT);

subplot(2,2,1);
plot(range_axis(1:100), 20*log10(abs(single_chirp_fft(1:100)) + eps));
title('距离FFT幅度谱 (dB) - 距离域');
xlabel('距离 (m)'); ylabel('幅度 (dB)'); grid on;

subplot(2,2,2);
range_freq = (0:NFFT-1) * fs_ADC / NFFT; % 拍频轴
plot(range_freq(1:100), 20*log10(abs(single_chirp_fft(1:100)) + eps));
title('距离FFT幅度谱 (dB) - 拍频域');
xlabel('拍频 (Hz)'); ylabel('幅度 (dB)'); grid on;

subplot(2,2,3);
plot(range_axis(1:100), real(single_chirp_fft(1:100)));
title('实部');
xlabel('距离 (m)'); ylabel('实部幅度'); grid on;

subplot(2,2,4);
plot(range_axis(1:100), imag(single_chirp_fft(1:100)));
title('虚部');
xlabel('距离 (m)'); ylabel('虚部幅度'); grid on;

%% 2. 所有chirp的平均距离谱
figure('Name', '平均距离FFT谱', 'Position', [150, 150, 1000, 600]);
mean_spectrum = mean(abs(all_profile), 2);
mean_spectrum_db = 20*log10(mean_spectrum + eps);

% 显示0-10m范围
range_limit = min(find(range_axis <= 10, 1, 'last'), length(range_axis));
plot(range_axis(1:range_limit), mean_spectrum_db(1:range_limit));
title('所有Chirp平均距离FFT谱 (0-10m)');
xlabel('距离 (m)'); ylabel('幅度 (dB)'); grid on;

%% 3. 指定距离bin的多普勒频谱
figure('Name', '多普勒频谱图', 'Position', [200, 200, 1000, 600]);
% 使用最大能量距离bin
doppler_data_at_max = all_profile(max_num, :);
doppler_spectrum = fft(doppler_data_at_max, M);
doppler_spectrum_db = 20*log10(abs(doppler_spectrum) + eps);
doppler_axis = (-M/2:M/2-1) * doppler_PRF / M; % 使用fftshift后的频率轴

subplot(2,1,1);
plot(doppler_axis, fftshift(doppler_spectrum_db));
title(sprintf('距离bin %d 处的多普勒频谱', max_num));
xlabel('多普勒频率 (Hz)'); ylabel('幅度 (dB)'); grid on;

subplot(2,1,2);
valid_idx = abs(doppler_axis) <= Fupper;
shifted_spectrum_db = fftshift(doppler_spectrum_db); % 先存储fftshift结果
plot(doppler_axis(valid_idx), shifted_spectrum_db(valid_idx));
title(sprintf('多普勒频谱 (|f| ≤ %.1f Hz)', Fupper));
xlabel('多普勒频率 (Hz)'); ylabel('幅度 (dB)'); grid on;

%% 4. 距离-时间热力图 (0-10m范围)
figure('Name', '距离-时间热力图 (0-10m)', 'Position', [250, 250, 1200, 700]);
range_power_db = 20*log10(abs(all_profile) + eps);

% 限制显示范围到0-10米
max_display_range = 10; % 显示最大距离10米
% 查找距离小于等于10m的最大索引
valid_range_idx = find(range_axis <= max_display_range, 1, 'last');
if isempty(valid_range_idx)
    valid_range_idx = length(range_axis); % 如果没有找到，使用全部
end

% 检查索引是否有效
valid_range_idx = min(valid_range_idx, size(range_power_db, 1));

% 提取0-10m范围的数据
display_data = range_power_db(1:valid_range_idx, :);
display_range_axis = range_axis(1:valid_range_idx);

% 绘制热力图
imagesc(1:size(all_profile,2), display_range_axis, display_data);
axis xy;
colorbar;
title('距离-时间热力图 (0-10m范围)');
xlabel('Chirp脉冲数');
ylabel('距离 (m)');
set(gca, 'YDir', 'normal');

% 设置颜色范围和刻度
caxis([0,80]); % 合理的颜色范围
yticks(0:1:10);   % 1米间隔的Y轴刻度
xtick_interval = round(size(all_profile,2)/10); % X轴自动刻度
xticks(0:xtick_interval:size(all_profile,2));

set(gca, 'FontSize', 10);
grid on;

%% 5. 3D频谱视图 (0-10m范围)
figure('Name', '3D频谱视图 (0-10m)', 'Position', [300, 300, 1000, 700]);
% 选择部分数据进行3D显示，限制在0-10m范围内
num_chirps_3d = min(50, size(all_profile,2));
range_limit_3d = find(range_axis <= 10, 1, 'last');
if isempty(range_limit_3d)
    range_limit_3d = size(all_profile, 1); % 如果没有找到，使用全部
end
range_limit_3d = min(range_limit_3d, size(all_profile, 1));

selected_data = all_profile(1:range_limit_3d, 1:num_chirps_3d);
selected_range_axis = range_axis(1:range_limit_3d);

% 生成网格并绘制
[X, Y] = meshgrid(1:num_chirps_3d, selected_range_axis);
Z = 20*log10(abs(selected_data) + eps);

surf(X, Y, Z);
shading interp;
xlabel('Chirp索引');
ylabel('距离 (m)');
zlabel('幅度 (dB)');
title('3D频谱视图 (0-10m范围)');
colorbar;