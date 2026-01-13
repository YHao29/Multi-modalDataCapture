# 多模态数据采集系统

基于毫米波雷达和超声波的多模态数据采集系统，支持精确时间同步的连续批量采集操作。

## 项目概述

本项目实现了一个高效的多模态数据采集系统，整合毫米波雷达和超声波两种传感模态，提供简单易用的自动化采集接口，支持40种预设场景的批量数据采集。

## 核心特性

- **多模态同步**：毫米波雷达 + 超声波数据精确时间同步采集
- **批量自动化**：通过场景配置文件实现连续自动采集
- **精确触发**：SNTP时间同步 + 雷达延迟补偿，确保毫秒级同步
- **完整元数据**：自动生成JSON元数据和CSV采集日志
- **操作简便**：MATLAB命令行界面，友好的进度提示

## 技术架构

- **服务端**：Spring Boot + Netty（AudioCenterServer）
  - REST API（8080端口）：MATLAB客户端接口
  - Netty服务器（6666端口）：Android设备连接
  - SNTP服务器（1123端口）：时间同步
- **Android端**：超声波数据采集App（44.1kHz采样率，20kHz超声波）
- **MATLAB端**：雷达控制、场景管理、同步采集协调
- **时间同步**：SNTP协议，毫秒级精度

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│              PC 控制中心 (MATLAB)                        │
│  ┌──────────────────────────────────────────────────┐   │
│  │ main_multimodal_data_capture.m                  │   │
│  │  - 场景管理 (scenes_file.csv)                   │   │
│  │  - AudioClient (HTTP REST API)                  │   │
│  │  - SNTP时间同步 (syncTimeNTP.m)                 │   │
│  │  - 雷达控制 (Init_RSTD_Connection.m)            │   │
│  │  - 同步触发协调 (syncCapture.m)                 │   │
│  └──────────────────────────────────────────────────┘   │
└────────────┬────────────────────────┬───────────────────┘
             │                        │
       REST API (8080)           USB/Ethernet
             │                        │
    ┌────────▼────────┐       ┌───────▼────────┐
    │ AudioCenterServer│       │  TI mmWave     │
    │  Spring Boot    │       │  雷达开发板     │
    │  + Netty        │       └────────────────┘
    │  + SNTP Server  │
    └────────┬────────┘
             │
        TCP (6666)
             │
    ┌────────▼────────┐
    │  Android 手机    │
    │  超声波采集App   │
    └─────────────────┘
```

### 核心工作流程

1. **初始化**：加载场景配置，连接AudioCenterServer和雷达设备
2. **时间同步**：SNTP协议同步PC和Android设备时间（< 10ms偏移）
3. **精确触发**：
   - 计算理论触发时间点
   - 提前1050ms发送雷达启动命令（补偿雷达启动延迟）
   - 提前2200ms发送音频采集命令（补偿手机启动延迟）
   - 雷达和音频在理论时间点同步开始采集
4. **数据存储**：自动保存.bin雷达数据、.wav音频数据、.json元数据、.csv日志

## 快速开始

### 前置要求

- **硬件**：PC (Windows 10/11) + TI mmWave雷达 + Android手机
- **软件**：Java 17+, MATLAB R2019b+, TI mmWave Studio

### 三步开始采集

#### 1. 编译并启动服务器

```powershell
cd AudioCenterServer
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

等待看到：
```
Netty server started successfully!
REST API available at http://localhost:8080/api
Started Main in X.XXX seconds
```

#### 2. 连接设备

- **手机**：打开App，输入 `PC_IP`和`端口:6666`，点击连接，显示XXX established
- **雷达**：USB连接，mmWave Studio中运行 `Run`（按照之前的毫米波雷达连接教程完成配置文件和雷达连接操作）

#### 3. 运行采集

```matlab
% MATLAB中运行
% 开始采集
main_multimodal_data_capture
```

### 完整设置指南

详细的安装、配置和测试步骤，请查看：
- **[SETUP_AND_TEST.md](SETUP_AND_TEST.md)** - 完整的安装配置和测试指南
## 数据格式

### 推荐：层次化数据组织（适用于深度学习）

**新项目推荐使用层次化结构**，便于数据加载和管理：

