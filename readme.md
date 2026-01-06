# 多模态数据采集系统

基于毫米波雷达和超声波的多模态数据采集系统，支持连续批量采集操作。

## 项目概述

本项目旨在构建一个高效的多模态数据采集系统，整合毫米波雷达和超声波两种传感模态，为数据采集操作员提供简单易用的指令接口，实现连续自动化的多段数据采集。

## 系统特性

- **多模态支持**：同时支持毫米波雷达和超声波数据采集
- **批量采集**：通过简单指令连续采集多段数据
- **操作简便**：为操作员设计的友好命令行界面
- **数据同步**：确保多模态数据的时间同步
- **自动化流程**：减少人工干预，提高采集效率

## 技术栈

- **Java**：AudioCenterServer 服务端，采用 Spring Boot + Netty 框架构建 Client-Server 架构
- **Android**：手机端超声波数据采集客户端应用
- **MATLAB**：毫米波雷达数据采集控制，场景管理和数据处理
- **NTP协议**：用于多模态数据的时间同步

## 系统架构

系统采用分布式采集架构，由三个核心组件构成：

```
┌─────────────────────────────────────────────────────────┐
│                    PC 控制中心 (MATLAB)                  │
│  - 场景管理 (scenes_file.csv)                           │
│  - 毫米波雷达控制 (data_collection_new.m)                │
│  - AudioCenterServer 客户端调用                          │
│  - 数据同步协调                                          │
└───────────────┬─────────────────────┬───────────────────┘
                │                     │
                │                     │
         ┌──────▼──────┐       ┌─────▼──────┐
         │  TI 毫米波   │       │   手机端    │
         │  雷达设备    │       │  Android App│
         │             │       │  (超声波)   │
         └─────────────┘       └────────────┘
```

### 工作流程

1. **初始化阶段**：MATLAB 程序读取场景配置文件，建立与雷达和 AudioCenterServer 的连接
2. **同步阶段**：通过 NTP 协议同步 PC 和手机端的系统时间
3. **采集阶段**：按场景顺序，同时触发雷达和手机端采集，确保时间戳对齐
4. **存储阶段**：数据按统一命名规则存储，便于后续多模态数据融合分析

## 硬件要求

### 必需设备

- **毫米波雷达**：TI mmWave 雷达开发板
- **Android 手机**：支持超声波采集的 Android 设备（需安装配套 App）
- **PC 主机**：运行 Windows 系统，用于控制和数据存储
  - 推荐配置：8GB+ 内存，100GB+ 可用存储空间
  - 需要良好的网络连接（用于与手机通信）

### 连接要求

- PC 与毫米波雷达通过 USB/Ethernet 连接
- PC 与 Android 手机需在同一局域网内（Wi-Fi 或 USB 网络共享）

## 软件依赖

### PC 端

- **MATLAB**：R2019b 或更高版本
  - 需要 Instrument Control Toolbox
- **TI mmWave Studio**：版本 02.01.01.00 或更高
- **Java Runtime Environment**：JDK 17 或更高版本
- **AudioCenterServer**：本项目提供的服务端程序

### Android 端

- Android 操作系统：5.0 (Lollipop) 或更高版本
- 配套的超声波采集 App（需单独安装）

### 可选工具

- NTP 服务器（用于高精度时间同步）

## 安装与配置

### 环境准备

#### 1. MATLAB 环境

```matlab
% 验证 MATLAB 安装
ver

% 检查必需的工具箱
license('test', 'Instrument_Control_Toolbox')
```

#### 2. TI mmWave Studio

1. 从 TI 官网下载并安装 mmWave Studio
2. 确认安装路径为：`C:\ti\mmwave_studio_02_01_01_00\`
3. 验证 RtttNetClientAPI.dll 存在

#### 3. AudioCenterServer

```bash
# 进入服务器目录
cd AudioCenterServer

# 构建项目
gradlew.bat clean build

