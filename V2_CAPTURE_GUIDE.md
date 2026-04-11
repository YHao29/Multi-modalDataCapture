# 多模态采集系统 V2 数据采集指南

## 1. 目的

本文档用于指导 V2 版本的多模态数据采集流程。

V2 的核心变化是：

- 超声链路不再走旧版 `AudioClient.m`
- 改为接入已经验证通过的独立超声采集链路
- 仍然保留 V1 的 MATLAB 侧同步模型
- 在元数据中写入 `V2` 版本信息与超声参数

如果你只需要开始采集，优先看“启动顺序”和“实际操作步骤”两节。

## 2. 仓库与脚本位置

工作区根目录：

```text
E:\ScreenDataCapture
```

### 2.1 V2 多模态主脚本

- 主入口：
  - `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\main_multimodal_data_capture_v2.m`
- V2 同步函数：
  - `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\syncCaptureV2.m`
- V2 超声 REST 客户端：
  - `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\UltrasonicAudioClientV2.m`
- 元数据保存：
  - `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\saveMetadata.m`

### 2.2 场景配置文件

V2 使用三层场景配置，位置如下：

- 地点配置：
  - `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\radar\locations_v2.csv`
- 子地点配置：
  - `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\radar\sub_locations_v2.csv`
- 动作场景配置：
  - `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\radar\scenes_file_v2.csv`

场景加载函数：

- `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\utils\loadHierarchicalScenes.m`

### 2.3 雷达初始化脚本

- `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\radar\Init_RSTD_Connection.m`

### 2.4 独立超声链路位置

- 超声服务端工程：
  - `E:\ScreenDataCapture\Multimodal_data_capture\Ultrasound_capture\UltrasonicCenterServer`
- 超声 Android 客户端工程：
  - `E:\ScreenDataCapture\Multimodal_data_capture\Ultrasound_capture\UltrasonicCenterClient`
- 服务端音频落盘目录：
  - `E:\ScreenDataCapture\Multimodal_data_capture\Ultrasound_capture\UltrasonicCenterServer\audio`

### 2.5 V2 说明文档

- `E:\ScreenDataCapture\Multimodal_data_capture\V2_NOTES.md`

## 3. V2 的同步原则

V2 必须遵守 V1 的同步要求，即：

1. 由 MATLAB 统一决定采集时序。
2. 先做时钟同步，再计算统一的触发时间。
3. 使用设备启动延迟补偿来安排命令发送时刻。
4. 雷达与超声不是“谁先 ready 就先采”，而是按同一个基准时间对齐。

V2 中最关键的几个参数位于：

`E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\main_multimodal_data_capture_v2.m`

```matlab
RADAR_STARTUP_DELAY = 500;
PHONE_STARTUP_DELAY = 2200;

AUDIO_START_OFFSET = -1000;
RADAR_START_OFFSET = 1000;
```

其中：

- `RADAR_STARTUP_DELAY` 表示从 MATLAB 发命令到雷达真正进入目标采集状态的延迟估计
- `PHONE_STARTUP_DELAY` 表示从 MATLAB 发超声采集命令到手机端真正进入采集状态的延迟估计
- `AUDIO_START_OFFSET` 和 `RADAR_START_OFFSET` 表示相对统一基准触发时间的偏移

注意：

- `PHONE_STARTUP_DELAY` 必须把独立超声客户端的预提示音和录放初始化耗时算进去
- 如果 Android 客户端代码、机型或系统版本变化，建议重新标定该值

## 4. 启动前准备

开始采集前，请确认以下条件满足：

### 4.1 PC 侧

- Windows 主机已连接雷达设备
- 已安装 MATLAB
- 已安装 TI mmWave Studio
- `RtttNetClientAPI.dll` 路径有效
- 准备用于保存数据的磁盘目录，例如 `D:\data`

### 4.2 超声服务端

- Java 17 或以上版本可用
- `UltrasonicCenterServer` 可以正常启动
- REST API 默认端口为 `8080`

### 4.3 Android 手机侧

- 已安装 `UltrasonicCenterClient` 的 App
- 手机与 PC 网络互通
- 手机已连接到超声服务端
- 服务端能识别到唯一在线设备，或者你已经明确指定了 `device_id`

### 4.4 雷达侧

