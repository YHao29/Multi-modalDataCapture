"""
PyTorch DataLoader for Multimodal Human Activity Dataset

支持加载毫米波雷达+超声波音频的多模态数据
数据格式: 雷达 .bin (int16) + 音频 .wav

使用示例:
    from multimodal_dataloader import MultimodalDataset
    
    dataset = MultimodalDataset(
        root_dir='E:/data/subjects',
        split='train',
        split_file='E:/data/train.txt'
    )
    
    dataloader = torch.utils.data.DataLoader(
        dataset,
        batch_size=32,
        shuffle=True,
        num_workers=4
    )
"""

import os
import json
import numpy as np
import torch
from torch.utils.data import Dataset
import soundfile as sf
from typing import Dict, List, Tuple, Optional
import warnings


class MultimodalDataset(Dataset):
    """多模态（雷达+音频）人体活动检测数据集"""
    
    def __init__(
        self,
        root_dir: str,
        split: str = 'train',
        split_file: Optional[str] = None,
        radar_config: Optional[Dict] = None,
        audio_config: Optional[Dict] = None,
        transform=None
    ):
        """
        参数:
            root_dir: 数据集根目录（包含subjects/文件夹）
            split: 数据集分割类型 ('train', 'val', 'test')
            split_file: 分割文件路径（每行一个样本路径）
            radar_config: 雷达配置字典（用于解析.bin文件）
            audio_config: 音频配置字典
            transform: 数据增强/变换函数
        """
        self.root_dir = root_dir
        self.split = split
        self.transform = transform
        
        # 默认雷达配置（与MATLAB readDCA1000对应）
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
        
        # 加载场景信息（用于标签映射）
        self.scene_to_idx = self._build_scene_mapping()
        
        print(f"加载 {split} 数据集: {len(self.samples)} 个样本")
        
    def _load_samples(self, split_file: Optional[str]) -> List[Dict]:
        """从分割文件加载样本路径"""
        samples = []
        
        if split_file and os.path.exists(split_file):
            # 从split文件读取
            with open(split_file, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    
                    # 格式: subject_001/sample_005_front_static_left_static_idle
                    parts = line.split('/')
                    subject_name = parts[0]
                    sample_name = parts[1]
                    
                    # 构建完整路径
                    subject_dir = os.path.join(self.root_dir, subject_name)
                    meta_path = os.path.join(subject_dir, 'samples_metadata.json')
                    
                    # 读取元数据找到对应样本
                    if os.path.exists(meta_path):
                        with open(meta_path, 'r', encoding='utf-8') as mf:
                            meta = json.load(mf)
                            for sample in meta['samples']:
                                radar_file = os.path.basename(sample['radar_file'])
                                if radar_file.replace('.bin', '') == sample_name:
                                    sample_info = {
                                        'subject_id': meta['subject_id'],
                                        'sample_id': sample['sample_id'],
                                        'scene_code': sample['scene']['code'],
                                        'scene_intro': sample['scene'].get('intro', ''),  # 使用CSV中的中文描述
                                        'scene_idx': sample['scene'].get('idx', -1),
                                        'radar_path': os.path.join(subject_dir, sample['radar_file']),
                                        'audio_path': os.path.join(subject_dir, sample['audio_file']),
                                        'sync_quality': sample.get('sync_quality', {}),
                                    }
                                    samples.append(sample_info)
                                    break
        else:
            # 如果没有split文件，扫描所有subjects
            warnings.warn(f"未找到分割文件 {split_file}，将加载所有数据")
            subjects_dir = self.root_dir
            for subject_name in os.listdir(subjects_dir):
                subject_dir = os.path.join(subjects_dir, subject_name)
                if not os.path.isdir(subject_dir):
                    continue
                
                meta_path = os.path.join(subject_dir, 'samples_metadata.json')
                if not os.path.exists(meta_path):
                    continue
                
                with open(meta_path, 'r', encoding='utf-8') as f:
                    meta = json.load(f)
                    for sample in meta['samples']:
                        sample_info = {
                            'subject_id': meta['subject_id'],
                            'sample_id': sample['sample_id'],
                            'scene_code': sample['scene']['code'],
                            'scene_intro': sample['scene'].get('intro', ''),  # 使用CSV中的中文描述
                            'scene_idx': sample['scene'].get('idx', -1),
                            'radar_path': os.path.join(subject_dir, sample['radar_file']),
                            'audio_path': os.path.join(subject_dir, sample['audio_file']),
                            'sync_quality': sample.get('sync_quality', {}),
                        }
                        samples.append(sample_info)
        
        return samples
    
    def _build_scene_mapping(self) -> Dict[str, int]:
        """构建场景代码到索引的映射（用于分类标签）"""
        unique_scenes = sorted(set(s['scene_code'] for s in self.samples))
        return {scene: idx for idx, scene in enumerate(unique_scenes)}
    
    def _load_radar_data(self, file_path: str) -> np.ndarray:
        """
        加载雷达二进制数据
        
        返回:
            radar_data: shape (num_samples, num_chirps, num_rx)
        """
        try:
            # 读取二进制文件
            data = np.fromfile(file_path, dtype=self.radar_config['dtype'])
            
            # 重塑为 [samples, chirps, RX]
            num_samples = self.radar_config['num_samples']
            num_chirps = self.radar_config['num_chirps']
            num_rx = self.radar_config['num_rx']
            
            expected_len = num_samples * num_chirps * num_rx
            if len(data) != expected_len:
                warnings.warn(
                    f"数据长度不匹配: 期望 {expected_len}, 实际 {len(data)}"
                    f"\n文件: {file_path}"
                )
                # 截断或填充
                if len(data) < expected_len:
                    data = np.pad(data, (0, expected_len - len(data)))
                else:
                    data = data[:expected_len]
            
            radar_data = data.reshape(num_samples, num_chirps, num_rx)
            return radar_data
            
        except Exception as e:
            raise IOError(f"无法加载雷达数据: {file_path}\n错误: {str(e)}")
    
    def _load_audio_data(self, file_path: str) -> Tuple[np.ndarray, int]:
        """
        加载音频数据
        
        返回:
            audio_data: shape (num_samples,) 单声道或 (num_samples, channels) 多声道
            sample_rate: 采样率
        """
        try:
            audio_data, sample_rate = sf.read(file_path)
            return audio_data, sample_rate
        except Exception as e:
            raise IOError(f"无法加载音频数据: {file_path}\n错误: {str(e)}")
    
    def __len__(self) -> int:
        return len(self.samples)
    
    def __getitem__(self, idx: int) -> Dict[str, torch.Tensor]:
        """
        返回一个样本
        
        返回字典:
            'radar': Tensor, shape (num_samples, num_chirps, num_rx)
            'audio': Tensor, shape (audio_samples,) 或 (audio_samples, channels)
            'label': int, 场景分类标签
            'scene_code': str, 场景代码 (如 'A1-B1-C1-D1-E1')
            'scene_intro': str, 场景描述 (直接从CSV读取)
            'subject_id': int, 被试ID
            'sample_id': int, 样本ID
        """
        sample_info = self.samples[idx]
        
        # 加载雷达数据
        radar_data = self._load_radar_data(sample_info['radar_path'])
        radar_tensor = torch.from_numpy(radar_data).float()
        
        # 加载音频数据
        audio_data, _ = self._load_audio_data(sample_info['audio_path'])
        audio_tensor = torch.from_numpy(audio_data).float()
        
        # 标签
        label = self.scene_to_idx[sample_info['scene_code']]
        
        # 构建返回字典
        item = {
            'radar': radar_tensor,
            'audio': audio_tensor,
            'label': label,
            'scene_code': sample_info['scene_code'],
            'scene_intro': sample_info.get('scene_intro', ''),  # 使用CSV中的场景描述
            'scene_idx': sample_info.get('scene_idx', -1),
            'subject_id': sample_info['subject_id'],
            'sample_id': sample_info['sample_id'],
        }
        
        # 应用变换
        if self.transform:
            item = self.transform(item)
        
        return item
    
    def get_scene_codes(self) -> List[str]:
        """返回所有场景代码列表（按标签索引排序）"""
        idx_to_scene = {v: k for k, v in self.scene_to_idx.items()}
        return [idx_to_scene[i] for i in range(len(idx_to_scene))]


# 使用示例
if __name__ == '__main__':
    # 创建数据集
    dataset = MultimodalDataset(
        root_dir='E:/data/subjects',
        split='train',
        split_file='E:/data/train.txt'
    )
    
    # 查看第一个样本
    sample = dataset[0]
    print(f"雷达数据形状: {sample['radar'].shape}")
    print(f"音频数据形状: {sample['audio'].shape}")
    print(f"标签: {sample['label']}")
    print(f"场景代码: {sample['scene_code']}")
    print(f"场景描述: {sample['scene_intro']}")
    print(f"被试ID: {sample['subject_id']}, 样本ID: {sample['sample_id']}")
    
    # 创建DataLoader
    from torch.utils.data import DataLoader
    dataloader = DataLoader(dataset, batch_size=8, shuffle=True)
    
    for batch in dataloader:
        print(f"\nBatch雷达形状: {batch['radar'].shape}")
        print(f"Batch音频形状: {batch['audio'].shape}")
        print(f"Batch标签: {batch['label']}")
        break