# 启动服务器（默认监听 6666 端口）
gradlew.bat bootRun
```

#### 4. Android 客户端

1. 在手机上安装超声波采集 App
2. 配置服务器连接：输入 PC 的 IP 地址和端口 6666
3. 测试连接是否正常

### 设备连接

#### 毫米波雷达连接

1. 通过 USB 线将雷达开发板连接到 PC
2. 确保 DCA1000 电源已接通
3. 在 mmWave Studio 中验证连接状态

#### Android 手机连接

1. 确保手机和 PC 在同一局域网
2. 在 PC 上运行 `ipconfig` 查看 IP 地址
3. 在 App 中输入 PC IP 和端口（默认 6666）
4. 点击连接并验证状态

## 使用说明

### 快速开始

#### 第一步：启动服务

```bash
# 1. 启动 AudioCenterServer（会自动启动 Netty 和 SNTP 服务）
cd AudioCenterServer
gradlew.bat bootRun

# 2. 等待服务器启动完成，看到以下提示：
#    - Netty server started on port 6666
#    - SNTP server started on port 1123
#    - Started Main
```

#### 第二步：连接设备

1. **连接雷达**：通过 USB 连接 TI mmWave 雷达到 PC，确保 mmWave Studio 已启动
2. **连接手机**：在手机 App 中输入 PC 的 IP 地址和端口 6666，点击连接

#### 第三步：校准雷达启动延迟 ⚠️ **必须执行**

```matlab
% 在 MATLAB 中运行延迟测量脚本
cd matlab_client
test_radar_startup_delay

% 脚本会自动测量雷达启动延迟（约10次测试）
% 记录输出的【推荐配置值】，例如：
% RADAR_STARTUP_DELAY = 1050;  % 毫秒
```

#### 第四步：配置采集参数

编辑 `matlab_client/main_multimodal_data_capture.m` 文件的**用户配置区**：
批量采集流程

1. **初始化阶段**
   - 程序验证配置参数（数据路径、场景文件、雷达DLL）
   - 输入人员组合（例如 `yh-wt`）
   - 连接 AudioCenterServer 并检查设备
   - 执行初始时间同步
   - 初始化雷达连接
   - 创建采集日志文件

2. **场景循环采集**
   - 程序依次遍历 `scenes_file.csv` 中的所有场景
   - 每个场景重复 `repeat_count` 次
   - 每次采集前显示场景信息和当前进度
   - 操作员输入 `y` 确认开始（输入 `s` 跳过）

3. **精确同步触发**
   - 执行 SNTP 时间同步，获取当前偏移量和 RTT
   - 计算理论触发时间点（考虑雷达启动延迟）
   - **先**发送雷达启动命令（提前 RADAR_STARTUP_DELAY 毫秒）
   - 等待到音频触发时间点，发送音频采集 API
   - 雷达和音频在理论时间点同步开始采集

4. **数据保存与验证**
   - 采集完成后自动停止雷达和音频
   - 验证雷达文件是否存在且大小正常（> 1MB）
   - 保存元数据 JSON 文件，包含：
     - 人员组合、场景信息
     - 同步质量（SNTP 偏移、RTT、触发时间戳）
     - 音频参数（44.1kHz 采样率、20kHz 超声波）
     - 文件映射关系
   - 记录采集结果到 CSV 日志

5. **完成统计**
   - 显示总采集次数、成功次数、失败次数、成功率
   - 生成完整的采集日志文件

#### 第五步：手动创建数据目录
关键参数说明

#### 主控程序配置 (main_multimodal_data_capture.m)

```matlab
data_root_path = 'D:\multimodal_data\';  % 数据存储根目录（需预先创建）
capture_duration = 10;                   % 每次采集时长（秒）
repeat_count = 3;                        % 每场景重复次数
RADAR_STARTUP_DELAY = 1000;              % 雷达启动延迟（毫秒，需实测）
server_ip = '127.0.0.1';                 % AudioCenterServer IP
server_port = 8080;                      % REST API 端口
```

#### 时间同步参数

- **预触发缓冲时间**：100ms（在 syncCapture.m 中定义）
- **SNTP 服务器端口**：1123（UDP）
- **时间同步精度**：毫秒级（取决于网络 RTT）

#### AudioClient API (MATLAB)

```matlab
% 创建客户端
audioClient = AudioClient(server_ip, server_port);

