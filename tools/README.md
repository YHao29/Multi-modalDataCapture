# Python深度学习工具集

本目录包含用于深度学习训练的Python工具，支持PyTorch框架。

## 工具列表

### 1. multimodal_dataloader.py
**PyTorch数据加载器**

加载多模态（雷达+音频）数据，支持批量训练。

#### 主要功能
- 自动解析层次化数据结构
- 读取雷达二进制文件（.bin）和音频文件（.wav）
- 支持train/val/test分割
- 返回PyTorch Tensor格式
- 场景代码自动映射为分类标签

#### 使用示例

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
    
    # 前向传播
    outputs = model(radar, audio)
    loss = criterion(outputs, labels)
    
    # 反向传播
    loss.backward()
    optimizer.step()
```

#### 配置参数

```python
dataset = MultimodalDataset(
    root_dir='E:/data/subjects',      # 数据集根目录
    split='train',                     # 'train', 'val', 或 'test'
    split_file='E:/data/train.txt',   # 分割文件路径
    radar_config={                     # 雷达配置
        'num_rx': 4,
        'num_chirps': 128,
        'num_samples': 256,
        'dtype': np.int16
    },
    audio_config={                     # 音频配置
        'sample_rate': 44100,
        'ultrasonic_freq': 20000
    },
    transform=None                     # 可选的数据增强
)
```

#### 返回数据格式

每个样本是一个字典：
```python
{
    'radar': Tensor,        # shape (256, 128, 4) - [samples, chirps, RX]
    'audio': Tensor,        # shape (audio_samples,) or (audio_samples, channels)
    'label': int,           # 场景分类标签 (0-39)
    'scene_code': str,      # 场景代码，如 'A1-B1-C1-D1-E1'
    'scene_intro': str,     # 场景描述（直接从CSV读取），如 '合法用户静坐，窥视者1.0米-0°-静止站立'
    'scene_idx': int,       # 场景索引号
    'subject_id': int,      # 被试ID
    'sample_id': int        # 样本ID
}
```

**设计原则**：
- 场景描述直接从CSV文件读取，不硬编码
- 支持CSV文件动态修改（增删改场景）
- 保证数据采集的可扩展性

---

### 2. split_dataset.py
**数据集分割工具**

生成train/val/test分割文件。

#### 使用方法

```bash
# 按被试分割（推荐，避免数据泄露）
python split_dataset.py \
    --root E:/data/subjects \
    --strategy subject \
    --ratios 0.7 0.15 0.15 \
    --seed 42

# 按样本随机分割
python split_dataset.py \
    --root E:/data/subjects \
    --strategy sample \
    --ratios 0.7 0.15 0.15
```

#### 参数说明

- `--root`: 数据集根目录（包含subjects/文件夹）
- `--output`: 输出目录（默认为root的父目录）
- `--strategy`: 分割策略
  - `subject`（推荐）：按被试分割，确保同一被试的样本在同一分割集中
  - `sample`：按样本随机分割
- `--ratios`: train/val/test比例（必须和为1.0）
- `--seed`: 随机种子（确保可重复性）

#### 输出文件

生成三个文本文件，每行一个样本路径：

**train.txt**
```
subject_001/sample_001_front_static_above_static_idle
subject_001/sample_002_front_static_above_moving_idle
subject_002/sample_001_front_static_left_static_idle
...
```

#### 统计信息

运行后会显示详细统计：
```
========== 数据集分割统计 ==========
总样本数: 600
训练集: 420 (70.0%)
验证集: 90 (15.0%)
测试集: 90 (15.0%)

被试分布:
训练集被试: 7
验证集被试: 2
测试集被试: 1

场景分布:
训练集场景数: 40
验证集场景数: 38
测试集场景数: 35
```

---

### 3. verify_dataset.py
**数据完整性验证工具**

检查数据集的完整性和一致性，生成验证报告。

#### 使用方法

```bash
# 基本验证
python verify_dataset.py \
    --root E:/data/subjects \
    --output verification_report.txt

# 详细输出
python verify_dataset.py \
    --root E:/data/subjects \
    --verbose

# 计算MD5校验和（耗时）
python verify_dataset.py \
    --root E:/data/subjects \·
    --check-md5
