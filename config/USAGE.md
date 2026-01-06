# 多模态数据采集系统 - 使用说明

## 服务器启动模式说明

AudioCenterServer 现已支持自动启动模式，无需手动输入 `server start` 命令。

### 自动启动模式（推荐用于数据采集）

启动服务器时会自动启动两个服务：
- **Netty 服务器**（端口 6666）：用于手机设备连接
- **REST API**（端口 8080）：用于 MATLAB 客户端调用

```bash
cd AudioCenterServer
gradlew.bat bootRun
```

启动成功后会看到：
```
========================================
Auto-starting Netty server on port 6666...
Netty server started successfully!
REST API available at http://localhost:8080/api
========================================
Started Main in X.XXX seconds
```

此时可以：
- 手机直接连接到 `<PC_IP>:6666`
- MATLAB 通过 `http://localhost:8080/api` 调用接口
- 命令行输入 shell 命令（如 `device list`）进行调试

### 手动控制模式（可选）

如果需要手动控制 Netty 服务器启停，仍可使用原有命令：

```bash
audio-center:> server stop   # 停止 Netty 服务器
audio-center:> server start  # 重新启动 Netty 服务器
audio-center:> device list   # 查看已连接设备
```

注：REST API 始终运行，不受 Netty 服务器启停影响。

## 快速开始

### 1. 启动 AudioCenterServer

```bash
cd AudioCenterServer
gradlew.bat bootRun
```

等待看到启动成功提示。

### 2. 连接手机设备

1. 在手机上打开超声波采集 App
2. 输入 PC 的 IP 地址和端口 6666
3. 点击连接

### 3. 修改配置

编辑 `config/system_config.json` 或直接在 MATLAB 脚本中修改：

- `server_ip`: AudioCenterServer 的 IP 地址
- `data_path`: 数据存储路径
- `capture_duration`: 每次采集时长（秒）

### 4. 运行采集程序

```matlab
cd mmwave_radar
data_collection_integrated
```

按提示操作：
1. 输入被试编号
2. 等待系统初始化
3. 对每个场景确认采集
4. 等待采集完成

## 文件说明

### AudioCenterServer
- 自动启动 Netty 服务器（端口 6666）和 REST API（端口 8080）
- 控制手机端录制
- 支持命令行交互式调试
- API 文档见下方

### matlab_client/AudioClient.m
MATLAB 客户端工具类，提供以下方法：
- `AudioClient(ip, port)` - 创建客户端
- `listDevices()` - 列出已连接设备
- `syncTime()` - 同步时间
- `startRecording(sceneId, duration, timestamp)` - 开始录制
- `stopRecording()` - 停止录制
- `getRecordingStatus()` - 获取状态

### mmwave_radar/
- `data_collection_integrated.m` - 集成版主采集程序
- `scenes_file.csv` - 场景配置文件
- `Init_RSTD_Connection.m` - 雷达初始化
- `readDCA1000.m` - 数据读取函数

## REST API 接口

### 录制控制

#### 开始录制
```
POST /api/recording/start
Content-Type: application/json

{
  "scene_id": "1-standing-01",
  "timestamp": 1704441600000,
  "duration": 10
}
```

#### 停止录制
```
POST /api/recording/stop
```

#### 获取录制状态
```
GET /api/recording/status
```

### 设备管理

#### 获取设备列表
```
GET /api/devices/list
```

#### 获取服务器状态
```
GET /api/devices/status
```

### 时间同步

#### 同步时间
```
POST /api/time/sync
Content-Type: application/json

{
  "client_timestamp": 1704441600000
}
```

#### 获取当前时间
```
GET /api/time/current
```

## 数据文件命名规则

格式：`{被试ID}-{场景代码}-{重复次数}.{扩展名}`

示例：
- 雷达数据：`1-standing-01.bin`
- 音频数据：`1-standing-01.wav`

## 故障排除

### AudioCenterServer 无法启动
- 检查 Java 版本（需要 JDK 17+）
- 使用 `gradlew.bat` 而非 `gradle`

### 手机无法连接
- 确认 PC 和手机在同一局域网
- 检查防火墙设置
- 验证 IP 地址和端口正确

### 雷达连接失败
- 确认 mmWave Studio 已安装
- 检查 USB 连接
- 验证 DLL 路径正确

### 时间同步精度低
- 确保局域网稳定
- 减少网络延迟
- 在采集前执行同步

## 联系方式

如有问题请查看项目 README.md 或提交 Issue。