% 列出已连接设备
devices = audioClient.listDevices();

% 时间同步
[offset_ms, rtt_ms] = audioClient.syncTime();

% 开始录制（sceneId, duration, timestamp）
success = audioClient.startRecording('yh-ssk-A1-B1-C1-D1-E1-01', 10, 1736121234567);

% 停止录制
audioClient.stopRecording();

% 获取录制状态
status = audioClient.getRecordingStatus();
```

#### REST API 端点 (HTTP)

- `POST /api/recording/start` - 开始录制
  ```json
  {
    "sceneId": "yh-ssk-A1-B1-C1-D1-E1-01",
    "duration": 10,
    "timestamp": 1736121234567
  }
  ```
- `POST /api/recording/stop` - 停止录制
- `GET /api/recording/status` - 获取状态
- `GET /api/devices/list` - 列出设备
- `POST /api/time/sync` - 时间同步
### 采集流程

1. **输入被试信息**：程序提示输入被试名称缩写，系统自动分配唯一编号
2. **场景循环**：程序依次读取 scenes_file.csv 中的场景
3. **确认采集**：每次采集前需要操作员确认（输入 'y'）
4. **同步触发**：MATLAB 同时触发雷达和手机端开始采集
5. **数据存储**：采集完成后自动保存，文件名格式：`{被试编号}-{场景代码}-{重复次数}.bin`

### 命令参考

#### MATLAB 端关键参数

在 `data_collection_new.m` 中可调整：

```matlab
data_path = 'D:\\mmwave_data\\';  % 数据存储根目录
dir_name = 'office\\';             % 场景子目录
start_scene = 1;                  % 起始场景编号
repeat_time = 3;                  % 每场景重复次数
```

#### AudioCenterServer 指令

服务器支持通过 Spring Shell 命令控制手机端：

- `start-recording`：开始录制超声波数据
- `stop-recording`：停止录制
- `list-devices`：查看已连接的设备
- `sync-time`：同步手机时间

### 数据格式

#### 毫米波雷达数据

- **格式**：`.bin` 二进制文件
- **命名**：`{人员组合}-{场景代码}-{重复序号:02d}.bin`
- **示例**：`yh-ssk-A1-B1-C1-D1-E1-01.bin`
- **位置**：`{data_root_path}\{人员组合}\`
- **解析**：使用 `readDCA1000.m` 函数读取

#### 超声波数据

- **格式**：`.wav` 音频文件
- **命名**：与雷达数据保持一致
- **示例**：`yh-ssk-A1-B1-C1-D1-E1-01.wav`
- **采样率**：44.1 kHz
- **超声波频率**：20 kHz（Android 端固定）
- **模式**：ultrasonic（超声波模式）
- **位置**：服务器端保存（需配置）

#### 元数据文件 (JSON)

每次采集会生成对应的元数据文件：

```json
{
  "staff_combination": "yh-ssk",
  "scene_info": {
    "idx": 5,
    "intro": "上方有人在动慢走速度1.0m/s-0°-静止站立",
    "code": "A1-B1-C1-D1-E1"
  },
  "capture_config": {
    "repeat_index": 1,
    "radar_delay_ms": 1050
  },
  "audio_params": {
    "sample_rate": 44100,
    "ultrasonic_freq": 20000,
    "format": "wav",
    "mode": "ultrasonic"
  },
  "radar_params": {
    "format": "bin",
    "data_type": "mmwave_adc"
  },
  "sync_quality": {
    "sntp_offset_ms": 2.5,
    "rtt_ms": 3.2,
    "trigger_timestamp_utc": 1736121234567,
    "trigger_time_readable": "2026-01-06 08:13:54"
  },
  "file_mapping": {
    "radar_file": "yh-ssk-A1-B1-C1-D1-E1-01.bin",
    "audio_files": ["yh-ssk-A1-B1-C1-D1-E1-01.wav"]
  },
  "capture_status": {
    "success": true,
    "status_message": "success",
    "timestamp": "2026-01-06 08:14:04"
  }
}
```

**命名**：`{人员组合}-{场景代码}-{重复序号:02d}_meta.json`

#### 采集日志 (CSV)

每次运行主控程序会生成一个日志文件：

```csv
timestamp,scene_idx,scene_code,repeat_index,success,sntp_offset,rtt,error_message
2026-01-06 08:13:54,5,A1-B1-C1-D1-E1,1,true,2.5,3.2,
2026-01-06 08:14:20,5,A1-B1-C1-D1-E1,2,true,2.3,3.1,
2026-01-06 08:14:45,5,A1-B1-C1-D1-E1,3,false,2.6,3.3,radar_file_missing
...
```

**命名**：`capture_log_{yyyymmdd_HHMMSS}.csv`

## 示例

### 完整采集流程示例

```matlab
% 1. 启动 MATLAB，进入项目目录
cd E:\ScreenDataCapture\Multimodal_data_capture\matlab_client