```
dataset_root/
├── dataset_info.json          # 数据集元信息
├── scenes_info.json           # 场景映射表（40个场景）
├── train.txt                  # 训练集分割
├── val.txt                    # 验证集分割
├── test.txt                   # 测试集分割
└── subjects/                  # 按被试组织
    ├── subject_001/
    │   ├── samples_metadata.json    # 被试所有样本的元数据
    │   ├── radar/
    │   │   ├── sample_001_front_static_left_static_idle.bin
    │   │   ├── sample_002_front_static_left_moving_idle.bin
    │   │   └── ...
    │   └── audio/
    │       ├── sample_001_front_static_left_static_idle.wav
    │       ├── sample_002_front_static_left_moving_idle.wav
    │       └── ...
    ├── subject_002/
    │   └── ...
    └── ...
```

#### 数据集元信息（dataset_info.json）

```json
{
  "dataset_name": "Multimodal_Human_Activity_Detection",
  "version": "1.0.0",
  "created_date": "2024-01-15",
  "modalities": ["mmwave_radar", "ultrasonic_audio"],
  "statistics": {
    "num_subjects": 10,
    "num_scenes": 40,
    "total_samples": 600,
    "samples_per_subject": 60
  },
  "hardware_config": {
    "radar": {
      "model": "TI mmWave AWR1843",
      "num_rx_antennas": 4,
      "chirp_samples": 256,
      "sample_rate_hz": 4000000
    },
    "audio": {
      "sample_rate_hz": 44100,
      "ultrasonic_freq_hz": 20000
    }
  }
}
```

#### 被试元数据（samples_metadata.json）

```json
{
  "subject_id": 1,
  "num_samples": 60,
  "samples": [
    {
      "sample_id": 1,
      "scene": {
        "code": "A1-B1-C1-D1-E1",
        "intro": "合法用户静坐，窥视者1.0米-0°-静止站立",
        "idx": 5
      },
      "radar_file": "radar/sample_001_A1_B1_C1_D1_E1.bin",
      "audio_file": "audio/sample_001_A1_B1_C1_D1_E1.wav",
      "sync_quality": {
        "ntp_offset_ms": 2.5,
        "audio_start_time": "2024-01-15 10:30:00.123",
        "radar_start_time": "2024-01-15 10:30:00.138"
      },
      "capture_time": "2024-01-15 10:30:00"
    }
  ]
}
```

### 工具函数

#### MATLAB工具（matlab_client/utils/）

```matlab
% 1. 生成数据集元信息
createDatasetInfo('E:\data\', 'E:\data\dataset_info.json');

% 2. 从CSV获取场景信息（直接读取，不硬编码）
sceneInfo = getSceneInfo('A1-B1-C1-D1-E1');
% 返回: sceneInfo.intro = '合法用户静坐，窥视者1.0米-0°-静止站立'

% 3. 转换旧数据到新格式
reorganizeData('E:\old_data\', 'E:\new_data\');

% 4. 使用新的元数据保存函数（推荐）
syncInfo = struct('ntp_offset_ms', 2.5, ...
                  'audio_start_time', '2024-01-15 10:30:00.123', ...
                  'radar_start_time', '2024-01-15 10:30:00.138');
saveMetadataV2(1, 5, 'A1-B1-C1-D1-E1', syncInfo, '2024-01-15 10:30:00', 'E:\data\');
```

#### Python DataLoader（tools/multimodal_dataloader.py）

```python
from torch.utils.data import DataLoader
from multimodal_dataloader import MultimodalDataset

# 创建训练集
train_dataset = MultimodalDataset(
    root_dir='E:/data/subjects',
    split='train',
    split_file='E:/data/train.txt'
)

# 创建DataLoader
train_loader = DataLoader(
    train_dataset,
    batch_size=32,
    shuffle=True,
    num_workers=4
)

# 训练循环
for batch in train_loader:
    radar = batch['radar']      # shape: (B, 256, 128, 4)
    audio = batch['audio']      # shape: (B, audio_samples)
    labels = batch['label']     # shape: (B,)
    # ... 模型训练
```

#### 数据集分割（tools/split_dataset.py）

```bash
# 按被试分割（推荐）
python split_dataset.py --root E:/data/subjects --strategy subject --ratios 0.7 0.15 0.15

# 按样本随机分割
python split_dataset.py --root E:/data/subjects --strategy sample --ratios 0.7 0.15 0.15
```

#### 数据验证（tools/verify_dataset.py）

