"""
数据集验证工具 - 检查数据完整性和一致性

功能:
1. 检查文件是否存在
2. 验证文件格式和大小
3. 检查元数据一致性
4. 生成验证报告

使用示例:
    python verify_dataset.py --root E:/data/subjects --output verification_report.txt
"""

import os
import json
import argparse
import hashlib
from typing import Dict, List, Tuple
from collections import defaultdict
import numpy as np
import soundfile as sf


def check_file_exists(file_path: str) -> Tuple[bool, str]:
    """检查文件是否存在"""
    if os.path.exists(file_path):
        return True, "OK"
    else:
        return False, f"文件不存在: {file_path}"


def verify_radar_file(file_path: str, expected_config: Dict) -> Tuple[bool, str]:
    """
    验证雷达文件格式
    
    参数:
        file_path: 雷达文件路径
        expected_config: 期望的配置 {'num_rx', 'num_chirps', 'num_samples'}
    
    返回:
        (is_valid, message)
    """
    try:
        # 读取文件
        data = np.fromfile(file_path, dtype=np.int16)
        
        # 计算期望长度
        expected_len = (expected_config['num_samples'] * 
                       expected_config['num_chirps'] * 
                       expected_config['num_rx'])
        
        # 检查长度
        if len(data) == expected_len:
            return True, "雷达文件格式正确"
        else:
            return False, f"雷达数据长度不匹配: 期望 {expected_len}, 实际 {len(data)}"
    
    except Exception as e:
        return False, f"读取雷达文件失败: {str(e)}"


def verify_audio_file(file_path: str, min_duration: float = 0.5) -> Tuple[bool, str]:
    """
    验证音频文件格式
    
    参数:
        file_path: 音频文件路径
        min_duration: 最小时长（秒）
    
    返回:
        (is_valid, message)
    """
    try:
        # 读取音频信息
        info = sf.info(file_path)
        duration = info.duration
        
        # 检查时长
        if duration < min_duration:
            return False, f"音频时长过短: {duration:.2f}s < {min_duration}s"
        
        return True, f"音频文件格式正确 (时长: {duration:.2f}s, 采样率: {info.samplerate}Hz)"
    
    except Exception as e:
        return False, f"读取音频文件失败: {str(e)}"


def compute_file_md5(file_path: str) -> str:
    """计算文件MD5哈希"""
    md5 = hashlib.md5()
    with open(file_path, 'rb') as f:
        for chunk in iter(lambda: f.read(4096), b""):
            md5.update(chunk)
    return md5.hexdigest()


def verify_dataset(
    root_dir: str,
    radar_config: Dict,
    check_md5: bool = False,
    verbose: bool = False
) -> Dict:
    """
    验证整个数据集
    
    返回:
        验证报告字典
    """
    report = {
        'total_subjects': 0,
        'total_samples': 0,
        'valid_samples': 0,
        'invalid_samples': 0,
        'errors': [],
        'warnings': [],
        'scene_distribution': defaultdict(int),
        'subject_sample_counts': {},
    }
    
    print(f"开始验证数据集: {root_dir}\n")
    
    # 遍历所有被试文件夹
    for subject_name in sorted(os.listdir(root_dir)):
        subject_dir = os.path.join(root_dir, subject_name)
        if not os.path.isdir(subject_dir):
            continue
        
        report['total_subjects'] += 1
        
        # 检查元数据文件
        meta_path = os.path.join(subject_dir, 'samples_metadata.json')
        if not os.path.exists(meta_path):
            error_msg = f"[{subject_name}] 找不到元数据文件"
            report['errors'].append(error_msg)
            print(f"✗ {error_msg}")
            continue
        
        # 读取元数据
        try:
            with open(meta_path, 'r', encoding='utf-8') as f:
                meta = json.load(f)
        except Exception as e:
            error_msg = f"[{subject_name}] 无法解析元数据文件: {str(e)}"
            report['errors'].append(error_msg)
            print(f"✗ {error_msg}")
            continue
        
        subject_id = meta.get('subject_id', 'unknown')
        samples = meta.get('samples', [])
        num_samples = len(samples)
        
        report['total_samples'] += num_samples
        report['subject_sample_counts'][subject_name] = num_samples
        
        if verbose:
            print(f"验证被试 {subject_name} ({num_samples} 个样本)...")
        
        # 验证每个样本
        for sample in samples:
            sample_id = sample.get('sample_id', 'unknown')
            scene_code = sample.get('scene', {}).get('code', 'unknown')
            
            # 统计场景分布
            report['scene_distribution'][scene_code] += 1
            
            # 构建文件路径
            radar_file = os.path.join(subject_dir, sample.get('radar_file', ''))
            audio_file = os.path.join(subject_dir, sample.get('audio_file', ''))
            
            is_valid = True
            
            # 检查雷达文件
            exists, msg = check_file_exists(radar_file)
            if not exists:
                report['errors'].append(f"[{subject_name}/sample_{sample_id}] {msg}")
                is_valid = False
            else:
                valid, msg = verify_radar_file(radar_file, radar_config)
                if not valid:
                    report['errors'].append(f"[{subject_name}/sample_{sample_id}] {msg}")
                    is_valid = False
            
            # 检查音频文件
            exists, msg = check_file_exists(audio_file)
            if not exists:
                report['errors'].append(f"[{subject_name}/sample_{sample_id}] {msg}")
                is_valid = False
            else:
                valid, msg = verify_audio_file(audio_file)
                if not valid:
                    report['errors'].append(f"[{subject_name}/sample_{sample_id}] {msg}")
                    is_valid = False
            
            # 计算MD5（可选）
            if check_md5 and is_valid:
                radar_md5 = compute_file_md5(radar_file)
                audio_md5 = compute_file_md5(audio_file)
                # 可以将MD5保存到报告中用于后续校验
            
            if is_valid:
                report['valid_samples'] += 1
            else:
                report['invalid_samples'] += 1
        
        if verbose:
            print(f"  ✓ {subject_name} 验证完成\n")
    
    return report


