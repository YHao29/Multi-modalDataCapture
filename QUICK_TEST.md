# 快速测试步骤

## 当前状态
已完成编译 AudioCenterServer，可以开始测试。

## 重要说明：端口配置
- **端口 6666**：Netty 服务器，用于手机设备连接
- **端口 8080**：REST API，用于 MATLAB 客户端调用

## 测试步骤

### 步骤 1：启动 AudioCenterServer
在当前 PowerShell 窗口运行：
```powershell
cd E:\ScreenDataCapture\Multimodal_data_capture\AudioCenterServer
.\gradlew.bat bootRun
```

等待看到以下提示：
```
========================================
Auto-starting Netty server on port 6666...
Netty server started successfully!
REST API available at http://localhost:8080/api
========================================
Started Main
```

保持窗口打开。

### 步骤 2：获取 PC IP 地址
打开新的 PowerShell 窗口：
```powershell
ipconfig | findstr "IPv4"
```
记下显示的 IP 地址（例如：192.168.1.100）

### 步骤 3：连接手机设备
1. 在手机上打开超声波采集 App
2. 输入 PC 的 IP 和端口 **6666**（Netty服务器）
3. 点击连接

### 步骤 4：测试 MATLAB 客户端
打开 MATLAB，运行：
```matlab
cd E:\ScreenDataCapture\Multimodal_data_capture\matlab_client
test_audio_client
```

输入服务器 IP（本机测试可以用 127.0.0.1），MATLAB 会自动连接到端口 8080（REST API）。

### 步骤 5：测试完整采集（可选）
如果前面测试都通过，可以运行：
```matlab
cd E:\ScreenDataCapture\Multimodal_data_capture\mmwave_radar

% 先修改配置
edit data_collection_integrated.m
% 修改 server_ip = '你的实际IP'
% 修改 data_path = '你的数据路径'

% 然后运行
data_collection_integrated
```

## 常见问题

1. **MATLAB 连接超时**
   - 确认 AudioCenterServer 已启动
   - 本机测试使用 127.0.0.1
   - MATLAB 连接的是端口 8080（REST API），不是 6666
   
2. **手机无法连接**
   - 手机连接的是端口 6666（Netty 服务器）
   - 确认 PC 和手机在同一网络
   - 临时关闭 Windows 防火墙测试
   
3. **端口冲突**
   - 端口 6666：修改 Main.java 中的 startServer(6666)
   - 端口 8080：修改 application.properties 中的 server.port
   
4. **MATLAB 找不到文件**
   - 确认工作目录正确
   - 使用 `pwd` 查看当前目录
   - 使用 `cd` 切换到正确目录

详细测试步骤请参考 TEST_GUIDE.md
