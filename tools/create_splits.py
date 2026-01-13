"""
create_splits.py - 数据集划分工具

用途: 将采集的多模态数据划分为训练集/验证集/测试集
支持: 按被试分割（推荐）或按样本随机分割
"""

import os
import json
import random
from pathlib import Path
import argparse

def create_dataset_splits(data_root, output_dir=None, strategy='subject', 
                         ratios=(0.7, 0.15, 0.15), seed=42):
    """
    创建数据集分割
    
    Args:
        data_root: 数据集根目录（包含subjects/文件夹）
        output_dir: 输出目录（默认为data_root）
        strategy: 'subject'（按被试分割）或'sample'（按样本随机分割）
        ratios: (train, val, test)比例，必须和为1.0
        seed: 随机种子
    """
    data_root = Path(data_root)
    output_dir = Path(output_dir) if output_dir else data_root
    subjects_dir = data_root / 'subjects'
    
    if not subjects_dir.exists():
        raise ValueError(f"找不到subjects目录: {subjects_dir}")
    
    # 验证比例
    if abs(sum(ratios) - 1.0) > 1e-6:
        raise ValueError(f"比例必须和为1.0，当前为 {sum(ratios)}")
    
    train_ratio, val_ratio, test_ratio = ratios
    random.seed(seed)
    
    print("=" * 60)
    print("数据集划分工具")
    print("=" * 60)
    print(f"数据根目录: {data_root}")
    print(f"划分策略: {strategy}")
    print(f"比例: Train {train_ratio:.1%}, Val {val_ratio:.1%}, Test {test_ratio:.1%}")
    print(f"随机种子: {seed}")
    print()
    
    # 收集所有有效样本
    samples = []
    subject_sample_count = {}
    
    for subject_dir in sorted(subjects_dir.glob("subject_*")):
        radar_dir = subject_dir / "radar"
        audio_dir = subject_dir / "audio"
        
        if not radar_dir.exists():
            continue
        
        subject_name = subject_dir.name
        subject_samples = []
        
        for radar_file in sorted(radar_dir.glob("sample_*.bin")):
            # 从文件名提取信息
            # 格式: sample_XXX_LXX_SLXX_ActionCode_Raw_0.bin
            stem = radar_file.stem.replace("_Raw_0", "")
            
            # 检查对应的音频文件是否存在
            audio_file = audio_dir / f"{stem}.wav"
            if audio_file.exists():
                sample_info = {
                    'subject': subject_name,
                    'sample_stem': stem,
                    'radar_file': str(radar_file),
                    'audio_file': str(audio_file)
                }
                samples.append(sample_info)
                subject_samples.append(sample_info)
        
        subject_sample_count[subject_name] = len(subject_samples)
    
    print(f"找到 {len(samples)} 个有效样本")
    print(f"被试数: {len(subject_sample_count)}")
    print()
    
    if len(samples) == 0:
        raise ValueError("未找到有效样本！请检查数据目录结构")
    
    # 分割数据
    if strategy == 'subject':
        # 按被试分割（推荐）
        subjects = list(subject_sample_count.keys())
        random.shuffle(subjects)
        
        n_train = int(len(subjects) * train_ratio)
        n_val = int(len(subjects) * val_ratio)
        
        train_subjects = set(subjects[:n_train])
        val_subjects = set(subjects[n_train:n_train+n_val])
        test_subjects = set(subjects[n_train+n_val:])
        
        train_samples = [s for s in samples if s['subject'] in train_subjects]
        val_samples = [s for s in samples if s['subject'] in val_subjects]
        test_samples = [s for s in samples if s['subject'] in test_subjects]
        
        print(f"按被试分割:")
        print(f"  训练集: {len(train_subjects)} 被试, {len(train_samples)} 样本")
        print(f"  验证集: {len(val_subjects)} 被试, {len(val_samples)} 样本")
        print(f"  测试集: {len(test_subjects)} 被试, {len(test_samples)} 样本")
        
    elif strategy == 'sample':
        # 按样本随机分割
        random.shuffle(samples)
        
        n_train = int(len(samples) * train_ratio)
        n_val = int(len(samples) * val_ratio)
        
        train_samples = samples[:n_train]
        val_samples = samples[n_train:n_train+n_val]
        test_samples = samples[n_train+n_val:]
        
        print(f"按样本分割:")
        print(f"  训练集: {len(train_samples)} 样本")
        print(f"  验证集: {len(val_samples)} 样本")
        print(f"  测试集: {len(test_samples)} 样本")
    else:
        raise ValueError(f"未知的分割策略: {strategy}")
    
    # 保存分割文件
    def save_split(samples_list, filename):
        filepath = output_dir / filename
        with open(filepath, 'w', encoding='utf-8') as f:
            for s in samples_list:
                # 格式: subject_XXX/sample_YYY_...
                f.write(f"{s['subject']}/{s['sample_stem']}\n")
        return filepath
    
    train_file = save_split(train_samples, 'train.txt')
    val_file = save_split(val_samples, 'val.txt')
    test_file = save_split(test_samples, 'test.txt')
    
    print()
    print("=" * 60)
    print("分割文件已生成:")
    print(f"  {train_file}")
    print(f"  {val_file}")
    print(f"  {test_file}")
    print("=" * 60)
    
    # 生成统计报告
    report = {
        'strategy': strategy,
        'ratios': {'train': train_ratio, 'val': val_ratio, 'test': test_ratio},
        'seed': seed,
        'total_subjects': len(subject_sample_count),
        'total_samples': len(samples),
        'splits': {
            'train': {
                'samples': len(train_samples),
                'subjects': len(train_subjects) if strategy == 'subject' else 'N/A'
            },
            'val': {
                'samples': len(val_samples),
                'subjects': len(val_subjects) if strategy == 'subject' else 'N/A'
            },
            'test': {
                'samples': len(test_samples),
                'subjects': len(test_subjects) if strategy == 'subject' else 'N/A'
            }
        }
    }
    
    report_file = output_dir / 'split_report.json'
    with open(report_file, 'w', encoding='utf-8') as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    
    print(f"\n统计报告: {report_file}")
    print("\n✓ 数据集划分完成！")
    
    return train_file, val_file, test_file


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='多模态数据集划分工具')
    parser.add_argument('--root', type=str, required=True,
                       help='数据集根目录（包含subjects/文件夹）')
    parser.add_argument('--output', type=str, default=None,
                       help='输出目录（默认为root）')
    parser.add_argument('--strategy', type=str, default='subject',
                       choices=['subject', 'sample'],
                       help='分割策略: subject（按被试）或 sample（按样本）')
    parser.add_argument('--ratios', type=float, nargs=3, default=[0.7, 0.15, 0.15],
                       help='Train/Val/Test比例（必须和为1.0）')
    parser.add_argument('--seed', type=int, default=42,
                       help='随机种子')
    
    args = parser.parse_args()
    
    create_dataset_splits(
        data_root=args.root,
        output_dir=args.output,
        strategy=args.strategy,
        ratios=tuple(args.ratios),
        seed=args.seed
    )
