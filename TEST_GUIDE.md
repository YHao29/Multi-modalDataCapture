# 多模态数据采集系统 - 测试验证指南

## 测试环境准备

### 1. 硬件准备
- [ ] PC 电脑（Windows，已安装 MATLAB 和 Java）
- [ ] TI 毫米波雷达开发板（已连接到 PC）
- [ ] Android 手机（已安装超声波采集 App）
- [ ] 确保 PC 和手机在同一局域网

### 2. 软件准备
- [ ] MATLAB R2019b 或更高版本
- [ ] Java JDK 17 或更高版本
- [ ] TI mmWave Studio 已安装
- [ ] AudioCenterServer 已编译

## 第一步：编译 AudioCenterServer

### 1.1 进入项目目录
```powershell
cd E:\ScreenDataCapture\Multimodal_data_capture\AudioCenterServer
```

### 1.2 清理并构建
```powershell
gradlew.bat clean build
```

**预期结果：**
- 看到 "BUILD SUCCESSFUL" 消息
- 在 build/libs/ 目录生成 JAR 文件

**如果失败：**
- 检查 Java 版本：`java -version`（应为 17+）
- 确保使用 `gradlew.bat` 而非 `gradle`
- 查看错误信息并根据提示修复

### 1.3 启动服务器
```powershell
gradlew.bat bootRun
```

**预期结果：**
- 看到 Spring Boot 启动日志
- 最后显示 "Started Main in X.XXX seconds"
- 服务器监听在端口 6666

**保持此终端窗口打开！**

## 第二步：连接手机设备

### 2.1 获取 PC 的 IP 地址
在新的 PowerShell 窗口运行：
```powershell
ipconfig | findstr "IPv4"
```

记录显示的 IP 地址，例如：`192.168.1.100`

### 2.2 配置手机 App
1. 打开手机上的超声波采集 App
2. 在设置中输入：
   - 服务器 IP：`192.168.1.100`（使用您的实际 IP）
   - 端口：`6666`
3. 点击"连接"按钮

### 2.3 验证连接
在 AudioCenterServer 的控制台应该看到：
- 新设备连接的日志
- 设备注册信息

## 第三步：测试 MATLAB 客户端

### 3.1 运行测试脚本
打开 MATLAB，运行：
```matlab
cd E:\ScreenDataCapture\Multimodal_data_capture\matlab_client
test_audio_client
```

### 3.2 测试检查清单

#### 测试 1：连接服务器
- [ ] 输入服务器 IP（或使用默认值）
- [ ] 看到"服务器连接成功"消息

**如果失败：**
- 检查 AudioCenterServer 是否运行
- 验证 IP 地址是否正确
- 检查防火墙是否阻止端口 6666

#### 测试 2：获取设备列表
- [ ] 显示已连接设备数量
- [ ] 至少有 1 个设备

**如果设备数为 0：**
- 检查手机 App 是否已连接
- 在 AudioCenterServer 控制台查看设备状态

#### 测试 3：时间同步
- [ ] 显示时间偏移（毫秒）
- [ ] 偏移量小于 100ms（理想情况）

**如果偏移过大：**
- 检查网络延迟
- 确保 PC 和手机在同一局域网
- 尝试重新连接手机

#### 测试 4：录制状态查询
- [ ] 成功获取录制状态
- [ ] 显示"未录制"状态

#### 测试 5：录制功能测试
- [ ] 输入 'y' 开始测试
- [ ] 录制成功启动
- [ ] 进度条正常显示
- [ ] 5 秒后录制完成
- [ ] 手机端生成测试文件

**验证手机端：**
- 打开手机文件管理器
- 查找 `test-recording` 相关文件
- 确认文件大小合理（不为 0）

## 第四步：测试雷达连接

### 4.1 运行雷达测试
在 MATLAB 中：
```matlab
cd E:\ScreenDataCapture\Multimodal_data_capture\mmwave_radar

% 测试雷达连接
RSTD_DLL_Path = 'C:\ti\mmwave_studio_02_01_01_00\mmWaveStudio\Clients\RtttNetClientController\RtttNetClientAPI.dll';
ErrStatus = Init_RSTD_Connection(RSTD_DLL_Path);

if ErrStatus == 30000
    disp('雷达连接成功！');
else
    fprintf('雷达连接失败，错误代码: %d\n', ErrStatus);
end
```

### 4.2 检查清单
- [ ] 雷达通过 USB 连接到 PC
- [ ] DCA1000 电源已打开
- [ ] mmWave Studio 路径正确
- [ ] 看到"雷达连接成功"消息