- mmWave Studio 可以正常连接雷达
- 雷达采集链路已在 mmWave Studio 中准备好
- MATLAB 可以调用 `Init_RSTD_Connection.m`

## 5. 启动顺序

建议严格按下面顺序启动：

1. 启动超声服务端
2. 启动 Android 超声客户端并连接服务端
3. 打开 mmWave Studio，确认雷达链路 ready
4. 打开 MATLAB
5. 检查并修改 V2 主脚本配置
6. 运行 `main_multimodal_data_capture_v2.m`

不要先运行 MATLAB 再临时排查服务端和手机客户端，否则容易造成设备未注册、时间同步失败或上传超时。

## 6. 具体操作方式

## 6.1 启动独立超声服务端

PowerShell 中执行：

```powershell
cd E:\ScreenDataCapture\Multimodal_data_capture\Ultrasound_capture\UltrasonicCenterServer
.\gradlew.bat bootRun
```

正常情况下，日志里应能看到类似信息：

```text
Netty server started successfully!
REST API available at http://localhost:8080/api
Started Main in ...
```

说明：

- Netty 设备通信端口为 `6666`
- REST API 端口为 `8080`
- 时间同步接口也由这个服务端提供

如果你只想先检查服务端是否正常，可以重点关注以下接口：

- `http://127.0.0.1:8080/api/devices/status`
- `http://127.0.0.1:8080/api/devices/list`
- `http://127.0.0.1:8080/api/time/sync`

## 6.2 启动 Android 超声客户端

Android 侧操作要求：

1. 打开手机上的 `UltrasonicCenterClient`
2. 填写 PC 的 IP 地址与端口
3. 连接到服务端
4. 确认设备成功注册

V2 MATLAB 端会通过以下 REST 接口自动检查设备：

- `/api/devices/status`
- `/api/devices/list`
- `/api/ultrasonic/capture/start`
- `/api/ultrasonic/capture/status`
- `/api/ultrasonic/capture/stop`

如果当前只有一台手机在线，V2 会自动选择该设备。

如果在线设备不止一台，请在 `main_multimodal_data_capture_v2.m` 中手动设置：

```matlab
ultrasonic_device_id = '';
```

将空字符串改为目标设备的真实 `device_id`。

## 6.3 准备雷达环境

确认以下文件路径有效：

```matlab
RSTD_DLL_Path = 'C:\ti\mmwave_studio_02_01_01_00\mmWaveStudio\Clients\RtttNetClientController\RtttNetClientAPI.dll';
```

如果你的 mmWave Studio 安装位置不同，需要先改这个路径。

另外请确保：

- 雷达已经上电
- mmWave Studio 已完成必要配置
- MATLAB 调用 `Init_RSTD_Connection.m` 时不会报错

## 6.4 修改 V2 主脚本配置

打开：

`E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\main_multimodal_data_capture_v2.m`

重点检查这些参数：

```matlab
data_root_path = 'D:\data';
capture_duration = 7;
repeat_count = 3;

RADAR_STARTUP_DELAY = 500;
PHONE_STARTUP_DELAY = 2200;

AUDIO_START_OFFSET = -1000;
RADAR_START_OFFSET = 1000;

server_ip = '127.0.0.1';
server_port = 8080;
scenes_version = '_v2';
```

各参数含义如下：

- `data_root_path`
  - 数据根目录
- `capture_duration`
  - 单次采集时长，单位秒
- `repeat_count`
  - 每个动作重复采集次数
- `RADAR_STARTUP_DELAY`
  - 雷达启动补偿
- `PHONE_STARTUP_DELAY`
  - 手机超声链路启动补偿
- `AUDIO_START_OFFSET`
  - 超声相对统一触发时间的偏移
- `RADAR_START_OFFSET`
  - 雷达相对统一触发时间的偏移
- `server_ip`
  - 超声服务端地址
- `server_port`
  - 超声服务端端口
- `scenes_version`
  - V2 场景配置版本，保持为 `'_v2'`

超声参数也在这个脚本里配置：

```matlab
ultrasonic_config = struct( ...
    'enabled', true, ...
    'mode', 'fmcw', ...
    'sampleRateHz', 48000, ...
    'startFreqHz', 20000.0, ...
    'endFreqHz', 22000.0, ...
    'chirpDurationMs', 40, ...
    'idleDurationMs', 0, ...
    'amplitude', 0.30, ...
    'windowType', 'hann', ...
    'repeat', true);
```