def print_report(report: Dict, output_file: str = None):
    """打印验证报告"""
    lines = []
    lines.append("=" * 60)
    lines.append("数据集验证报告")
    lines.append("=" * 60)
    lines.append(f"\n总体统计:")
    lines.append(f"  被试数: {report['total_subjects']}")
    lines.append(f"  样本数: {report['total_samples']}")
    lines.append(f"  有效样本: {report['valid_samples']}")
    lines.append(f"  无效样本: {report['invalid_samples']}")
    
    if report['total_samples'] > 0:
        valid_rate = report['valid_samples'] / report['total_samples'] * 100
        lines.append(f"  有效率: {valid_rate:.2f}%")
    
    # 场景分布
    lines.append(f"\n场景分布 (共 {len(report['scene_distribution'])} 种场景):")
    for scene, count in sorted(report['scene_distribution'].items()):
        lines.append(f"  {scene}: {count} 个样本")
    
    # 被试样本数
    lines.append(f"\n被试样本数:")
    for subject, count in sorted(report['subject_sample_counts'].items()):
        lines.append(f"  {subject}: {count} 个样本")
    
    # 错误信息
    if report['errors']:
        lines.append(f"\n错误列表 (共 {len(report['errors'])} 个):")
        for error in report['errors'][:50]:  # 最多显示50个错误
            lines.append(f"  ✗ {error}")
        if len(report['errors']) > 50:
            lines.append(f"  ... 还有 {len(report['errors']) - 50} 个错误")
    else:
        lines.append("\n✓ 未发现错误！")
    
    # 警告信息
    if report['warnings']:
        lines.append(f"\n警告列表 (共 {len(report['warnings'])} 个):")
        for warning in report['warnings'][:50]:
            lines.append(f"  ⚠ {warning}")
    
    lines.append("\n" + "=" * 60)
    
    # 输出
    report_text = "\n".join(lines)
    print(report_text)
    
    # 保存到文件
    if output_file:
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(report_text)
        print(f"\n报告已保存到: {output_file}")


def main():
    parser = argparse.ArgumentParser(description='数据集验证工具')
    parser.add_argument('--root', type=str, required=True,
                        help='数据集根目录（包含subjects/文件夹）')
    parser.add_argument('--output', type=str, default='verification_report.txt',
                        help='验证报告输出文件')
    parser.add_argument('--check-md5', action='store_true',
                        help='计算文件MD5校验和（耗时较长）')
    parser.add_argument('--verbose', action='store_true',
                        help='显示详细输出')
    
    args = parser.parse_args()
    
    # 雷达配置（根据实际硬件配置修改）
    radar_config = {
        'num_rx': 4,
        'num_chirps': 128,
        'num_samples': 256,
    }
    
    # 验证数据集
    report = verify_dataset(
        args.root,
        radar_config,
        check_md5=args.check_md5,
        verbose=args.verbose
    )
    
    # 打印报告
    print_report(report, args.output)


if __name__ == '__main__':
    main()