% 2. 首次使用需要校准雷达启动延迟
test_radar_startup_delay
% 输出示例：
% 【推荐配置值】
% RADAR_STARTUP_DELAY = 1050;  % 毫秒

% 3. 编辑 main_multimodal_data_capture.m，填入推荐值
% RADAR_STARTUP_DELAY = 1050;

% 4. 运行主控程序
main_multimodal_data_capture

% 5. 按提示操作
% ========================================
%   多模态数据采集系统 v1.0
% ========================================
%
% ========== 参数验证 ==========
% ✓ 数据根目录: D:\multimodal_data\
% ✓ 场景文件: ../mmwave_radar/scenes_file.csv
% ✓ 雷达DLL: 已找到
%
% ========== 用户信息 ==========
% 请输入人员组合（例如 yh-ssk）: yh-ssk
% ✓ 人员组合: yh-ssk
% ✓ 保存路径: D:\multimodal_data\yh-ssk
%
% ========== 加载场景配置 ==========
% ✓ 已加载 40 个场景
%
% ========================================
%   开始批量采集
%   总场景数: 40
%   每场景重复: 3 次
%   预计总采集次数: 120
% ========================================
%
% ========================================
% 场景 1/40
% ========================================
% 描述: 静止不动
% 代码: A0-B0-C0-D0-E0
% ========================================
%
% ---------- 第 1/3 次采集 ----------
% 输入 y 开始采集，输入 s 跳过: y
%
% 场景ID: yh-ssk-A0-B0-C0-D0-E0-01
%
% 开始同步采集...
%   [同步] 执行时间同步...
%   [同步] 时间偏移: 2.50 ms, RTT: 3.20 ms
%   [时序] 当前时间: 1736121234567 ms (UTC)
%   [时序] 触发时间: 1736121235717 ms (UTC)
%   [雷达] 发送雷达启动命令...
%   [雷达] 启动命令已发送 (T=1736121234567 ms)
%   [音频] 发送音频采集命令...
%   [音频] 启动命令已发送 (T=1736121235617 ms)
%   [采集] 正在同步采集数据 (10秒)...
%   [停止] 停止采集...
%   [验证] 检查数据文件...
%   [验证] 雷达文件: yh-ssk-A0-B0-C0-D0-E0-01.bin (5.23 MB)
%   [验证] 音频文件将由服务器保存
%   [完成] 同步采集成功！
%
% ✓✓✓ 采集成功！✓✓✓
%   [元数据] 已保存: yh-ssk-A0-B0-C0-D0-E0-01_meta.json
%
% 当前统计: 成功 1 / 失败 0 / 总计 1
% ...
```

### 数据读取示例

```matlab
% 读取毫米波雷达数据
filename = 'D:\multimodal_data\yh-ssk\yh-ssk-A1-B1-C1-D1-E1-01.bin';
[adcData, ~] = readDCA1000(filename);