除非你已经重新验证过独立超声链路参数，否则不建议随意修改。

## 6.5 在 MATLAB 中运行 V2 采集脚本

建议在 MATLAB 中先切到脚本目录：

```matlab
cd('E:\ScreenDataCapture\Multimodal_data_capture\matlab_client')
main_multimodal_data_capture_v2
```

运行后会依次执行：

1. 加载 V2 场景配置
2. 选择地点
3. 选择子地点
4. 连接超声服务端
5. 自动解析在线手机设备
6. 进行一次时间同步
7. 初始化雷达
8. 输入采集人员组合标识
9. 为每个动作循环执行采集

## 6.6 MATLAB 运行过程中的交互

### 选择地点

脚本会显示地点列表，让你输入序号。

### 选择子地点

脚本会显示当前地点下的子地点列表，让你输入序号。

### 确认采集计划

脚本会显示：

- Location
- Sub-location
- Action scenes

输入 `y` 才会继续。

### 输入采集人员组合

脚本会提示：

```text
Enter staff combination (for example yh-ssk):
```

这里建议填写实际执行采集的人员组合缩写，例如：

- `yh-ssk`
- `yh-lj`

这个字段会写入：

- 日志文件
- 元数据 JSON
- 被试编号映射文件

### 每一轮动作采集

每个动作的每一轮重复都会提示：

```text
Repeat x/y. Enter y=start, s=skip, q=quit:
```

输入规则：

- `y`
  - 开始当前轮采集
- `s`
  - 跳过当前轮
- `q`
  - 结束整个采集流程

## 7. 采集时系统内部做了什么

当你输入 `y` 开始某一轮采集时，V2 大致会执行以下动作：

1. 调用超声服务端时间同步接口
2. 在 MATLAB 中计算统一的基准触发时间
3. 计算超声命令发送时间和雷达命令发送时间
4. 在计划时刻发送超声采集命令
5. 在计划时刻发送雷达 `StartFrame`
6. 等待采集时长结束
7. 停止雷达
8. 轮询等待手机录音上传到 `Ultrasound_capture\UltrasonicCenterServer\audio`
9. 将上传完成的 WAV 拷贝到被试目录下的 `audio`
10. 校验雷达 `_Raw_0.bin` 文件
11. 保存日志和元数据

## 8. 采集结果保存位置

V2 的数据输出位于：

```text
<data_root_path>\subjects\subject_XXX\
```

例如：

```text
D:\data\subjects\subject_001\
```

目录结构通常如下：

```text
subject_001\
  audio\
  radar\
  capture_log_v2_001_YYYYMMDD_HHMMSS.csv
  sample_001_..._meta.json
  sample_002_..._meta.json
```

同时还会在数据根目录维护：

```text
<data_root_path>\subject_mapping.txt
```

该文件用于将 `staff_combo` 映射到 `subject_id`。

### 8.1 音频文件

音频最终保存到：

```text
<data_root_path>\subjects\subject_XXX\audio\
```

音频源文件先出现在：

```text
E:\ScreenDataCapture\Multimodal_data_capture\Ultrasound_capture\UltrasonicCenterServer\audio\
```

V2 会在上传完成后自动拷贝一份到被试目录。

### 8.2 雷达文件

雷达文件保存到：

```text
<data_root_path>\subjects\subject_XXX\radar\
```

V2 校验的是实际生成的：

```text
*_Raw_0.bin
```

### 8.3 元数据文件

元数据文件是 JSON，和 sample 对应，内容包含：

- 采集版本信息
- V2 超声参数
- 时间同步信息
- 地点与场景信息
- 音频文件映射
- 雷达文件映射

V2 元数据中会额外写入：

- `capture_system.version = "V2"`
- `capture_system.audio_chain_version = "V2"`
- `audio_server_relative_path`

## 9. 建议的单次采集流程

下面给出一个推荐操作流：

1. 启动 `UltrasonicCenterServer`
2. 打开手机 App 并连接
3. 在服务端确认设备在线
4. 打开 mmWave Studio 并让雷达 ready
5. 打开 MATLAB
6. 检查 `main_multimodal_data_capture_v2.m` 中的数据路径和延迟参数
7. 运行 `main_multimodal_data_capture_v2`
8. 选择地点和子地点
9. 输入 `staff_combo`
10. 每轮采集前确认被试和场景状态无误
11. 输入 `y` 开始采集
12. 采集完成后检查 WAV、BIN 和 JSON 是否齐全

