# 项目结构优化 - 实施总结

**实施日期**: 2024-01-15  
**优化目标**: 文件结构重组 + 深度学习工作流优化

---

## 改动概览

### 1. 文件结构重组

#### 创建新目录
- ✅ `matlab_client/utils/` - MATLAB工具函数库
- ✅ `matlab_client/radar/` - 雷达相关函数
- ✅ `tools/` - Python深度学习工具集

#### 删除目录
- ✅ `mmwave_radar/` - 内容已迁移到`matlab_client/radar/`

#### 文件迁移

**雷达相关**：
- `Init_RSTD_Connection.m`: `matlab_client/` → `matlab_client/radar/`
- `readDCA1000.m`: `mmwave_radar/` → `matlab_client/radar/`
- `scenes_file.csv`: `mmwave_radar/` → `matlab_client/radar/`

**元数据保存**：
- `saveMetadata.m`: 修复Git冲突，保留在`matlab_client/`

---

### 2. 新增MATLAB工具函数

位置: `matlab_client/utils/`

| 文件 | 功能 | 用途 |
|------|------|------|
| `getSceneEnglishName.m` | 场景代码转英文名 | 语义化文件命名 |
| `saveMetadataV2.m` | 新版元数据保存 | 层次化数据组织（推荐）|
| `createDatasetInfo.m` | 生成数据集元信息 | 统计信息和配置 |
| `createScenesInfo.m` | 生成场景映射表 | 40个场景的详细说明 |
| `reorganizeData.m` | 数据格式转换 | 旧数据→新格式 |

---

### 3. 新增Python工具

位置: `tools/`

| 文件 | 功能 | 依赖 |
|------|------|------|
| `multimodal_dataloader.py` | PyTorch DataLoader | torch, numpy, soundfile |
| `split_dataset.py` | 数据集分割 | - |
| `verify_dataset.py` | 数据完整性验证 | numpy, soundfile |

---

### 4. 数据格式优化

#### 新格式（推荐）：层次化组织

```
dataset_root/
├── dataset_info.json          # 数据集统计信息
├── scenes_info.json           # 场景映射表
├── train.txt, val.txt, test.txt  # 数据集分割
└── subjects/
    └── subject_XXX/
        ├── samples_metadata.json   # 被试统一元数据
        ├── radar/*.bin
        └── audio/*.wav
```

**优势**：
- ✅ 一个被试一个JSON（vs 每个样本一个JSON）
- ✅ 语义化文件命名（`sample_001_front_static_above_static_idle.bin`）
- ✅ 标准train/val/test分割
- ✅ 适配深度学习DataLoader

#### 旧格式（兼容）：平铺结构

所有文件在`audio/`目录下，每个样本独立JSON元数据。

---

### 5. 文档更新

#### README.md
- ✅ 新增"数据格式"章节，介绍层次化组织
- ✅ 新增MATLAB工具函数使用说明
- ✅ 新增Python DataLoader使用示例
- ✅ 更新项目结构图
- ✅ 更新场景编码说明

#### SETUP_AND_TEST.md
- ✅ 更新`scenes_file.csv`路径引用
- ✅ 更新目录路径说明

#### 新增文档
- ✅ `matlab_client/utils/README.md` - MATLAB工具使用指南
- ✅ `tools/README.md` - Python工具使用指南

---

## 使用指南

### 新项目（推荐流程）

1. **采集时直接使用新格式**
```matlab
% 在main_multimodal_data_capture.m中使用
saveMetadataV2(subjectId, sampleId, sceneCode, syncInfo, captureTime, dataRoot);
```

2. **生成数据集信息**
```matlab
createDatasetInfo('E:\data\');
createScenesInfo('E:\data\scenes_info.json');
```

3. **数据集分割**
```bash
python tools/split_dataset.py --root E:/data/subjects --strategy subject --ratios 0.7 0.15 0.15
```

4. **训练模型**
```python
from tools.multimodal_dataloader import MultimodalDataset
dataset = MultimodalDataset(root_dir='E:/data/subjects', split='train', split_file='E:/data/train.txt')
```

---

### 旧数据迁移

1. **转换数据格式**
```matlab
reorganizeData('E:\old_data\', 'E:\new_data\');
```

2. **验证数据**
```bash
python tools/verify_dataset.py --root E:/new_data/subjects
```

3. **后续步骤同上**

---

## 路径更新总结

| 原路径 | 新路径 | 状态 |
|--------|--------|------|
| `mmwave_radar/scenes_file.csv` | `matlab_client/radar/scenes_file.csv` | ✅ 已迁移 |
| `mmwave_radar/readDCA1000.m` | `matlab_client/radar/readDCA1000.m` | ✅ 已迁移 |
| `matlab_client/Init_RSTD_Connection.m` | `matlab_client/radar/Init_RSTD_Connection.m` | ✅ 已迁移 |
| `matlab_client/saveMetadata.m` | `matlab_client/saveMetadata.m` | ✅ 已修复Git冲突 |

**main_multimodal_data_capture.m中的路径更新**：
```matlab
% 旧: scenes_csv_file = '../mmwave_radar/scenes_file.csv';
% 新: scenes_csv_file = 'radar/scenes_file.csv';
```

---

## 排除的文件

按照用户要求，以下文件**未包含**在优化中：

- ❌ `test_radar_startup_delay.m` - 雷达延迟由采集员手动设置，不需要自动化测试工具

---

## 兼容性说明

- ✅ **向后兼容**：旧的平铺数据格式仍然可用
- ✅ **渐进式迁移**：可以先使用旧格式采集，后用`reorganizeData.m`转换
- ✅ **文档完整**：两种格式的使用方法都有说明

---

## 下一步建议

### 短期（立即可用）
1. 使用新工具采集少量样本测试
2. 验证Python DataLoader正常工作
3. 尝试数据集分割和训练流程

### 中期（项目进展中）
1. 批量转换现有旧数据（如有）
2. 建立标准化数据采集流程
3. 编写模型训练脚本

### 长期（项目优化）
1. 添加数据增强策略
2. 优化DataLoader性能（缓存、多进程）
3. 建立模型评估基准

---

## 依赖清单

### MATLAB
- MATLAB R2019b+
- 无额外工具箱需求

### Python
```bash
pip install torch numpy soundfile
```

---

## 参考文档

- [README.md](readme.md) - 项目总览和快速开始
- [matlab_client/utils/README.md](matlab_client/utils/README.md) - MATLAB工具详细说明
- [tools/README.md](tools/README.md) - Python工具详细说明
- [SETUP_AND_TEST.md](SETUP_AND_TEST.md) - 完整安装配置指南

---

**✅ 优化完成！项目结构更清晰，深度学习工作流更友好。**

*最后更新：2024-01-15*
