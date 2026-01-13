"""
数据集分割工具 - 生成train/val/test分割文件

支持两种分割策略:
1. 随机分割: 按比例随机划分样本
2. 被试分割: 按被试划分，确保同一被试的样本不会跨分割集

使用示例:
    python split_dataset.py --root E:/data/subjects --strategy subject --ratios 0.7 0.15 0.15
"""

import os
import json
import argparse
import random
from typing import List, Tuple, Dict
from collections import defaultdict


def load_all_samples(root_dir: str) -> List[Dict]:
    """
    加载所有样本信息
    
    返回:
        samples: 列表，每个元素是样本信息字典
    """
    samples = []
    
    for subject_name in sorted(os.listdir(root_dir)):
        subject_dir = os.path.join(root_dir, subject_name)
        if not os.path.isdir(subject_dir):
            continue
        
        meta_path = os.path.join(subject_dir, 'samples_metadata.json')
        if not os.path.exists(meta_path):
            print(f"警告: 找不到元数据文件 {meta_path}")
            continue
        
        with open(meta_path, 'r', encoding='utf-8') as f:
            meta = json.load(f)
            
        for sample in meta['samples']:
            sample_info = {
                'subject_id': meta['subject_id'],
                'subject_name': subject_name,
                'sample_id': sample['sample_id'],
                'scene_code': sample['scene']['code'],
                'scene_intro': sample['scene'].get('intro', ''),  # 使用CSV中的场景描述
                'scene_idx': sample['scene'].get('idx', -1),
                'radar_file': sample['radar_file'],
                'audio_file': sample['audio_file'],
            }
            samples.append(sample_info)
    
    return samples


def split_by_sample(
    samples: List[Dict],
    train_ratio: float,
    val_ratio: float,
    test_ratio: float,
    seed: int = 42
) -> Tuple[List[Dict], List[Dict], List[Dict]]:
    """
    按样本随机分割数据集
    
    参数:
        samples: 所有样本列表
        train_ratio: 训练集比例
        val_ratio: 验证集比例
        test_ratio: 测试集比例
        seed: 随机种子
    
    返回:
        (train_samples, val_samples, test_samples)
    """
    random.seed(seed)
    samples_copy = samples.copy()
    random.shuffle(samples_copy)
    
    total = len(samples_copy)
    train_end = int(total * train_ratio)
    val_end = train_end + int(total * val_ratio)
    
    train_samples = samples_copy[:train_end]
    val_samples = samples_copy[train_end:val_end]
    test_samples = samples_copy[val_end:]
    
    return train_samples, val_samples, test_samples


def split_by_subject(
    samples: List[Dict],
    train_ratio: float,
    val_ratio: float,
    test_ratio: float,
    seed: int = 42
) -> Tuple[List[Dict], List[Dict], List[Dict]]:
    """
    按被试分割数据集（确保同一被试的样本在同一分割集中）
    
    参数:
        samples: 所有样本列表
        train_ratio: 训练集比例
        val_ratio: 验证集比例
        test_ratio: 测试集比例
        seed: 随机种子
    
    返回:
        (train_samples, val_samples, test_samples)
    """
    random.seed(seed)
    
    # 按被试组织样本
    subject_to_samples = defaultdict(list)
    for sample in samples:
        subject_to_samples[sample['subject_id']].append(sample)
    
    # 打乱被试顺序
    subject_ids = list(subject_to_samples.keys())
    random.shuffle(subject_ids)
    
    # 按比例分配被试
    total_subjects = len(subject_ids)
    train_end = int(total_subjects * train_ratio)
    val_end = train_end + int(total_subjects * val_ratio)
    
    train_subjects = subject_ids[:train_end]
    val_subjects = subject_ids[train_end:val_end]
    test_subjects = subject_ids[val_end:]
    
    # 收集样本
    train_samples = []
    val_samples = []
    test_samples = []
    
    for sid in train_subjects:
        train_samples.extend(subject_to_samples[sid])
    for sid in val_subjects:
        val_samples.extend(subject_to_samples[sid])
    for sid in test_subjects:
        test_samples.extend(subject_to_samples[sid])
    
    return train_samples, val_samples, test_samples