## 10. 采集完成后的快速检查

每次正式采集后，建议至少检查以下内容：

### 10.1 检查日志文件

查看：

```text
<subject_dir>\capture_log_v2_*.csv
```

确认：

- `success` 列为 `true`
- `audio_file` 非空
- `radar_file` 非空
- `error_message` 为空

### 10.2 检查音频文件

确认以下目录中存在对应 WAV：

```text
<subject_dir>\audio\
```

### 10.3 检查雷达文件

确认以下目录中存在对应 `_Raw_0.bin`：

```text
<subject_dir>\radar\
```

### 10.4 检查元数据文件

打开对应的 `*_meta.json`，确认至少存在以下字段：

- `capture_system.version`
- `capture_system.audio_chain_version`
- `audio_params`
- `sync_quality`
- `file_mapping`

## 11. 常见问题与排查

### 11.1 MATLAB 报找不到数据根目录

检查：

```matlab
data_root_path = 'D:\data';
```

确保该目录真实存在。

### 11.2 MATLAB 报找不到雷达 DLL

检查：

```matlab
RSTD_DLL_Path = 'C:\ti\mmwave_studio_02_01_01_00\mmWaveStudio\Clients\RtttNetClientController\RtttNetClientAPI.dll';
```

如果 mmWave Studio 安装路径变化，必须同步修改。

### 11.3 提示时间偏移过大

脚本在初始化时会做一次时间同步。如果偏移超过 100 ms，会给出 warning。

建议：

- 确认手机和 PC 网络稳定
- 重新连接手机客户端
- 重启超声服务端后重试

### 11.4 提示没有唯一在线设备

说明当前在线设备数量不是 1 台。

解决方法：

- 关闭其他手机客户端
- 或在脚本中手动指定：

```matlab
ultrasonic_device_id = 'your_device_id';
```

### 11.5 出现 `audio_upload_timeout`

说明手机端录音完成后，服务端在规定时间内没有等到上传文件。

检查：

- 手机端是否仍然在线
- 服务端 `audio` 目录是否有新文件
- 网络是否中断
- 采集时长是否过长，需要增加超时时间

可以在主脚本中调整：

```matlab
captureOptions.upload_timeout_seconds = max(20, capture_duration + 10);
```

### 11.6 出现 `radar_file_missing_or_small`

说明雷达文件没有正常生成，或文件过小。

建议检查：

- mmWave Studio 是否真正处于 ready 状态
- 雷达采集脚本是否正确执行
- 雷达存储路径是否可写
- 当前场景是否在采集过程中被中断

## 12. 采集前建议固定不变的内容

如果你准备做一批正式数据，建议先固定下面内容，不要在同一批数据中途频繁修改：

- `capture_duration`
- `repeat_count`
- `RADAR_STARTUP_DELAY`
- `PHONE_STARTUP_DELAY`
- `AUDIO_START_OFFSET`
- `RADAR_START_OFFSET`
- `ultrasonic_config`
- 场景配置 CSV

否则后续做跨批次分析时，元数据虽可追踪，但采集条件会变得不一致。

## 13. 相关文件索引

### 采集入口

- `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\main_multimodal_data_capture_v2.m`

### 同步与设备控制

- `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\syncCaptureV2.m`
- `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\UltrasonicAudioClientV2.m`

### 元数据与场景配置

- `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\saveMetadata.m`
- `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\utils\loadHierarchicalScenes.m`
- `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\radar\locations_v2.csv`
- `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\radar\sub_locations_v2.csv`
- `E:\ScreenDataCapture\Multimodal_data_capture\matlab_client\radar\scenes_file_v2.csv`

### 独立超声链路

- `E:\ScreenDataCapture\Multimodal_data_capture\Ultrasound_capture\UltrasonicCenterServer`
- `E:\ScreenDataCapture\Multimodal_data_capture\Ultrasound_capture\UltrasonicCenterClient`

### V2 补充说明

- `E:\ScreenDataCapture\Multimodal_data_capture\V2_NOTES.md`