% 数据维度：[samples, chirps, rx_antennas]
size(adcData)

% 读取元数据
meta_file = 'D:\multimodal_data\yh-ssk\yh-ssk-A1-B1-C1-D1-E1-01_meta.json';
fid = fopen(meta_file, 'r', 'n', 'UTF-8');
meta_json = fread(fid, '*char')';
fclose(fid);
metadata = jsondecode(meta_json);

% 查看同步质量
fprintf('SNTP 偏移: %.2f ms\n', metadata.sync_quality.sntp_offset_ms);
fprintf('触发时间: %s\n', metadata.sync_quality.trigger_time_readable);
```

## 项目结构

```
Multimodal_data_capture/
│
├── README.md                          # 项目总体说明
├── TEST_GUIDE.md                      # 测试指南
├── QUICK_TEST.md                      # 快速测试说明
│
├── AudioCenterServer/                 # Java 服务端
│   ├── src/
│   │   └── main/
│   │       ├── java/com/lannooo/
│   │       │   ├── Main.java          # 主程序（自动启动 Netty + SNTP）
│   │       │   ├── server/api/        # REST API 控制器
│   │       │   │   ├── RecordingController.java   # 录制控制
│   │       │   │   ├── DeviceController.java      # 设备管理
│   │       │   │   └── TimeController.java        # 时间同步
│   │       │   ├── service/
│   │       │   │   └── RecordingService.java      # 录制业务逻辑
│   │       │   └── sync/
│   │       │       └── SNTPServer.java            # SNTP 服务器
│   │       └── resources/
│   │           └── application.properties         # 配置文件
│   ├── build.gradle.kts               # Gradle 构建配置
│   └── README.md                      # 服务端详细说明
│
├── matlab_client/                     # MATLAB 客户端工具
│   ├── AudioClient.m                  # HTTP REST 客户端类
│   ├── syncTimeNTP.m                  # SNTP 时间同步函数
│   ├── syncCapture.m                  # 同步采集协调函数
│   ├── saveMetadata.m                 # 元数据保存函数
│   ├── main_multimodal_data_capture.m # 主控批量采集程序 ⭐
│   ├── test_audio_client.m            # AudioClient 测试脚本
│   ├── test_sntp_sync.m               # SNTP 同步测试脚本
│   └── test_radar_startup_delay.m     # 雷达启动延迟测量脚本 ⚠️
│
├── mmwave_radar/                      # 雷达控制脚本
│   ├── data_collection_integrated.m   # 集成采集程序（旧版）
│   ├── scenes_file.csv                # 场景配置文件（40个场景）
│   ├── Init_RSTD_Connection.m         # 雷达初始化函数
│   └── readDCA1000.m                  # 数据读取函数
│
├── config/                            # 配置文件
│   ├── system_config.json             # 系统配置
│   └── USAGE.md                       # 使用说明
│
└── [数据存储目录]/                    # 采集的数据（用户配置）
    └── {人员组合}/                    # 例如：yh-ssk/
        ├── {场景ID}.bin               # 雷达数据
        ├── {场景ID}.wav               # 超声波数据（服务器端）
        ├── {场景ID}_meta.json         # 元数据
        └── capture_log_{timestamp}.csv # 采集日志