```

#### 验证项目
·
1. **文件存在性**：检查元数据中引用的文件是否存在
2. **雷达文件格式**：验证.bin文件大小是否符合配置
3. **音频文件格式**：验证.wav文件时长和采样率
4. **元数据一致性**：检查JSON文件格式

#### 验证报告示例

```
============================================================
数据集验证报告
============================================================

总体统计:
  被试数: 10
  样本数: 600
  有效样本: 598
  无效样本: 2
  有效率: 99.67%

场景分布 (共 40 种场景):
  E0-A0-B0-C0: 15 个样本
  E0-A1-B0-C0: 15 个样本
  ...

被试样本数:
  subject_001: 60 个样本
  subject_002: 60 个样本
  ...

错误列表 (共 2 个):
  ✗ [subject_003/sample_025] 文件不存在: E:/data/.../sample_025.bin
  ✗ [subject_007/sample_012] 雷达数据长度不匹配: 期望 131072, 实际 130000

✓ 验证完成！
============================================================
```

---

### 4. visualize_data.py
**数据验证与可视化工具**

用于测试阶段验证毫米波雷达和音频数据质量，生成可视化图表。

#### 主要功能

1. **雷达数据可视化**
   - Range-Doppler图（多普勒-距离图）
   - Range-Time图（时间-距离图）
   - 各通道Range Profile对比
   - 原始ADC波形（I/Q分量）
   - 信号功率时间序列
   - 各RX通道对比

2. **音频数据可视化**
   - 时域波形
   - 全频段频谱图（0-22kHz）
   - 超声波频段频谱图（18-22kHz）
   - 全频段频谱（FFT）
   - 超声波频段放大分析

3. **数据质量验证**
   - 文件完整性检查
   - 信号幅度分析
   - 通道一致性验证
   - 超声波能量比分析
   - 信噪比计算
   - 削波检测

#### 使用方法

```bash
# 1. 验证单个样本（自动匹配文件）
python visualize_data.py --radar sample_001.bin --audio sample_001.wav

# 2. 从元数据读取指定样本
python visualize_data.py --dir E:/data/subjects/subject_001 --sample 1

# 3. 批量验证所有样本
python visualize_data.py --dir E:/data/subjects --batch --report validation_report.json

# 4. 保存可视化结果
python visualize_data.py --radar sample.bin --audio sample.wav --save ./output
```

#### 配置参数

工具支持从元数据JSON自动读取配置，也可以使用默认配置：

**默认雷达配置：**
```python
{
    "num_rx": 4,           # 接收天线数量
    "num_chirps": 128,     # chirp数量
    "num_samples": 256,    # ADC采样点数
    "dtype": np.int16      # 数据类型
}
```

**默认音频配置：**
```python
{
    "sample_rate": 44100,       # 采样率
    "ultrasonic_freq": 20000,   # 超声波中心频率
    "ultrasonic_bw": 2000       # 超声波带宽
}
```

#### 输出示例

**单样本验证输出：**
```
============================================================
数据验证与可视化
============================================================

[1/4] 加载雷达数据: sample_001_A1_B1_C1_D1_E1.bin
  ✓ 加载成功: 0.50 MB, 128 chirps

[2/4] 加载音频数据: sample_001_A1_B1_C1_D1_E1.wav
  ✓ 加载成功: 5.00s, 44100 Hz

[3/4] 验证数据质量

  雷达数据验证:
    平均幅度: 1523.45
    最大幅度: 12847.00

  音频数据验证:
    时长: 5.00s
    超声波能量比: 0.0345
    超声波SNR: 12.3 dB

[4/4] 生成可视化图表
雷达数据可视化已保存: ./output/radar_analysis.png
音频数据可视化已保存: ./output/audio_analysis.png