def save_split_file(samples: List[Dict], output_path: str):
    """
    保存分割文件
    
    格式: 每行一个样本路径
        subject_001/sample_005_front_static_left_static_idle
    """
    with open(output_path, 'w', encoding='utf-8') as f:
        for sample in samples:
            # 构建路径
            radar_basename = os.path.basename(sample['radar_file'])
            sample_name = radar_basename.replace('.bin', '')
            line = f"{sample['subject_name']}/{sample_name}\n"
            f.write(line)
    
    print(f"保存分割文件: {output_path} ({len(samples)} 个样本)")


def print_statistics(
    train_samples: List[Dict],
    val_samples: List[Dict],
    test_samples: List[Dict]
):
    """打印分割统计信息"""
    total = len(train_samples) + len(val_samples) + len(test_samples)
    
    print("\n========== 数据集分割统计 ==========")
    print(f"总样本数: {total}")
    print(f"训练集: {len(train_samples)} ({len(train_samples)/total*100:.1f}%)")
    print(f"验证集: {len(val_samples)} ({len(val_samples)/total*100:.1f}%)")
    print(f"测试集: {len(test_samples)} ({len(test_samples)/total*100:.1f}%)")
    
    # 被试统计
    train_subjects = set(s['subject_id'] for s in train_samples)
    val_subjects = set(s['subject_id'] for s in val_samples)
    test_subjects = set(s['subject_id'] for s in test_samples)
    
    print(f"\n被试分布:")
    print(f"训练集被试: {len(train_subjects)}")
    print(f"验证集被试: {len(val_subjects)}")
    print(f"测试集被试: {len(test_subjects)}")
    
    # 场景统计
    def count_scenes(samples):
        scenes = defaultdict(int)
        for s in samples:
            scenes[s['scene_code']] += 1
        return scenes
    
    train_scenes = count_scenes(train_samples)
    val_scenes = count_scenes(val_samples)
    test_scenes = count_scenes(test_samples)
    
    print(f"\n场景分布:")
    print(f"训练集场景数: {len(train_scenes)}")
    print(f"验证集场景数: {len(val_scenes)}")
    print(f"测试集场景数: {len(test_scenes)}")


def main():
    parser = argparse.ArgumentParser(description='数据集分割工具')
    parser.add_argument('--root', type=str, required=True,
                        help='数据集根目录（包含subjects/文件夹）')
    parser.add_argument('--output', type=str, default=None,
                        help='输出目录（默认为root）')
    parser.add_argument('--strategy', type=str, default='subject',
                        choices=['sample', 'subject'],
                        help='分割策略: sample(按样本随机) 或 subject(按被试)')
    parser.add_argument('--ratios', type=float, nargs=3, default=[0.7, 0.15, 0.15],
                        help='train/val/test分割比例，例如: 0.7 0.15 0.15')
    parser.add_argument('--seed', type=int, default=42,
                        help='随机种子')
    
    args = parser.parse_args()
    
    # 检查比例和
    train_ratio, val_ratio, test_ratio = args.ratios
    if abs(train_ratio + val_ratio + test_ratio - 1.0) > 1e-6:
        print(f"错误: 分割比例之和必须为1.0 (当前: {sum(args.ratios)})")
        return
    
    # 输出目录
    output_dir = args.output if args.output else os.path.dirname(args.root.rstrip('/\\'))
    os.makedirs(output_dir, exist_ok=True)
    
    # 加载样本
    print(f"加载数据集: {args.root}")
    samples = load_all_samples(args.root)
    print(f"找到 {len(samples)} 个样本")
    
    # 分割数据集
    print(f"\n使用策略: {args.strategy}")
    if args.strategy == 'sample':
        train_samples, val_samples, test_samples = split_by_sample(
            samples, train_ratio, val_ratio, test_ratio, args.seed
        )
    else:
        train_samples, val_samples, test_samples = split_by_subject(
            samples, train_ratio, val_ratio, test_ratio, args.seed
        )
    
    # 保存分割文件
    save_split_file(train_samples, os.path.join(output_dir, 'train.txt'))
    save_split_file(val_samples, os.path.join(output_dir, 'val.txt'))
    save_split_file(test_samples, os.path.join(output_dir, 'test.txt'))
    
    # 打印统计信息
    print_statistics(train_samples, val_samples, test_samples)
    
    print(f"\n分割文件已保存到: {output_dir}")


if __name__ == '__main__':
    main()
