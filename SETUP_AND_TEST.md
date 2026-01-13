# 多模态数据采集系统 - 安装与测试指南

本指南涵盖从新电脑环境配置到完整功能测试的全部流程。

## 目录

- [前置条件检查](#前置条件检查)
- [环境配置](#环境配置)
- [编译与启动](#编译与启动)
- [设备连接](#设备连接)
- [功能测试](#功能测试)
- [完整采集测试](#完整采集测试)
- [常见问题排查](#常见问题排查)

---

## 前置条件检查

### 硬件要求

- [ ] PC（Windows 10/11）
- [ ] TI mmWave 雷达开发板（已连接到 PC）
- [ ] Android 手机（已安装超声波采集 App）
- [ ] USB 连接线
- [ ] 局域网环境（PC 和手机需在同一网络）

### 软件依赖

#### Java 环境（必需）

```powershell
# 检查 Java 版本（需要 JDK 17+）
java -version

# 如果没有安装，下载安装 JDK 17+
# Oracle JDK: https://www.oracle.com/java/technologies/downloads/
# 或 OpenJDK: https://adoptium.net/
```

#### MATLAB 环境（必需）

```powershell
# 检查 MATLAB 版本（需要 R2019b 或更高）
matlab -batch "version"

# 或在 MATLAB 中运行
# >> ver
```

**必需工具箱：**
- Instrument Control Toolbox

```matlab
% 检查工具箱
license('test', 'Instrument_Control_Toolbox')
```

#### TI mmWave Studio（必需）

- 参考之前毫米波雷达配置、连接教程

---

## 环境配置

### 步骤 1: 克隆/获取代码

```powershell
# 如果从 GitHub 克隆
git clone https://github.com/YOUR_USERNAME/Multimodal_data_capture.git
cd Multimodal_data_capture

```

### 步骤 2: 创建数据目录

```powershell
# 创建数据存储根目录
mkdir D:\multimodal_data

# 创建测试子目录
mkdir D:\multimodal_data\test
```

### 步骤 3: 配置项目参数

编辑 `matlab_client/main_multimodal_data_capture.m`，修改**用户配置区**：

```matlab
%% ==================== 用户配置区 ====================

% 【必填】数据存储根目录（需手动创建）
data_root_path = 'D:\multimodal_data\';  % 改为你的路径

% 【必填】采集时长（秒）
capture_duration = 5;  % 测试时可设为 5 秒

% 【必填】每个场景重复采集次数
repeat_count = 1;  % 测试时设为 1 次

% 【必填】雷达启动延迟（毫秒）
RADAR_STARTUP_DELAY = 1000;  % 初始值

% 【必填】手机音频启动延迟（毫秒）
PHONE_STARTUP_DELAY = 2200;  % 默认值

% 服务器配置
server_ip = '127.0.0.1';  % 如果在本机测试，保持不变
server_port = 8080;

% 场景文件路径
scenes_csv_file = 'radar/scenes_file.csv';

% 雷达配置
RSTD_DLL_Path = 'C:\ti\mmwave_studio_02_01_01_00\mmWaveStudio\Clients\RtttNetClientController\RtttNetClientAPI.dll';
% 改为你的实际安装路径
```

---

## 编译与启动

### 第一步：编译 AudioCenterServer

⚠️ **新电脑必须先编译，否则无法运行！**

```powershell
# 进入服务器目录
cd AudioCenterServer

# 清理旧的构建文件，编译项目
.\gradlew.bat clean build
```

**首次编译说明：**
- **需要网络连接**：会自动下载 Gradle 和项目依赖（约 100MB+）
- **耗时较长**：首次编译可能需要 3-10 分钟（取决于网络速度）
- **进度显示**：会看到下载 jar 包的进度信息


**如果编译失败：**

1. **检查 Java 版本**
```powershell
java -version  # 必须 >= 17
```

2. **配置国内镜像（如果网络慢）**

编辑 `AudioCenterServer/build.gradle.kts`，在 repositories 部分添加：

```kotlin
repositories {
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/spring") }
    mavenCentral()
}
```

然后重新编译：
```powershell
.\gradlew.bat clean build
```

### 第二步：启动 AudioCenterServer

编译成功后启动服务器：

```powershell
# 启动服务器
.\gradlew.bat bootRun
```

**预期启动输出：**
```
> Task :bootRun

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.3.2)

========================================
Auto-starting Netty server on port 6666...
Netty server started successfully!
REST API available at http://localhost:8080/api
========================================
Started Main in X.XXX seconds
```

**启动成功标志：**
- ✅ 看到 "Netty server started successfully!"
- ✅ 看到 "REST API available at http://localhost:8080/api"
- ✅ 看到 "Started Main"
- ✅ 进程保持运行，不退出

**保持此终端窗口打开！**

**如果启动失败：**

检查端口是否被占用：
```powershell
netstat -ano | findstr "6666"
netstat -ano | findstr "8080"
netstat -ano | findstr "1123"
```

如果端口被占用，关闭占用程序或修改配置文件中的端口。

---

## 设备连接

### 连接 Android 手机

#### 1. 获取 PC 的 IP 地址

打开新的 PowerShell 窗口：

```powershell
ipconfig | findstr "IPv4"
```

记下显示的 IP 地址，例如：`192.168.1.100`

#### 2. 配置手机 App

1. 在手机上打开超声波采集 App
2. 在设置中输入：
   - 服务器 IP：`192.168.1.100`（使用你的实际 IP）
   - 端口：`6666`（Netty 服务器端口）
3. 点击"连接"按钮


### 连接雷达设备

1. 通过 USB 连接 TI mmWave 雷达到 PC
2. 确保 DCA1000 电源已接通
3. 启动 mmWave Studio
4. 完成毫米波雷达配置步骤

---

## 功能测试

### 测试 1：AudioClient 基础功能测试

打开 MATLAB，运行：

```matlab
cd E:\ScreenDataCapture\Multimodal_data_capture\matlab_client
test_audio_client
```

**测试流程：**

1. **连接服务器测试**
   - 输入服务器 IP（本机测试用 `127.0.0.1`）
   - 预期：✅ 服务器连接成功

2. **获取设备列表**
   - 预期：✅ 显示已连接设备数量（至少 1 个）
   - 如果为 0：检查手机 App 是否已连接

3. **时间同步测试**
   - 预期：✅ 显示时间偏移（毫秒）
   - 理想值：偏移量 < 100ms

4. **录制状态查询**
   - 预期：✅ 成功获取录制状态
   - 显示"未录制"状态

5. **录制功能测试**
   - 输入 `y` 开始测试
   - 预期：✅ 录制成功启动
   - 预期：✅ 进度条正常显示
   - 预期：✅ 5 秒后录制完成
   - 预期：✅ 手机端生成测试文件

**所有测试通过示例输出：**
```matlab
========================================
AudioClient 功能测试
========================================

[测试1] 连接服务器测试
---------------------------------------
请输入服务器IP地址（默认: 127.0.0.1）: 
成功连接到服务器: http://127.0.0.1:8080
结果: 通过

[测试2] 获取设备列表
---------------------------------------
已连接设备数: 1
设备 1:
  ID: ac2c7ec2
  型号: HUAWEI/LIO-AL00
  状态: connected
结果: 通过

[测试3] 时间同步
---------------------------------------
时间同步成功
  客户端时间: 1736686234567
  服务器时间: 1736686234570
  时间偏移: 3 ms
结果: 通过

[测试4] 录制状态查询
---------------------------------------
当前录制状态: 未录制
结果: 通过

[测试5] 录制功能测试（可选）
---------------------------------------
是否测试录制功能？(y/n): y
开始 5 秒测试录制...
录制进度: ████████████████████ 100% (5/5 秒)
录制已停止
结果: 通过

========================================
所有测试通过！✓
========================================
```

**如果测试失败：**

| 失败测试 | 可能原因 | 解决方案 |
|---------|---------|---------|
| 测试 1 | AudioCenterServer 未启动 | 检查服务器是否运行 |
| 测试 2 | 手机未连接 | 检查手机 App 连接状态 |
| 测试 3 | 网络延迟过大 | 检查网络连接质量 |
| 测试 5 | 手机存储权限 | 检查 App 权限设置 |

### 测试 2：雷达连接测试

在 MATLAB 中运行：

```matlab
cd E:\ScreenDataCapture\Multimodal_data_capture\matlab_client

% 测试雷达连接
RSTD_DLL_Path = 'C:\ti\mmwave_studio_02_01_01_00\mmWaveStudio\Clients\RtttNetClientController\RtttNetClientAPI.dll';
ErrStatus = Init_RSTD_Connection(RSTD_DLL_Path);

if ErrStatus == 30000
    disp('✅ 雷达连接成功！');
else
    fprintf('❌ 雷达连接失败，错误代码: %d\n', ErrStatus);
end
```

**预期输出：**
```
Adding RSTD Assembly
Waiting for client to connect...
Client connected successfully
✅ 雷达连接成功！
```

**如果连接失败：**
- 检查 USB 连接
- 确认 DCA1000 电源已打开
- 检查 mmWave Studio 是否已运行 `RSTD.NetStart()`
- 验证 DLL 路径是否正确

---

## 完整采集测试

### 单场景测试

在 MATLAB 中运行主控程序：

```matlab
cd E:\ScreenDataCapture\Multimodal_data_capture\matlab_client
main_multimodal_data_capture
```

**预期流程：**

```
========================================
  多模态数据采集系统
========================================

========== 参数验证 ==========
✓ 数据根目录: D:\multimodal_data\
✓ 场景文件: radar/scenes_file.csv
✓ 雷达DLL: 已找到

配置参数:
  - 采集时长: 5 秒
  - 每场景重复: 1 次
  - 雷达延迟: 1050 毫秒
  - 手机延迟: 2200 毫秒

========== 加载场景配置 ==========
✓ 已加载 40 个场景

========== 用户信息 ==========
请输入人员组合（例如 yh-ssk）: test
✓ 人员组合: test
✓ 保存路径: D:\multimodal_data\test

========== 初始化音频客户端 ==========
✓ 成功连接到服务器: http://127.0.0.1:8080
✓ 已连接设备数: 1
✓ 时间同步完成，偏移量: 3 ms

========== 初始化雷达设备 ==========
✓ 雷达连接成功

========================================
  所有系统初始化完成！
========================================

========================================
  开始批量采集
  总场景数: 40
  每场景重复: 1 次
  预计总采集次数: 40
========================================

========================================
场景 1/40
========================================
描述: 静止不动
代码: A0-B0-C0-D0-E0
========================================

---------- 第 1/1 次采集 ----------
输入 y 开始采集，输入 s 跳过: y

场景ID: test-A0-B0-C0-D0-E0-01

开始同步采集...
  [同步] 执行时间同步...
  [同步] 时间偏移: 2.5 ms, RTT: 3.2 ms
  [雷达] 发送雷达启动命令 (提前 1050 ms)
  [音频] 发送音频采集命令 (提前 2200 ms)
  [采集] 正在同步采集数据 (5秒)...
  [停止] 停止采集...
  [验证] 雷达文件: test-A0-B0-C0-D0-E0-01.bin (2.15 MB) ✓
  [完成] 同步采集成功！

✓✓✓ 采集成功！✓✓✓
  [元数据] 已保存: test-A0-B0-C0-D0-E0-01_meta.json

当前统计: 成功 1 / 失败 0 / 总计 1
成功率: 100.00%

是否继续下一个场景？(y/n/q=退出): n

========================================
  采集完成统计
========================================
  总采集次数: 1
  成功次数: 1
  失败次数: 0
  成功率: 100.00%

采集日志已保存: D:\multimodal_data\test\capture_log_20260112_143052.csv
```

### 验证采集结果

#### 1. 检查数据文件

```powershell
cd D:\multimodal_data\test
dir
```

应该看到以下文件：
```
test-A0-B0-C0-D0-E0-01.bin        # 雷达数据（约 2-5 MB）
test-A0-B0-C0-D0-E0-01_meta.json  # 元数据
test-A0-B0-C0-D0-E0-01.wav        # 超声波数据
capture_log_20260112_143052.csv   # 采集日志
```

#### 2. 读取雷达数据

在 MATLAB 中：

```matlab
% 读取雷达数据
cd('E:\ScreenDataCapture\Multimodal_data_capture\matlab_client')
[adcData, fileSize] = readDCA1000('D:\multimodal_data\test\test-A0-B0-C0-D0-E0-01.bin');

fprintf('雷达数据文件大小: %.2f MB\n', fileSize / 1024 / 1024);
fprintf('数据维度: %s\n', mat2str(size(adcData)));
```

**预期输出：**
```
雷达数据文件大小: 2.15 MB
数据维度: [256 640]  % [采样点数, chirp数]
```

#### 3. 查看元数据

```matlab
% 读取元数据
meta_file = 'D:\multimodal_data\test\test-A0-B0-C0-D0-E0-01_meta.json';
fid = fopen(meta_file, 'r', 'n', 'UTF-8');
meta_json = fread(fid, '*char')';
fclose(fid);
metadata = jsondecode(meta_json);

% 显示关键信息
fprintf('人员组合: %s\n', metadata.staff_combination);
fprintf('场景描述: %s\n', metadata.scene_info.intro);
fprintf('SNTP偏移: %.2f ms\n', metadata.sync_quality.sntp_offset_ms);
fprintf('RTT: %.2f ms\n', metadata.sync_quality.rtt_ms);
fprintf('触发时间: %s\n', metadata.sync_quality.trigger_time_readable);
```

---

## 测试通过标准

所有测试通过的标志：

- [x] AudioCenterServer 成功编译和启动
- [x] 手机设备成功连接（设备列表 > 0）
- [x] MATLAB test_audio_client 所有 5 项测试通过
- [x] 雷达连接成功（ErrStatus == 30000）
- [x] 雷达启动延迟已测量并配置
- [x] 完成至少 1 次完整采集
- [x] 生成雷达数据文件（.bin，大小 > 1MB）
- [x] 生成元数据文件（_meta.json）
- [x] 生成采集日志（capture_log_*.csv）
- [x] 数据文件可以正常读取和解析

---

## 快速参考

### 重要端口

| 端口 | 协议 | 用途 |
|------|------|------|
| 6666 | TCP | Android 手机连接（Netty） |
| 8080 | HTTP | MATLAB REST API |
| 1123 | UDP | SNTP 时间同步 |
| 2777 | TCP | mmWave Studio 雷达控制 |

### 常用命令速查

**PowerShell：**
```powershell
# 编译服务器
cd AudioCenterServer
.\gradlew.bat clean build

# 启动服务器
.\gradlew.bat bootRun

# 查看 IP
ipconfig | findstr "IPv4"

# 检查端口
netstat -ano | findstr "6666"
```

**MATLAB：**
```matlab
% 测试 AudioClient
cd matlab_client
test_audio_client

% 测试时间同步
test_sntp_sync

% 测量雷达延迟
test_radar_startup_delay

% 运行主程序
main_multimodal_data_capture
```

---

## 下一步

测试成功后，可以：

1. **批量采集**：修改 `repeat_count` 进行完整采集
2. **场景定制**：编辑 `matlab_client/radar/scenes_file.csv` 添加新场景
3. **数据分析**：使用 `readDCA1000.m` 读取雷达数据进行分析
4. **参数优化**：根据测试结果调整时间同步和触发参数

---


*最后更新：2026年1月12日*
