# MATLAB工具函数库

本目录包含用于数据处理和深度学习工作流的MATLAB工具函数。

## 设计原则

1. **严格按照CSV文件中的场景描述** - 不硬编码场景信息
2. **支持CSV动态修改** - 场景增删改不影响工具函数
3. **保证可扩展性** - 新场景自动支持

## 工具函数列表

### 1. loadScenesFromCSV.m
**从CSV文件加载场景映射表**

**功能**：读取`scenes_file.csv`，返回场景代码到场景信息的映射

**使用**：
```matlab
scenesMap = loadScenesFromCSV('radar/scenes_file.csv');
sceneInfo = scenesMap('A1-B1-C1-D1-E1');
disp(sceneInfo.intro);  % 输出: 合法用户静坐，窥视者1.0米-0°-静止站立
```

**返回值**：`containers.Map`，key为场景代码，value为结构体（包含idx、intro、code字段）

---

### 2. getSceneInfo.m
**根据场景代码获取场景信息**

**功能**：查询指定场景代码的完整信息（带缓存，避免重复读取CSV）

**使用**：
```matlab
info = getSceneInfo('A1-B1-C1-D1-E1');
disp(info.intro);  % 合法用户静坐，窥视者1.0米-0°-静止站立
disp(info.idx);    % 5
```

**返回值**：结构体
- `code`: 场景代码
- `intro`: 场景描述（直接从CSV读取）
- `idx`: 场景索引号

---

### 3. saveMetadataV2.m
**新版元数据保存函数（推荐使用）**

**功能**：将样本元数据保存到被试级别的统一JSON文件中

**设计亮点**：
- 场景描述直接从CSV文件读取，不硬编码
- 文件命名使用场景代码（如`sample_005_A1_B1_C1_D1_E1.bin`），保证唯一性
- 支持CSV文件动态修改

**使用**：
```matlab
% 构建同步质量信息
syncInfo = struct();
syncInfo.ntp_offset_ms = 2.5;
syncInfo.audio_start_time = '2024-01-15 10:30:00.123';
syncInfo.radar_start_time = '2024-01-15 10:30:00.138';

% 保存元数据
saveMetadataV2(subjectId, sampleId, sceneCode, syncInfo, captureTime, dataRoot);

% 示例：
saveMetadataV2(1, 5, 'A1-B1-C1-D1-E1', syncInfo, '2024-01-15 10:30:00', 'E:\data\');
```

**生成的JSON结构**：
```json
{
  "subject_id": 1,
  "num_samples": 5,
  "samples": [
    {
      "sample_id": 5,
      "scene": {
        "code": "A1-B1-C1-D1-E1",
        "intro": "合法用户静坐，窥视者1.0米-0°-静止站立",
        "idx": 5
      },
      "radar_file": "radar/sample_005_A1_B1_C1_D1_E1.bin",
      "audio_file": "audio/sample_005_A1_B1_C1_D1_E1.wav",
      ...
    }
  ]
}
```

---

### 4. createDatasetInfo.m
**生成数据集元信息文件**

**功能**：扫描整个数据集，统计样本数、场景分布等信息

**使用**：
```matlab
createDatasetInfo('E:\data\', 'E:\data\dataset_info.json');
```

---

### 5. createScenesInfo.m
**从CSV生成场景信息JSON文件**

**功能**：读取CSV文件，生成包含所有场景详细信息的JSON

**设计亮点**：
- 直接从CSV读取，不硬编码
- 包含完整的场景编码说明

**使用**：
```matlab
createScenesInfo('E:\data\scenes_info.json', 'radar/scenes_file.csv');
```

---

### 6. reorganizeData.m
**数据格式转换工具**

**功能**：将旧的平铺数据格式转换为新的层次化结构

**使用**：
```matlab
reorganizeData('E:\old_data\', 'E:\new_data\', 'radar/scenes_file.csv');
```

---

## 场景编码说明

根据`scenes_file.csv`，场景代码格式为：`A{用户状态}-B{用户动作}-C{窥视者距离}-D{窥视者角度}-E{窥视者行为}`

| 代码 | 含义 | 取值 |
|------|------|------|
| A | 用户状态 | 0=环境基线, 1=合法用户存在 |
| B | 用户动作 | 0=无, 1=静坐, 2=打字, 3=轻微摇晃 |
| C | 窥视者距离 | 0=无, 1=1.0米, 2=2.0米 |
| D | 窥视者角度 | 0=无, 1=0°, 2=60° |
| E | 窥视者行为 | 0=无, 1=静止站立, 2=慢速路过, 3=靠近并驻足, 4=正常路过 |

**示例**：
- `A0-B0-C0-D0-E0` = 环境基线
- `A1-B1-C0-D0-E0` = 合法用户静坐
- `A1-B1-C1-D1-E1` = 合法用户静坐，窥视者1.0米-0°-静止站立

---

## 如何扩展场景

1. **编辑CSV文件**：在`radar/scenes_file.csv`中添加/修改/删除场景
2. **无需修改代码**：工具函数会自动读取最新的CSV内容
3. **重新生成场景信息**：运行`createScenesInfo()`更新JSON文件

---

*最后更新：2026-01-13*