============================================================
验证完成!
============================================================
```

**批量验证报告（JSON）：**
```json
{
  "timestamp": "2026-01-13T14:30:00",
  "root_dir": "E:/data/subjects",
  "total_subjects": 10,
  "total_samples": 600,
  "valid_samples": 598,
  "invalid_samples": 2,
  "subjects": {
    "subject_001": {
      "valid_count": 60,
      "invalid_count": 0,
      "samples": [...]
    }
  }
}
```

#### 可视化图表内容

**雷达分析图（radar_analysis.png）：**
- 3×3布局，包含9个子图
- 左上：RX0的Range-Doppler图
- 中上：RX0的Range-Time图
- 右上：各通道Range Profile对比
- 左中：原始ADC波形（I/Q分量）
- 中中：信号功率时间序列
- 右中：数据统计信息表
- 下排：RX0/RX1/RX2的Range-Doppler图对比

**音频分析图（audio_analysis.png）：**
- 3×3布局，包含6个子图
- 左上2格：时域波形（完整）
- 左中2格：全频段频谱图（0-22kHz）
- 左下2格：超声波频段频谱图（18-22kHz）
- 右上：全频段FFT频谱
- 右中：超声波频段放大频谱
- 右下：音频统计信息表

#### 依赖库

```bash
pip install numpy matplotlib soundfile scipy
```

#### 常见问题

**Q: 中文字体显示为方块？**
A: 安装SimHei或Microsoft YaHei字体，或修改代码中的`plt.rcParams["font.sans-serif"]`

**Q: 雷达数据解析失败？**
A: 检查`num_samples`和`num_chirps`配置是否与实际硬件一致

**Q: 超声波能量比过低？**
A: 可能音频录制未启用超声波模式，检查AudioCenterServer配置

**Q: 可视化图表不显示？**
A: 使用`--save`参数保存到文件，或在服务器环境配置X11转发

---

## 完整工作流

### 1. 准备数据

使用MATLAB工具采集或转换数据：

```matlab
% MATLAB中
% 方式1: 使用新版采集函数
saveMetadataV2(subjectId, sampleId, sceneCode, syncInfo, captureTime, dataRoot);

% 方式2: 转换旧数据
reorganizeData('E:\old_data\', 'E:\new_data\');
createDatasetInfo('E:\new_data\');
```

### 2. 验证数据

```bash
python verify_dataset.py --root E:/data/subjects --verbose
```

### 3. 划分数据集

```bash
python split_dataset.py \
    --root E:/data/subjects \
    --strategy subject \
    --ratios 0.7 0.15 0.15
```

### 4. 训练模型

```python
from multimodal_dataloader import MultimodalDataset
from torch.utils.data import DataLoader

# 加载数据
train_dataset = MultimodalDataset(
    root_dir='E:/data/subjects',
    split='train',
    split_file='E:/data/train.txt'
)

train_loader = DataLoader(
    train_dataset,
    batch_size=32,
    shuffle=True,
    num_workers=4
)

# 训练循环
for epoch in range(num_epochs):
    for batch in train_loader:
        # 训练代码
        pass
```

---

## 依赖安装

```bash
pip install torch numpy soundfile scipy matplotlib
```

或使用requirements.txt：

```bash
cd tools
pip install -r requirements.txt
```

**requirements.txt内容**：
```
torch>=1.10.0
numpy>=1.21.0
soundfile>=0.11.0
scipy>=1.7.0
matplotlib>=3.3.0
```

---

## 数据格式要求

Python工具期望以下目录结构：

```
root_dir/
├── subjects/
│   ├── subject_001/
│   │   ├── samples_metadata.json
│   │   ├── radar/
│   │   │   └── *.bin
│   │   └── audio/
│   │       └── *.wav
│   └── ...
└── (train.txt, val.txt, test.txt)
```

如果数据不符合此格式，使用MATLAB工具`reorganizeData.m`转换。

---

## 常见问题

### Q: DataLoader加载速度慢
A: 增加`num_workers`参数（如设为4-8），使用多进程加载

### Q: 内存不足
A: 减小`batch_size`，或者实现数据预处理缓存

### Q: 雷达数据维度错误
A: 检查`radar_config`参数是否与实际硬件配置一致

### Q: 分割文件路径错误
A: 确保`split_file`使用绝对路径

---

## 自定义数据增强

可以通过`transform`参数添加数据增强：

```python
def custom_transform(item):
    # item是包含'radar'和'audio'的字典
    radar = item['radar']
    audio = item['audio']
    
    # 添加噪声
    radar += torch.randn_like(radar) * 0.01
    
    # 归一化
    radar = (radar - radar.mean()) / radar.std()
    
    item['radar'] = radar
    return item

dataset = MultimodalDataset(
    root_dir='E:/data/subjects',
    split='train',
    transform=custom_transform
)
```

---

*最后更新：2024-01-15*
