"""
multimodal_dataloader_v2.py - 多模态数据集加载器（三层场景体系版本）

适配新的三层场景体系和层次化存储结构

数据目录结构:
root_dir/subjects/
  subject_001/
    sample_001_L01_SL01_A1-B1-C1-D1-E1_meta.json
    radar/sample_001_L01_SL01_A1-B1-C1-D1-E1_Raw_0.bin
    audio/sample_001_L01_SL01_A1-B1-C1-D1-E1.wav
"""

import os
import numpy as np
import json
import soundfile as sf
from pathlib import Path
from torch.utils.data import Dataset
import torch


class MultimodalDatasetV2(Dataset):
    """
    多模态数据集（三层场景体系版本）
    
    支持特性:
    - 三层场景信息（大场景、子场景、动作组合）
    - 层次化目录结构
    - 自动解析元数据JSON
    - 雷达和音频数据加载
    """
    
    def __init__(self, root_dir, split='train', split_file=None,
                 radar_config=None, audio_config=None, transform=None):
        """
        Args:
            root_dir: 数据集根目录（包含subjects/）
            split: 'train', 'val', 'test', 或 'all'
            split_file: 分割文件路径（如 train.txt）
            radar_config: 雷达配置字典
            audio_config: 音频配置字典
            transform: 可选的数据增强函数
        """
        self.root_dir = Path(root_dir)
        self.split = split
        self.transform = transform
        
        # 默认雷达配置
        self.radar_config = radar_config or {
            'num_rx': 4,
            'num_chirps': 128,
            'num_samples': 256,
            'dtype': np.int16
        }
        
        # 默认音频配置
        self.audio_config = audio_config or {
            'sample_rate': 44100,
            'ultrasonic_freq': 20000
        }
        
        # 加载样本列表
        self.samples = self._load_samples(split_file)
        
        # 构建场景代码到标签的映射（可选）
        self.scene_to_label = self._build_scene_mapping()
        
        print(f"[{split}] 加载 {len(self.samples)} 个样本")
    
    def _load_samples(self, split_file):
        """从分割文件加载样本路径"""
        samples = []
        
        if split_file and os.path.exists(split_file):
            # 从分割文件加载
            with open(split_file, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if line:
                        # 格式: subject_XXX/sample_YYY_...
                        samples.append(line)
        else:
            # 如果没有分割文件，加载所有样本
            subjects_dir = self.root_dir / 'subjects'
            if not subjects_dir.exists():
                raise ValueError(f"找不到subjects目录: {subjects_dir}")
            
            for subject_dir in sorted(subjects_dir.glob("subject_*")):
                radar_dir = subject_dir / 'radar'
                if not radar_dir.exists():
                    continue
                
                for radar_file in sorted(radar_dir.glob("sample_*.bin")):
                    stem = radar_file.stem.replace("_Raw_0", "")
                    samples.append(f"{subject_dir.name}/{stem}")
        
        return samples
    
    def _build_scene_mapping(self):
        """构建场景代码到标签的映射（示例）"""
        # 这里可以根据需要自定义映射逻辑
        # 简单示例：按动作代码映射
        scene_codes = set()
        for sample_path in self.samples:
            # 从路径中提取动作代码
            parts = sample_path.split('/')[-1].split('_')
            if len(parts) >= 5:
                action_code = parts[-1]  # 最后一部分是动作代码
                scene_codes.add(action_code)
        
        return {code: idx for idx, code in enumerate(sorted(scene_codes))}
    
    def __len__(self):
        return len(self.samples)
    
    def __getitem__(self, idx):
        sample_path = self.samples[idx]
        subject_name, sample_stem = sample_path.split('/')
        
        subject_dir = self.root_dir / 'subjects' / subject_name
        
        # 读取雷达数据
        radar_file = subject_dir / 'radar' / f"{sample_stem}_Raw_0.bin"
        radar_data = self._load_radar(radar_file)
        
        # 读取音频数据
        audio_file = subject_dir / 'audio' / f"{sample_stem}.wav"
        audio_data = self._load_audio(audio_file)
        
        # 读取元数据
        meta_file = subject_dir / f"{sample_stem}_meta.json"
        metadata = self._load_metadata(meta_file)
        
        # 提取场景信息
        location = metadata.get('location', {})
        sub_location = metadata.get('sub_location', {})
        action_scene = metadata.get('action_scene', {})
        
        # 生成标签（可根据需要自定义）
        action_code = action_scene.get('code', '')
        label = self.scene_to_label.get(action_code, -1)
        
        # 构造返回数据
        item = {
            'radar': torch.from_numpy(radar_data).float(),
            'audio': torch.from_numpy(audio_data).float(),
            
            # 三层场景信息
            'location_id': location.get('location_id', ''),
            'location_name': location.get('location_name', ''),
            'sub_location_id': sub_location.get('sub_location_id', ''),
            'sub_location_name': sub_location.get('sub_location_name', ''),
            'action_code': action_code,
            'action_intro': action_scene.get('intro', ''),
            'action_idx': action_scene.get('idx', 0),
            
            # 其他信息
            'subject_id': metadata.get('subject_id', 0),
            'sample_id': metadata.get('capture_config', {}).get('sample_id', 0),
            'label': label,  # 分类标签
            
            # 元信息
            'sample_path': sample_path,
            'metadata': metadata
        }
        
        # 应用数据增强
        if self.transform:
            item = self.transform(item)
        
        return item
    
    def _load_radar(self, filepath):
        """加载雷达二进制数据"""
        if not filepath.exists():
            raise FileNotFoundError(f"雷达文件不存在: {filepath}")
        
        with open(filepath, 'rb') as f:
            data = np.fromfile(f, dtype=self.radar_config['dtype'])
        
        # 重塑为 [samples, chirps, RX]
        num_samples = self.radar_config['num_samples']
        num_chirps = self.radar_config['num_chirps']
        num_rx = self.radar_config['num_rx']
        
        expected_len = num_samples * num_chirps * num_rx
        if len(data) < expected_len:
            # 填充零
            data = np.pad(data, (0, expected_len - len(data)))
        elif len(data) > expected_len:
            # 截断
            data = data[:expected_len]
        
        data = data.reshape(num_samples, num_chirps, num_rx)
        return data.astype(np.float32)
    
    def _load_audio(self, filepath):
        """加载音频数据"""
        if not filepath.exists():
            raise FileNotFoundError(f"音频文件不存在: {filepath}")
        
        audio, sr = sf.read(filepath)
        
        # 确保采样率正确
        expected_sr = self.audio_config['sample_rate']
        if sr != expected_sr:
            print(f"警告: 音频采样率不匹配 ({sr} vs {expected_sr})")
        
        # 转为单声道
        if len(audio.shape) > 1:
            audio = audio.mean(axis=1)
        
        return audio.astype(np.float32)
    
    def _load_metadata(self, filepath):
        """加载元数据JSON"""
        if not filepath.exists():
            return {}
        
        with open(filepath, 'r', encoding='utf-8') as f:
            return json.load(f)
    
    def get_scene_info(self, idx):
        """获取指定样本的场景信息（用于分析）"""
        item = self[idx]
        return {
            'location': f"{item['location_id']} - {item['location_name']}",
            'sub_location': f"{item['sub_location_id']} - {item['sub_location_name']}",
            'action': f"{item['action_code']} - {item['action_intro']}",
            'subject_id': item['subject_id'],
            'sample_id': item['sample_id']
        }


# ===== 使用示例 =====
if __name__ == '__main__':
    from torch.utils.data import DataLoader
    
    print("=" * 60)
    print("多模态数据集加载器测试")
    print("=" * 60)
    
    # 配置
    data_root = 'F:/testData'  # 修改为您的数据路径
    train_split = 'F:/testData/train.txt'
    
    # 创建数据集
    print("\n创建训练集...")
    train_dataset = MultimodalDatasetV2(
        root_dir=data_root,
        split='train',
        split_file=train_split
    )
    
    # 显示场景映射
    print(f"\n场景代码映射: {len(train_dataset.scene_to_label)} 个类别")
    print(f"类别: {list(train_dataset.scene_to_label.keys())[:5]}...")
    
    # 创建DataLoader
    print("\n创建DataLoader...")
    train_loader = DataLoader(
        train_dataset,
        batch_size=4,
        shuffle=True,
        num_workers=0  # Windows上设置为0
    )
    
    # 测试加载一个batch
    print("\n测试加载batch...")
    batch = next(iter(train_loader))
    
    print(f"\nBatch信息:")
    print(f"  批次大小: {batch['radar'].shape[0]}")
    print(f"  雷达形状: {batch['radar'].shape}")
    print(f"  音频形状: {batch['audio'].shape}")
    print(f"  标签: {batch['label']}")
    
    print(f"\n样本详情:")
    for i in range(min(2, batch['radar'].shape[0])):
        print(f"\n  样本 {i+1}:")
        print(f"    大场景: {batch['location_name'][i]}")
        print(f"    子场景: {batch['sub_location_name'][i]}")
        print(f"    动作: {batch['action_code'][i]}")
        print(f"    描述: {batch['action_intro'][i]}")
        print(f"    被试ID: {batch['subject_id'][i].item()}")
        print(f"    样本ID: {batch['sample_id'][i].item()}")
    
    print("\n=" * 60)
    print("✓ 测试完成！")
    print("=" * 60)