```bash
# 验证数据完整性
python verify_dataset.py --root E:/data/subjects --output verification_report.txt
```

### 传统：平铺文件结构（向后兼容）

旧的采集方式仍然支持，数据文件采用统一命名格式：`{人员组合}-{场景代码}-{重复序号:02d}`

示例：`yh-ssk-A1-B1-C1-D1-E1-01`

### 数据文件类型

#### 1. 雷达数据（.bin）
- **格式**：二进制文件（int16）
- **读取**：使用 `readDCA1000.m` 函数
```matlab
[adcData, fileSize] = readDCA1000('path/to/file.bin');
% adcData: [采样点数=256, chirp数=128, 天线数=4]
```

#### 2. 超声波数据（.wav）
- **格式**：WAV音频文件
- **采样率**：44.1 kHz
- **超声波频率**：20 kHz
- **位置**：由AudioCenterServer管理

### 场景编码说明

场景代码格式：`A{数字}-B{数字}-C{数字}-D{数字}-E{数字}`

**这是一个隐私保护场景下的窥视检测系统**，编码含义如下：

- **A - 合法用户状态**
  - `A0`: 环境基线（无人）
  - `A1`: 合法用户存在

- **B - 合法用户动作**
  - `B0`: 基线
  - `B1`: 静坐
  - `B2`: 打字
  - `B3`: 轻微摇晃

- **C - 窥视者距离**
  - `C0`: 无窥视者
  - `C1`: 1.0米
  - `C2`: 2.0米

- **D - 窥视者角度**
  - `D0`: 无窥视者
  - `D1`: 0°（正面）
  - `D2`: 60°（侧面）

- **E - 窥视者行为**
  - `E0`: 基线/无窥视者
  - `E1`: 静止站立
  - `E2`: 慢速路过
  - `E3`: 靠近并驻足
  - `E4`: 正常路过

示例：
- `A0-B0-C0-D0-E0`：环境基线（无人）
- `A1-B1-C1-D1-E1`：合法用户静坐，窥视者1.0米-0°-静止站立
- `A1-B2-C2-D2-E3`：合法用户打字，窥视者2.0米-60°-靠近并驻足

共40个场景，详见 `matlab_client/radar/scenes_file.csv`

## 项目结构

```
Multimodal_data_capture/
├── README.md                          # 项目总览（本文件）
├── SETUP_AND_TEST.md                  # 完整安装配置和测试指南
├── GITHUB_GUIDE.md                    # Git版本控制指南
│
├── AudioCenterServer/                 # Java服务端
│   ├── src/main/java/com/lannooo/
│   │   ├── Main.java                  # 主程序（自动启动Netty+SNTP）
│   │   ├── server/api/                # REST API控制器
│   │   │   ├── RecordingController.java    # 录制控制
│   │   │   ├── DeviceController.java       # 设备管理
│   │   │   └── TimeController.java         # 时间同步
│   │   ├── service/
│   │   │   ├── NettyService.java          # Netty服务
│   │   │   └── RecordingService.java      # 录制业务逻辑
│   │   ├── sync/
│   │   │   └── SNTPServer.java            # SNTP时间同步服务器
│   │   ├── device/
│   │   │   └── DeviceManager.java         # 设备管理
│   │   └── shell/                         # Spring Shell命令行
│   ├── src/main/resources/
│   │   └── application.properties         # 服务器配置
│   ├── audio/
│   │   └── audio.properties               # 音频配置
│   └── build.gradle.kts                   # Gradle构建配置
│
├── matlab_client/                     # MATLAB客户端工具
│   ├── main_multimodal_data_capture.m # ⭐ 主控批量采集程序
│   ├── AudioClient.m                  # HTTP REST客户端类
│   ├── syncTimeNTP.m                  # SNTP时间同步函数
│   ├── syncCapture.m                  # 同步采集协调函数
│   ├── saveMetadata.m                 # 元数据保存函数（旧版）
│   ├── test_audio_client.m            # AudioClient测试脚本
│   ├── test_sntp_sync.m               # SNTP同步测试脚本
│   │
│   ├── utils/                         # ⭐ 工具函数库
│   │   ├── loadScenesFromCSV.m        # 从CSV加载场景配置
│   │   ├── getSceneInfo.m             # 获取场景信息（从CSV读取）
│   │   ├── saveMetadataV2.m           # 新版元数据保存（推荐）
│   │   ├── createDatasetInfo.m        # 生成数据集元信息
│   │   ├── createScenesInfo.m         # 生成场景映射文件
│   │   └── reorganizeData.m           # 旧数据格式转换
│   │
│   └── radar/                         # 雷达相关函数
│       ├── Init_RSTD_Connection.m     # 雷达初始化
│       ├── readDCA1000.m              # 雷达数据读取
│       └── scenes_file.csv            # 场景配置（可扩展）
│
├── tools/                             # ⭐ Python工具集（深度学习）
│   ├── multimodal_dataloader.py       # PyTorch DataLoader
│   ├── split_dataset.py               # 数据集分割工具
│   └── verify_dataset.py              # 数据完整性验证
│
└── config/                            # 系统配置
    └── system_config.json             # 全局配置
```