```

## 常见问题

### Q1: AudioCenterServer 无法启动？

**A**: 
- 检查 Java 版本是否为 JDK 17+，使用 `java -version` 确认
- 确保使用 `gradlew.bat` 而非全局的 `gradle` 命令
- 检查端口 6666（Netty）、8080（REST API）、1123（SNTP）是否被占用

### Q2: 手机无法连接到服务器？

**A**: 
- 确认 PC 和手机在同一局域网
- 检查防火墙是否阻止了 6666 端口
- 在 PC 上运行 `ipconfig` 确认 IP 地址
- 在手机 App 中输入正确的 IP:6666（注意是 Netty 端口，不是 8080）

### Q3: 雷达连接失败？

**A**:
- 确认 mmWave Studio 已正确安装并启动
- 检查 USB 连接和电源
- 验证 RSTD_DLL_Path 路径是否正确
- 检查是否有其他程序占用雷达连接

### Q4: 时间同步失败或偏移量异常？

**A**: 
- 运行 `test_sntp_sync.m` 检查 SNTP 服务器状态
- 确保 AudioCenterServer 已启动（SNTP 服务在 1123 端口）
- 检查防火墙是否阻止 UDP 1123 端口
- 如果偏移量为 -28800000ms（8小时），说明时区配置错误，已在代码中修复

### Q5: 雷达和音频不同步？

**A**: 
- **必须**先运行 `test_radar_startup_delay.m` 测量雷达启动延迟
- 将测量结果填入 `main_multimodal_data_capture.m` 的 `RADAR_STARTUP_DELAY` 参数
- 检查网络延迟（RTT）是否过大（> 50ms）
- 确保 SNTP 同步质量良好（偏移 < 10ms）

### Q6: 如何修改采集时长？

**A**: 在 `main_multimodal_data_capture.m` 的用户配置区修改 `capture_duration` 参数（单位：秒）。

### Q7: scenes_file.csv 格式要求？

**A**: 
- CSV 文件必须包含三列：`idx`（序号）、`intro`（场景描述）、`code`（文件名代码）
- 确保使用 UTF-8 编码
- 不要修改表头（第一行）
- 场景代码（code）会用于文件命名，避免使用特殊字符

### Q8: 如何跳过某个场景？

**A**: 在程序提示 "输入 y 开始采集" 时，输入 `s` 即可跳过当前采集。跳过的记录会标记在日志文件中。

### Q9: 采集失败后如何处理？

**A**: 
- 程序不会自动重试，避免中断整体流程
- 查看采集日志 CSV 文件中的 `error_message` 列确定失败原因
- 可以重新运行程序，手动跳过已成功的场景，只采集失败的部分

### Q10: 元数据 JSON 文件有什么用？

**A**: 
- 记录每次采集的完整参数和同步质量信息
- 用于后期数据分析时验证数据质量
- 包含精确的触发时间戳，便于多模态数据对齐
- 可用于数据溯源和实验记录

## 开发计划

### 已完成功能 ✅

- [x] 毫米波雷达数据采集
- [x] AudioCenterServer 基础框架（Spring Boot + Netty）
- [x] Android 客户端连接管理
- [x] REST API 接口（录制控制、设备管理、时间同步）
- [x] MATLAB AudioClient 工具类
- [x] SNTP 时间同步模块（Java 服务器 + MATLAB 客户端）
- [x] 精确同步触发机制（考虑雷达启动延迟）
- [x] 批量采集主控程序（main_multimodal_data_capture.m）
- [x] 元数据自动生成（JSON 格式）
- [x] 采集日志记录（CSV 格式）
- [x] 雷达启动延迟测量工具
- [x] 场景配置管理（40 个预设场景）

### 计划优化 📋

- [ ] 数据质量实时检查（采集过程中）
- [ ] 采集失败自动重试机制（可选配置）
- [ ] 批量数据后处理工具（数据对齐、格式转换）
- [ ] GUI 控制界面（可选，替代命令行）
- [ ] 支持更多传感器模态（如 IMU、摄像头）
- [ ] 云端数据自动备份
- [ ] 数据压缩存储（减少磁盘占用）
- [ ] 实时数据预览功能（波形显示）

### 性能优化 ⚡

- [ ] 减少采集触发延迟（目标 < 10ms）
- [ ] 优化数据传输效率（手机到 PC）
- [ ] 改进错误处理和异常恢复
- [ ] 完善日志系统（分级日志、自动轮转）

---

*最后更新：2026年1月6日*