## 第五步：运行完整采集测试

### 5.1 准备数据目录
```powershell
# 创建数据目录
New-Item -Path "D:\multimodal_data\office\" -ItemType Directory -Force
```

### 5.2 修改配置
编辑 `data_collection_integrated.m` 的前几行：
```matlab
% 修改为您的实际配置
data_path = 'D:\multimodal_data\';
dir_name = 'office\';
server_ip = '192.168.1.100';  % 使用您的实际 IP
```

### 5.3 运行完整采集
```matlab
cd E:\ScreenDataCapture\Multimodal_data_capture\mmwave_radar
data_collection_integrated
```

### 5.4 采集流程验证

#### 初始化阶段
- [ ] 音频客户端初始化成功
- [ ] 检测到已连接的手机设备
- [ ] 时间同步完成
- [ ] 输入被试编号（例如：S01）
- [ ] 被试编号确认
- [ ] 场景配置加载成功
- [ ] 雷达连接成功

#### 采集阶段（第一个场景）
- [ ] 显示场景信息
- [ ] 输入 'y' 确认采集
- [ ] 看到"同步触发采集开始"
- [ ] 音频采集启动成功
- [ ] 雷达采集启动成功
- [ ] 进度条显示 1-10 秒
- [ ] 采集完成提示

#### 数据验证
检查数据目录 `D:\multimodal_data\office\`：
- [ ] 存在 `subject_log.txt` 文件
- [ ] 存在 `.bin` 雷达数据文件
- [ ] 存在 `.wav` 或 `.pcm` 音频文件
- [ ] 文件名格式正确：`1-场景代码-01`

### 5.5 数据完整性检查
```matlab
% 读取雷达数据
radar_file = 'D:\multimodal_data\office\1-standing-01.bin';
[adcData, fileSize] = readDCA1000(radar_file);

fprintf('雷达数据文件大小: %d MB\n', fileSize / 1024 / 1024);
fprintf('数据维度: %s\n', mat2str(size(adcData)));

% 检查音频文件
audio_file = 'D:\multimodal_data\office\1-standing-01.wav';
if exist(audio_file, 'file')
    info = audioinfo(audio_file);
    fprintf('音频文件时长: %.2f 秒\n', info.Duration);
    fprintf('采样率: %d Hz\n', info.SampleRate);
else
    warning('未找到音频文件');
end
```

## 常见问题排查

### 问题 1：AudioCenterServer 编译失败
**症状：** BUILD FAILED
**解决：**
```powershell
# 检查 Java 版本
java -version

# 如果版本低于 17，安装 JDK 17+
# 确保使用项目的 gradlew.bat
cd E:\ScreenDataCapture\Multimodal_data_capture\AudioCenterServer
.\gradlew.bat --version
```

### 问题 2：手机无法连接
**症状：** 设备列表为空
**解决：**
1. 确认 PC 和手机在同一网络
2. 检查防火墙设置
3. 尝试关闭 Windows 防火墙测试
4. 使用 `ipconfig` 确认 IP
5. 重启 AudioCenterServer

### 问题 3：时间同步偏移过大
**症状：** offset > 100ms
**解决：**
1. 检查网络延迟：`ping 手机IP`
2. 确保没有使用 VPN
3. 使用有线网络而非 Wi-Fi
4. 减少同一网络上的其他设备

### 问题 4：雷达连接失败
**症状：** ErrStatus != 30000
**解决：**
1. 检查 USB 连接
2. 确认 DCA1000 电源
3. 验证 mmWave Studio 安装
4. 检查 DLL 路径是否正确
5. 重启雷达设备

### 问题 5：录制启动但无数据文件
**症状：** 采集完成但找不到文件
**解决：**
1. 检查手机存储权限
2. 查看 AudioCenterServer 日志
3. 确认数据路径存在
4. 检查手机存储空间

## 测试通过标准

所有测试通过的标志：
- [x] AudioCenterServer 成功编译和启动
- [x] 手机设备成功连接
- [x] MATLAB 客户端所有 5 项测试通过
- [x] 雷达连接成功
- [x] 完成至少 1 次完整采集
- [x] 生成雷达和音频数据文件
- [x] 文件可以正常读取和解析

## 下一步

测试通过后，可以：
1. 编辑 `scenes_file.csv` 定义实际采集场景
2. 修改采集参数（时长、重复次数等）
3. 开始正式的数据采集工作
4. 如需更高精度时间同步，继续实现任务二（NTP 模块）