### 关键文件说明

| 文件 | 用途 |
|------|------|
| `main_multimodal_data_capture.m` | 主控程序，批量采集入口 |
| `AudioClient.m` | MATLAB HTTP客户端，封装REST API |
| `utils/saveMetadataV2.m` | **推荐**使用的新版元数据保存函数 |
| `utils/loadScenesFromCSV.m` | 从CSV文件加载场景配置（动态读取） |
| `utils/getSceneInfo.m` | 根据场景代码获取场景信息 |
| `utils/reorganizeData.m` | 将旧的平铺数据转换为层次化结构 |
| `tools/multimodal_dataloader.py` | **PyTorch数据加载器**，支持批量训练 |
| `tools/split_dataset.py` | 生成train/val/test分割 |
| `tools/verify_dataset.py` | 数据完整性检查 |
| `radar/scenes_file.csv` | 场景配置文件，支持动态修改 |
| `AudioCenterServer/Main.java` | 服务器主程序，自动启动所有服务 |
| `SETUP_AND_TEST.md` | 完整的安装、配置和测试指南 |

## 常见问题

详细的问题排查指南请查看 [SETUP_AND_TEST.md](SETUP_AND_TEST.md#常见问题排查)

### 快速解决方案

| 问题 | 快速检查 |
|------|----------|
| 服务器启动失败 | 检查Java版本（>=17），端口占用 |
| 手机无法连接 | 确认同一网络，防火墙主动设置开放端口6666（高级防火墙设置） |
| 雷达连接失败 | USB连接，mmWave Studio运行`Run` (参考之前毫米波配置文档)|
| 时间同步偏移大 | 检查网络延迟，关闭VPN |
| MATLAB找不到函数 | 检查当前工作目录，添加路径 |

## API参考

### MATLAB AudioClient API

```matlab
% 创建客户端
client = AudioClient(server_ip, server_port);

% 主要方法
devices = client.listDevices();                    % 获取设备列表
[offset, rtt] = client.syncTime();                 % 时间同步
success = client.startRecording(sceneId, duration, timestamp);
client.stopRecording();                            % 停止录制
status = client.getRecordingStatus();              % 获取状态
```

### REST API端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/devices/list` | GET | 获取已连接设备列表 |
| `/api/devices/status` | GET | 服务器状态检查 |
| `/api/recording/start` | POST | 开始录制 |
| `/api/recording/stop` | POST | 停止录制 |
| `/api/recording/status` | GET | 获取录制状态 |
| `/api/time/sync` | POST | SNTP时间同步 |

### 服务端口

| 端口 | 协议 | 用途 |
|------|------|------|
| 6666 | TCP | Android设备连接（Netty） |
| 8080 | HTTP | MATLAB REST API |
| 1123 | UDP | SNTP时间同步 |

## 开发状态

### 已实现功能 ✅

- [x] AudioCenterServer（Spring Boot + Netty + SNTP）
- [x] REST API接口完整实现
- [x] MATLAB AudioClient工具类
- [x] SNTP时间同步（<10ms精度）
- [x] 精确同步触发机制（雷达/音频延迟补偿）
- [x] 批量采集主控程序
- [x] 元数据自动生成（JSON）
- [x] 采集日志记录（CSV）
- [x] 雷达启动延迟测量工具
- [x] 40个预设场景配置
- [x] 完整测试脚本套件


## 许可证

本项目仅供研究和学习使用。

## 致谢

- TI mmWave Studio SDK
- Spring Boot Framework
- Netty Framework

---

*最后更新：2026年1月12日*