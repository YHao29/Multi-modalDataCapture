"""
数据验证与可视化工具 - 用于测试采集阶段验证毫米波雷达和音频数据质量

功能：
1. 加载和解析毫米波雷达数据（.bin文件）
2. 加载和解析音频数据（.wav文件）
3. 可视化展示：
   - 雷达：Range-Doppler图、Range-Time图、原始ADC波形
   - 音频：时域波形、频谱图、超声波频段分析
4. 数据质量验证：
   - 检查文件完整性
   - 检查信号能量
   - 检查同步质量

使用示例:
    # 命令行模式
    python visualize_data.py --radar path/to/radar.bin --audio path/to/audio.wav
    
    # 交互式模式（指定数据目录）
    python visualize_data.py --dir E:/data/subjects/subject_001 --sample 1
    
    # 批量验证模式
    python visualize_data.py --dir E:/data/subjects --batch --report validation_report.json

作者：Data Capture System
日期：2026-01-13
"""

import os
import sys
import json
import argparse
import numpy as np
import matplotlib.pyplot as plt
from matplotlib.gridspec import GridSpec
import soundfile as sf
from scipy import signal
from typing import Dict, Tuple, Optional, List
from datetime import datetime
import warnings

# 设置中文字体支持
plt.rcParams["font.sans-serif"] = ["SimHei", "Microsoft YaHei", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False


# ==================== 配置参数 ====================
DEFAULT_RADAR_CONFIG = {
    "num_rx": 4,           # 接收天线数量
    "num_chirps": 128,     # chirp数量（每帧）
    "num_samples": 256,    # ADC采样点数（每chirp）
    "dtype": np.int16,     # 数据类型
    "sample_rate_hz": 4e6, # ADC采样率
    "bandwidth_mhz": 152.1,# 带宽
    "center_freq_ghz": 77, # 中心频率
}

DEFAULT_AUDIO_CONFIG = {
    "sample_rate": 44100,       # 采样率
    "ultrasonic_freq": 20000,   # 超声波中心频率
    "ultrasonic_bw": 2000,      # 超声波带宽（19k-21k）
}


# ==================== 数据加载函数 ====================
def load_radar_data(file_path: str, config: Dict = None) -> Tuple[np.ndarray, Dict]:
    """
    加载雷达二进制数据
    
    参数:
        file_path: .bin文件路径
        config: 雷达配置字典
        
    返回:
        radar_data: 复数数组，shape (num_rx, num_chirps, num_samples)
        info: 数据信息字典
    """
    config = config or DEFAULT_RADAR_CONFIG
    
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"雷达文件不存在: {file_path}")
    
    # 读取原始数据
    raw_data = np.fromfile(file_path, dtype=config["dtype"])
    file_size = len(raw_data)
    
    num_samples = config["num_samples"]
    num_rx = config["num_rx"]
    
    # 计算chirp数量（复数数据：I和Q交织）
    # 格式: 2I followed by 2Q
    expected_samples_per_chirp = num_samples * num_rx * 2  # *2 for I/Q
    num_chirps = file_size // expected_samples_per_chirp
    
    info = {
        "file_size_bytes": file_size * 2,  # int16 = 2 bytes
        "file_size_mb": file_size * 2 / 1024 / 1024,
        "num_chirps": num_chirps,
        "num_samples": num_samples,
        "num_rx": num_rx,
        "duration_estimate_s": num_chirps / 128 * 0.05,  # 估算时长
    }
    
    # 解析IQ数据 (参考MATLAB readDCA1000.m)
    try:
        # 重组为复数数据
        expected_len = num_chirps * num_samples * num_rx * 2
        if file_size < expected_len:
            warnings.warn(f"文件数据不完整: 期望 {expected_len}, 实际 {file_size}")
            # 填充零
            raw_data = np.pad(raw_data, (0, expected_len - file_size))
        elif file_size > expected_len:
            raw_data = raw_data[:expected_len]
        
        # 解析IQ：2I followed by 2Q
        complex_data = np.zeros(file_size // 2, dtype=np.complex64)
        for i in range(0, min(len(raw_data) - 3, file_size - 3), 4):
            complex_data[i // 2] = raw_data[i] + 1j * raw_data[i + 2]
            complex_data[i // 2 + 1] = raw_data[i + 1] + 1j * raw_data[i + 3]
        
        # 重塑为 (num_chirps, num_samples * num_rx)
        complex_data = complex_data[:num_chirps * num_samples * num_rx]
        complex_data = complex_data.reshape(num_chirps, num_samples * num_rx)
        
        # 重组为 (num_rx, num_chirps, num_samples)
        radar_data = np.zeros((num_rx, num_chirps, num_samples), dtype=np.complex64)
        for rx in range(num_rx):
            radar_data[rx] = complex_data[:, rx * num_samples:(rx + 1) * num_samples]
        
        info["parse_success"] = True
        
    except Exception as e:
        warnings.warn(f"数据解析失败: {str(e)}")
        # 简化处理：直接reshape
        radar_data = raw_data.reshape(-1, num_samples).astype(np.float32)
        radar_data = radar_data[:num_chirps * num_rx].reshape(num_rx, num_chirps, num_samples)
        info["parse_success"] = False
        info["parse_error"] = str(e)
    
    return radar_data, info


def load_audio_data(file_path: str, config: Dict = None) -> Tuple[np.ndarray, int, Dict]:
    """
    加载音频数据
    
    参数:
        file_path: .wav文件路径
        config: 音频配置字典
        
    返回:
        audio_data: 音频数组
        sample_rate: 采样率
        info: 数据信息字典
    """
    config = config or DEFAULT_AUDIO_CONFIG
    
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"音频文件不存在: {file_path}")
    
    audio_data, sample_rate = sf.read(file_path)
    
    # 获取文件信息
    file_info = sf.info(file_path)
    
    info = {
        "sample_rate": sample_rate,
        "duration_s": file_info.duration,
        "channels": file_info.channels,
        "format": file_info.format,
        "subtype": file_info.subtype,
        "num_samples": len(audio_data),
        "file_size_mb": os.path.getsize(file_path) / 1024 / 1024,
    }
    
    return audio_data, sample_rate, info


# ==================== 信号处理函数 ====================
def compute_range_doppler(radar_data: np.ndarray, rx_idx: int = 0) -> np.ndarray:
    """
    计算Range-Doppler图
    
    参数:
        radar_data: shape (num_rx, num_chirps, num_samples)
        rx_idx: 使用的接收天线索引
        
    返回:
        rd_map: Range-Doppler图，shape (num_doppler, num_range)
    """
    data = radar_data[rx_idx]  # (num_chirps, num_samples)
    
    # Range FFT
    range_fft = np.fft.fft(data, axis=1)
    
    # Doppler FFT
    doppler_fft = np.fft.fft(range_fft, axis=0)
    
    # 移位使零频在中心
    rd_map = np.fft.fftshift(doppler_fft, axes=0)
    
    return np.abs(rd_map)


def compute_range_time(radar_data: np.ndarray, rx_idx: int = 0) -> np.ndarray:
    """
    计算Range-Time图
    
    参数:
        radar_data: shape (num_rx, num_chirps, num_samples)
        rx_idx: 使用的接收天线索引
        
    返回:
        rt_map: Range-Time图，shape (num_chirps, num_range)
    """
    data = radar_data[rx_idx]  # (num_chirps, num_samples)
    
    # Range FFT
    range_fft = np.fft.fft(data, axis=1)
    
    return np.abs(range_fft)


def compute_audio_spectrogram(audio_data: np.ndarray, sample_rate: int, 
                              nperseg: int = 1024, noverlap: int = 512) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    计算音频频谱图
    
    参数:
        audio_data: 音频数据
        sample_rate: 采样率
        nperseg: FFT窗口大小
        noverlap: 重叠样本数
        
    返回:
        freqs: 频率轴
        times: 时间轴
        Sxx: 频谱图
    """
    # 如果是多通道，取第一个通道
    if len(audio_data.shape) > 1:
        audio_data = audio_data[:, 0]
    
    freqs, times, Sxx = signal.spectrogram(
        audio_data, fs=sample_rate, 
        nperseg=nperseg, noverlap=noverlap,
        scaling="density"
    )
    
    return freqs, times, Sxx


def analyze_ultrasonic_band(audio_data: np.ndarray, sample_rate: int, 
                            center_freq: int = 20000, bandwidth: int = 2000) -> Dict:
    """
    分析超声波频段
    
    参数:
        audio_data: 音频数据
        sample_rate: 采样率
        center_freq: 超声波中心频率
        bandwidth: 分析带宽
        
    返回:
        分析结果字典
    """
    # 如果是多通道，取第一个通道
    if len(audio_data.shape) > 1:
        audio_data = audio_data[:, 0]
    
    # 计算FFT
    n = len(audio_data)
    fft_data = np.fft.fft(audio_data)
    freqs = np.fft.fftfreq(n, 1 / sample_rate)
    
    # 取正频率部分
    positive_mask = freqs >= 0
    freqs = freqs[positive_mask]
    magnitude = np.abs(fft_data[positive_mask])
    
    # 超声波频段
    low_freq = center_freq - bandwidth / 2
    high_freq = center_freq + bandwidth / 2
    
    # 超声波频段能量
    ultrasonic_mask = (freqs >= low_freq) & (freqs <= high_freq)
    ultrasonic_energy = np.sum(magnitude[ultrasonic_mask] ** 2)
    
    # 全频段能量
    total_energy = np.sum(magnitude ** 2)
    
    # 超声波频段峰值频率
    if np.any(ultrasonic_mask):
        peak_idx = np.argmax(magnitude[ultrasonic_mask])
        peak_freq = freqs[ultrasonic_mask][peak_idx]
        peak_magnitude = magnitude[ultrasonic_mask][peak_idx]
    else:
        peak_freq = 0
        peak_magnitude = 0
    
    return {
        "ultrasonic_energy": ultrasonic_energy,
        "total_energy": total_energy,
        "energy_ratio": ultrasonic_energy / total_energy if total_energy > 0 else 0,
        "peak_freq_hz": peak_freq,
        "peak_magnitude": peak_magnitude,
        "snr_db": 10 * np.log10(ultrasonic_energy / (total_energy - ultrasonic_energy + 1e-10)),
    }


# ==================== 数据质量验证 ====================
def validate_radar_data(radar_data: np.ndarray, info: Dict) -> Dict:
    """
    验证雷达数据质量
    
    返回:
        验证结果字典
    """
    results = {
        "valid": True,
        "warnings": [],
        "errors": [],
        "metrics": {},
    }
    
    # 检查数据形状
    if radar_data.ndim != 3:
        results["errors"].append(f"数据维度错误: 期望3维，实际{radar_data.ndim}维")
        results["valid"] = False
    
    # 检查数据范围
    if np.iscomplex(radar_data.flat[0]):
        magnitude = np.abs(radar_data)
    else:
        magnitude = np.abs(radar_data.astype(np.float32))
    
    results["metrics"]["mean_amplitude"] = float(np.mean(magnitude))
    results["metrics"]["max_amplitude"] = float(np.max(magnitude))
    results["metrics"]["std_amplitude"] = float(np.std(magnitude))
    
    # 检查是否有有效信号
    if results["metrics"]["max_amplitude"] < 10:
        results["warnings"].append("信号幅度过低，可能无有效数据")
    
    # 检查是否存在饱和
    if not np.iscomplex(radar_data.flat[0]):
        saturation_ratio = np.sum(np.abs(radar_data) > 32000) / radar_data.size
        results["metrics"]["saturation_ratio"] = float(saturation_ratio)
        if saturation_ratio > 0.01:
            results["warnings"].append(f"数据饱和比例过高: {saturation_ratio:.2%}")
    
    # 检查各通道一致性
    if radar_data.shape[0] >= 2:
        channel_powers = [np.mean(np.abs(radar_data[i]) ** 2) for i in range(radar_data.shape[0])]
        power_variation = np.std(channel_powers) / (np.mean(channel_powers) + 1e-10)
        results["metrics"]["channel_power_variation"] = float(power_variation)
        if power_variation > 0.5:
            results["warnings"].append(f"通道间功率差异过大: {power_variation:.2f}")
    
    return results


def validate_audio_data(audio_data: np.ndarray, sample_rate: int, info: Dict, 
                        expected_duration: float = 5.0) -> Dict:
    """
    验证音频数据质量
    
    参数:
        expected_duration: 期望的录音时长（秒）
        
    返回:
        验证结果字典
    """
    results = {
        "valid": True,
        "warnings": [],
        "errors": [],
        "metrics": {},
    }
    
    # 检查时长
    actual_duration = info["duration_s"]
    results["metrics"]["duration_s"] = actual_duration
    
    if actual_duration < expected_duration * 0.9:
        results["warnings"].append(f"录音时长不足: {actual_duration:.2f}s < {expected_duration}s")
    
    # 检查采样率
    if sample_rate != 44100:
        results["warnings"].append(f"采样率异常: {sample_rate} != 44100")
    
    # 分析超声波频段
    ultrasonic_analysis = analyze_ultrasonic_band(audio_data, sample_rate)
    results["metrics"].update(ultrasonic_analysis)
    
    # 检查超声波能量
    if ultrasonic_analysis["energy_ratio"] < 0.01:
        results["warnings"].append("超声波频段能量过低，可能未正常录制")
    
    # 检查信噪比
    if ultrasonic_analysis["snr_db"] < 3:
        snr_val = ultrasonic_analysis["snr_db"]
        results["warnings"].append(f"超声波信噪比过低: {snr_val:.1f} dB")
    
    # 检查是否存在削波
    if len(audio_data.shape) > 1:
        audio_mono = audio_data[:, 0]
    else:
        audio_mono = audio_data
    
    clipping_ratio = np.sum(np.abs(audio_mono) > 0.99) / len(audio_mono)
    results["metrics"]["clipping_ratio"] = float(clipping_ratio)
    if clipping_ratio > 0.001:
        results["warnings"].append(f"音频削波比例: {clipping_ratio:.4%}")
    
    return results


# ==================== 可视化函数 ====================
def visualize_radar_data(radar_data: np.ndarray, info: Dict, 
                         config: Dict = None, save_path: str = None):
    """
    可视化雷达数据
    
    参数:
        radar_data: shape (num_rx, num_chirps, num_samples)
        info: 数据信息字典
        config: 雷达配置
        save_path: 保存路径（可选）
    """
    config = config or DEFAULT_RADAR_CONFIG
    
    fig = plt.figure(figsize=(16, 12))
    fig.suptitle("毫米波雷达数据分析", fontsize=14, fontweight="bold")
    
    gs = GridSpec(3, 3, figure=fig, hspace=0.3, wspace=0.3)
    
    # 1. Range-Doppler图 (RX0)
    ax1 = fig.add_subplot(gs[0, 0])
    rd_map = compute_range_doppler(radar_data, rx_idx=0)
    rd_db = 20 * np.log10(rd_map + 1e-10)
    im1 = ax1.imshow(rd_db, aspect="auto", cmap="jet", 
                     vmin=np.percentile(rd_db, 10), vmax=np.percentile(rd_db, 99))
    ax1.set_title("Range-Doppler图 (RX0)")
    ax1.set_xlabel("Range Bin")
    ax1.set_ylabel("Doppler Bin")
    plt.colorbar(im1, ax=ax1, label="dB")
    
    # 2. Range-Time图 (RX0)
    ax2 = fig.add_subplot(gs[0, 1])
    rt_map = compute_range_time(radar_data, rx_idx=0)
    rt_db = 20 * np.log10(rt_map + 1e-10)
    im2 = ax2.imshow(rt_db, aspect="auto", cmap="jet",
                     vmin=np.percentile(rt_db, 10), vmax=np.percentile(rt_db, 99))
    ax2.set_title("Range-Time图 (RX0)")
    ax2.set_xlabel("Range Bin")
    ax2.set_ylabel("Chirp Index")
    plt.colorbar(im2, ax=ax2, label="dB")
    
    # 3. 各RX通道Range Profile对比
    ax3 = fig.add_subplot(gs[0, 2])
    colors = ["b", "r", "g", "m"]
    for rx in range(min(radar_data.shape[0], 4)):
        range_profile = np.mean(np.abs(np.fft.fft(radar_data[rx], axis=1)), axis=0)
        ax3.plot(20 * np.log10(range_profile + 1e-10), colors[rx], 
                 label=f"RX{rx}", alpha=0.7)
    ax3.set_title("各通道Range Profile")
    ax3.set_xlabel("Range Bin")
    ax3.set_ylabel("幅度 (dB)")
    ax3.legend()
    ax3.grid(True, alpha=0.3)
    
    # 4. 原始ADC波形（第一个chirp）
    ax4 = fig.add_subplot(gs[1, 0])
    if np.iscomplex(radar_data.flat[0]):
        ax4.plot(np.real(radar_data[0, 0, :]), "b-", label="I", alpha=0.7)
        ax4.plot(np.imag(radar_data[0, 0, :]), "r-", label="Q", alpha=0.7)
    else:
        ax4.plot(radar_data[0, 0, :], "b-", alpha=0.7)
    ax4.set_title("原始ADC波形 (Chirp 0, RX0)")
    ax4.set_xlabel("采样点")
    ax4.set_ylabel("幅度")
    ax4.legend()
    ax4.grid(True, alpha=0.3)
    
    # 5. 信号功率随时间变化
    ax5 = fig.add_subplot(gs[1, 1])
    power_vs_time = np.mean(np.abs(radar_data[0]) ** 2, axis=1)
    ax5.plot(power_vs_time, "b-")
    ax5.set_title("信号功率 vs 时间")
    ax5.set_xlabel("Chirp Index")
    ax5.set_ylabel("平均功率")
    ax5.grid(True, alpha=0.3)
    
    # 6. 数据统计信息
    ax6 = fig.add_subplot(gs[1, 2])
    ax6.axis("off")
    stats_text = f"""
    === 数据统计 ===
    
    文件大小: {info.get("file_size_mb", 0):.2f} MB
    Chirp数量: {info.get("num_chirps", 0)}
    采样点数: {info.get("num_samples", 0)}
    RX通道数: {info.get("num_rx", 0)}
    
    估计时长: {info.get("duration_estimate_s", 0):.2f} s
    解析状态: {"成功" if info.get("parse_success", False) else "简化模式"}
    
    === 信号特征 ===
    
    平均幅度: {np.mean(np.abs(radar_data)):.2f}
    最大幅度: {np.max(np.abs(radar_data)):.2f}
    标准差: {np.std(np.abs(radar_data)):.2f}
    """
    ax6.text(0.1, 0.9, stats_text, transform=ax6.transAxes, fontsize=10,
             verticalalignment="top", fontfamily="monospace",
             bbox=dict(boxstyle="round", facecolor="wheat", alpha=0.5))
    
    # 7-9. 各RX通道的Range-Doppler图
    for rx in range(min(radar_data.shape[0], 3)):
        ax = fig.add_subplot(gs[2, rx])
        rd_map = compute_range_doppler(radar_data, rx_idx=rx)
        rd_db = 20 * np.log10(rd_map + 1e-10)
        im = ax.imshow(rd_db, aspect="auto", cmap="jet",
                       vmin=np.percentile(rd_db, 10), vmax=np.percentile(rd_db, 99))
        ax.set_title(f"Range-Doppler (RX{rx})")
        ax.set_xlabel("Range Bin")
        ax.set_ylabel("Doppler Bin")
    
    plt.tight_layout()
    
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
        print(f"雷达数据可视化已保存: {save_path}")
    
    plt.show()
    plt.close()


def visualize_audio_data(audio_data: np.ndarray, sample_rate: int, info: Dict,
                         config: Dict = None, save_path: str = None):
    """
    可视化音频数据
    
    参数:
        audio_data: 音频数据
        sample_rate: 采样率
        info: 数据信息字典
        config: 音频配置
        save_path: 保存路径（可选）
    """
    config = config or DEFAULT_AUDIO_CONFIG
    
    # 如果是多通道，取第一个通道进行分析
    if len(audio_data.shape) > 1:
        audio_mono = audio_data[:, 0]
    else:
        audio_mono = audio_data
    
    fig = plt.figure(figsize=(16, 12))
    fig.suptitle("超声波音频数据分析", fontsize=14, fontweight="bold")
    
    gs = GridSpec(3, 3, figure=fig, hspace=0.3, wspace=0.3)
    
    # 1. 时域波形
    ax1 = fig.add_subplot(gs[0, :2])
    time_axis = np.arange(len(audio_mono)) / sample_rate
    ax1.plot(time_axis, audio_mono, "b-", linewidth=0.5)
    ax1.set_title("时域波形")
    ax1.set_xlabel("时间 (s)")
    ax1.set_ylabel("幅度")
    ax1.grid(True, alpha=0.3)
    
    # 2. 频谱图
    ax2 = fig.add_subplot(gs[1, :2])
    try:
        freqs, times, Sxx = compute_audio_spectrogram(audio_mono, sample_rate)
        Sxx_db = 10 * np.log10(Sxx + 1e-10)
        im2 = ax2.pcolormesh(times, freqs / 1000, Sxx_db, shading="gouraud", cmap="jet")
        ax2.set_title("频谱图")
        ax2.set_xlabel("时间 (s)")
        ax2.set_ylabel("频率 (kHz)")
        ax2.set_ylim([0, sample_rate / 2000])
        plt.colorbar(im2, ax=ax2, label="dB")
        
        # 标注超声波频段
        ax2.axhline(y=config["ultrasonic_freq"] / 1000, color="r", 
                    linestyle="--", label="超声波中心频率")
    except Exception as e:
        ax2.text(0.5, 0.5, f"频谱图计算失败:\n{str(e)}", 
                 transform=ax2.transAxes, ha="center", va="center")
    
    # 3. 超声波频段频谱图
    ax3 = fig.add_subplot(gs[2, :2])
    try:
        freqs, times, Sxx = compute_audio_spectrogram(audio_mono, sample_rate, 
                                                       nperseg=2048, noverlap=1024)
        Sxx_db = 10 * np.log10(Sxx + 1e-10)
        
        # 只显示18k-22k频段
        freq_mask = (freqs >= 18000) & (freqs <= 22000)
        im3 = ax3.pcolormesh(times, freqs[freq_mask] / 1000, Sxx_db[freq_mask], 
                             shading="gouraud", cmap="jet")
        ax3.set_title("超声波频段频谱图 (18-22 kHz)")
        ax3.set_xlabel("时间 (s)")
        ax3.set_ylabel("频率 (kHz)")
        plt.colorbar(im3, ax=ax3, label="dB")
    except Exception as e:
        ax3.text(0.5, 0.5, f"超声波频谱图计算失败:\n{str(e)}", 
                 transform=ax3.transAxes, ha="center", va="center")
    
    # 4. 全频段频谱
    ax4 = fig.add_subplot(gs[0, 2])
    n = len(audio_mono)
    fft_data = np.fft.fft(audio_mono)
    freqs_full = np.fft.fftfreq(n, 1 / sample_rate)
    positive_mask = freqs_full >= 0
    magnitude_db = 20 * np.log10(np.abs(fft_data[positive_mask]) + 1e-10)
    ax4.plot(freqs_full[positive_mask] / 1000, magnitude_db, "b-", linewidth=0.5)
    ax4.axvline(x=config["ultrasonic_freq"] / 1000, color="r", 
                linestyle="--", label="超声波中心")
    ax4.set_title("全频段频谱")
    ax4.set_xlabel("频率 (kHz)")
    ax4.set_ylabel("幅度 (dB)")
    ax4.set_xlim([0, sample_rate / 2000])
    ax4.legend()
    ax4.grid(True, alpha=0.3)
    
    # 5. 超声波频段频谱（放大）
    ax5 = fig.add_subplot(gs[1, 2])
    ultrasonic_mask = (freqs_full >= 18000) & (freqs_full <= 22000)
    ax5.plot(freqs_full[ultrasonic_mask] / 1000, 
             20 * np.log10(np.abs(fft_data[ultrasonic_mask]) + 1e-10), "b-")
    ax5.axvline(x=config["ultrasonic_freq"] / 1000, color="r", 
                linestyle="--", label="20kHz")
    ax5.set_title("超声波频段频谱 (18-22 kHz)")
    ax5.set_xlabel("频率 (kHz)")
    ax5.set_ylabel("幅度 (dB)")
    ax5.legend()
    ax5.grid(True, alpha=0.3)
    
    # 6. 数据统计信息
    ax6 = fig.add_subplot(gs[2, 2])
    ax6.axis("off")
    
    # 分析超声波频段
    ultrasonic_analysis = analyze_ultrasonic_band(audio_mono, sample_rate)
    
    peak_freq = ultrasonic_analysis["peak_freq_hz"]
    energy_ratio = ultrasonic_analysis["energy_ratio"]
    snr_db = ultrasonic_analysis["snr_db"]
    max_amp = np.max(np.abs(audio_mono))
    rms = np.sqrt(np.mean(audio_mono**2))
    clip_ratio = np.sum(np.abs(audio_mono) > 0.99) / len(audio_mono)
    
    stats_text = f"""
    === 音频统计 ===
    
    采样率: {sample_rate} Hz
    时长: {info.get("duration_s", 0):.2f} s
    通道数: {info.get("channels", 1)}
    文件大小: {info.get("file_size_mb", 0):.2f} MB
    
    === 超声波分析 ===
    
    峰值频率: {peak_freq:.1f} Hz
    能量比例: {energy_ratio:.4f}
    信噪比: {snr_db:.1f} dB
    
    === 信号质量 ===
    
    最大幅度: {max_amp:.4f}
    均方根: {rms:.4f}
    削波比例: {clip_ratio:.4%}
    """
    ax6.text(0.1, 0.9, stats_text, transform=ax6.transAxes, fontsize=10,
             verticalalignment="top", fontfamily="monospace",
             bbox=dict(boxstyle="round", facecolor="lightblue", alpha=0.5))
    
    plt.tight_layout()
    
    if save_path:
        plt.savefig(save_path, dpi=150, bbox_inches="tight")
        print(f"音频数据可视化已保存: {save_path}")
    
    plt.show()
    plt.close()


def visualize_sample(radar_path: str, audio_path: str, 
                     metadata: Dict = None, save_dir: str = None,
                     radar_config: Dict = None, audio_config: Dict = None):
    """
    可视化单个样本（雷达+音频）
    
    参数:
        radar_path: 雷达文件路径
        audio_path: 音频文件路径
        metadata: 元数据（可选）
        save_dir: 保存目录（可选）
        radar_config: 雷达配置（可选，从元数据读取）
        audio_config: 音频配置（可选，从元数据读取）
    """
    print("=" * 60)
    print("数据验证与可视化")
    print("=" * 60)
    
    # 从元数据读取配置
    if metadata:
        if "radar_params" in metadata and radar_config is None:
            radar_config = metadata.get("radar_params", {})
        if "audio_params" in metadata and audio_config is None:
            audio_config = metadata.get("audio_params", {})
    
    # 加载雷达数据
    print(f"\n[1/4] 加载雷达数据: {radar_path}")
    try:
        radar_data, radar_info = load_radar_data(radar_path, radar_config)
        file_size = radar_info.get("file_size_mb", 0)
        num_chirps = radar_info.get("num_chirps", 0)
        print(f"   加载成功: {file_size:.2f} MB, {num_chirps} chirps")
    except Exception as e:
        print(f"   加载失败: {str(e)}")
        radar_data, radar_info = None, None
    
    # 加载音频数据
    print(f"\n[2/4] 加载音频数据: {audio_path}")
    try:
        audio_data, sample_rate, audio_info = load_audio_data(audio_path, audio_config)
        duration = audio_info.get("duration_s", 0)
        print(f"   加载成功: {duration:.2f}s, {sample_rate} Hz")
    except Exception as e:
        print(f"   加载失败: {str(e)}")
        audio_data, sample_rate, audio_info = None, None, None
    
    # 验证数据
    print("\n[3/4] 验证数据质量")
    
    if radar_data is not None:
        radar_validation = validate_radar_data(radar_data, radar_info)
        print(f"\n  雷达数据验证:")
        mean_amp = radar_validation["metrics"].get("mean_amplitude", 0)
        max_amp = radar_validation["metrics"].get("max_amplitude", 0)
        print(f"    平均幅度: {mean_amp:.2f}")
        print(f"    最大幅度: {max_amp:.2f}")
        for warning in radar_validation["warnings"]:
            print(f"     {warning}")
        for error in radar_validation["errors"]:
            print(f"     {error}")
    
    if audio_data is not None:
        audio_validation = validate_audio_data(audio_data, sample_rate, audio_info)
        print(f"\n  音频数据验证:")
        duration = audio_validation["metrics"].get("duration_s", 0)
        energy_ratio = audio_validation["metrics"].get("energy_ratio", 0)
        snr = audio_validation["metrics"].get("snr_db", 0)
        print(f"    时长: {duration:.2f}s")
        print(f"    超声波能量比: {energy_ratio:.4f}")
        print(f"    超声波SNR: {snr:.1f} dB")
        for warning in audio_validation["warnings"]:
            print(f"     {warning}")
        for error in audio_validation["errors"]:
            print(f"     {error}")
    
    # 可视化
    print("\n[4/4] 生成可视化图表")
    
    if save_dir:
        os.makedirs(save_dir, exist_ok=True)
        radar_save = os.path.join(save_dir, "radar_analysis.png")
        audio_save = os.path.join(save_dir, "audio_analysis.png")
    else:
        radar_save = None
        audio_save = None
    
    if radar_data is not None:
        visualize_radar_data(radar_data, radar_info, radar_config, save_path=radar_save)
    
    if audio_data is not None:
        visualize_audio_data(audio_data, sample_rate, audio_info, audio_config, save_path=audio_save)
    
    print("\n" + "=" * 60)
    print("验证完成!")
    print("=" * 60)


# ==================== 批量验证 ====================
def batch_validate(root_dir: str, output_report: str = None) -> Dict:
    """
    批量验证数据目录中的所有样本
    
    参数:
        root_dir: 数据根目录（包含subjects/文件夹）
        output_report: 输出报告路径（可选）
        
    返回:
        验证报告字典
    """
    report = {
        "timestamp": datetime.now().isoformat(),
        "root_dir": root_dir,
        "total_subjects": 0,
        "total_samples": 0,
        "valid_samples": 0,
        "invalid_samples": 0,
        "subjects": {},
        "errors": [],
        "warnings": [],
    }
    
    print(f"开始批量验证: {root_dir}")
    print("=" * 60)
    
    # 遍历subjects目录
    subjects_dir = root_dir
    if os.path.exists(os.path.join(root_dir, "subjects")):
        subjects_dir = os.path.join(root_dir, "subjects")
    
    for subject_name in sorted(os.listdir(subjects_dir)):
        subject_dir = os.path.join(subjects_dir, subject_name)
        if not os.path.isdir(subject_dir):
            continue
        
        report["total_subjects"] += 1
        subject_report = {
            "samples": [],
            "valid_count": 0,
            "invalid_count": 0,
        }
        
        # 读取元数据
        meta_path = os.path.join(subject_dir, "samples_metadata.json")
        if not os.path.exists(meta_path):
            report["errors"].append(f"[{subject_name}] 找不到元数据文件")
            continue
        
        with open(meta_path, "r", encoding="utf-8") as f:
            meta = json.load(f)
        
        num_samples = len(meta.get("samples", []))
        print(f"\n验证被试 {subject_name} ({num_samples} 个样本)...")
        
        for sample in meta.get("samples", []):
            report["total_samples"] += 1
            sample_id = sample.get("sample_id", "unknown")
            
            sample_result = {
                "sample_id": sample_id,
                "scene_code": sample.get("scene", {}).get("code", ""),
                "valid": True,
                "radar_valid": False,
                "audio_valid": False,
                "warnings": [],
            }
            
            # 验证雷达文件
            radar_path = os.path.join(subject_dir, sample.get("radar_file", ""))
            if os.path.exists(radar_path):
                try:
                    radar_data, radar_info = load_radar_data(radar_path)
                    validation = validate_radar_data(radar_data, radar_info)
                    sample_result["radar_valid"] = len(validation["errors"]) == 0
                    sample_result["warnings"].extend(validation["warnings"])
                except Exception as e:
                    sample_result["warnings"].append(f"雷达数据加载失败: {str(e)}")
            else:
                sample_result["warnings"].append(f"雷达文件不存在")
            
            # 验证音频文件
            audio_path = os.path.join(subject_dir, sample.get("audio_file", ""))
            if os.path.exists(audio_path):
                try:
                    audio_data, sr, audio_info = load_audio_data(audio_path)
                    validation = validate_audio_data(audio_data, sr, audio_info)
                    sample_result["audio_valid"] = len(validation["errors"]) == 0
                    sample_result["warnings"].extend(validation["warnings"])
                except Exception as e:
                    sample_result["warnings"].append(f"音频数据加载失败: {str(e)}")
            else:
                sample_result["warnings"].append(f"音频文件不存在")
            
            # 判断整体有效性
            sample_result["valid"] = sample_result["radar_valid"] and sample_result["audio_valid"]
            
            if sample_result["valid"]:
                report["valid_samples"] += 1
                subject_report["valid_count"] += 1
            else:
                report["invalid_samples"] += 1
                subject_report["invalid_count"] += 1
            
            subject_report["samples"].append(sample_result)
        
        report["subjects"][subject_name] = subject_report
        valid_cnt = subject_report["valid_count"]
        total_cnt = len(subject_report["samples"])
        print(f"   {valid_cnt}/{total_cnt} 样本有效")
    
    # 打印总结
    print("\n" + "=" * 60)
    print("批量验证总结")
    print("=" * 60)
    print(f"总被试数: {report['total_subjects']}")
    print(f"总样本数: {report['total_samples']}")
    valid_pct = report['valid_samples']/max(report['total_samples'],1)*100
    print(f"有效样本: {report['valid_samples']} ({valid_pct:.1f}%)")
    print(f"无效样本: {report['invalid_samples']}")
    
    # 保存报告
    if output_report:
        with open(output_report, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f"\n验证报告已保存: {output_report}")
    
    return report


# ==================== 命令行接口 ====================
def main():
    parser = argparse.ArgumentParser(
        description="数据验证与可视化工具 - 验证毫米波雷达和音频数据质量",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 验证单个样本
  python visualize_data.py --radar data/sample.bin --audio data/sample.wav
  
  # 验证指定目录下的样本
  python visualize_data.py --dir E:/data/subjects/subject_001 --sample 1
  
  # 批量验证并生成报告
  python visualize_data.py --dir E:/data/subjects --batch --report validation_report.json
  
  # 保存可视化结果
  python visualize_data.py --radar data/sample.bin --audio data/sample.wav --save ./output
        """
    )
    
    parser.add_argument("--radar", type=str, help="雷达数据文件路径 (.bin)")
    parser.add_argument("--audio", type=str, help="音频数据文件路径 (.wav)")
    parser.add_argument("--dir", type=str, help="数据目录路径")
    parser.add_argument("--sample", type=int, help="样本ID（与--dir配合使用）")
    parser.add_argument("--batch", action="store_true", help="批量验证模式")
    parser.add_argument("--report", type=str, help="验证报告输出路径")
    parser.add_argument("--save", type=str, help="可视化结果保存目录")
    
    args = parser.parse_args()
    
    # 批量验证模式
    if args.batch and args.dir:
        report_path = args.report or "validation_report.json"
        batch_validate(args.dir, report_path)
        return
    
    # 单文件模式
    if args.radar or args.audio:
        radar_path = args.radar
        audio_path = args.audio
        
        if radar_path and not audio_path:
            # 尝试自动查找对应的音频文件
            audio_path = radar_path.replace(".bin", ".wav")
            if not os.path.exists(audio_path):
                audio_path = None
        
        if audio_path and not radar_path:
            # 尝试自动查找对应的雷达文件
            radar_path = audio_path.replace(".wav", ".bin")
            if not os.path.exists(radar_path):
                radar_path = None
        
        visualize_sample(radar_path, audio_path, save_dir=args.save)
        return
    
    # 目录+样本ID模式
    if args.dir and args.sample is not None:
        subject_dir = args.dir
        meta_path = os.path.join(subject_dir, "samples_metadata.json")
        
        if not os.path.exists(meta_path):
            print(f"错误: 找不到元数据文件 {meta_path}")
            return
        
        with open(meta_path, "r", encoding="utf-8") as f:
            meta = json.load(f)
        
        # 查找指定样本
        sample_found = False
        for sample in meta.get("samples", []):
            if sample.get("sample_id") == args.sample:
                radar_path = os.path.join(subject_dir, sample.get("radar_file", ""))
                audio_path = os.path.join(subject_dir, sample.get("audio_file", ""))
                visualize_sample(radar_path, audio_path, metadata=sample, save_dir=args.save)
                sample_found = True
                break
        
        if not sample_found:
            print(f"错误: 未找到样本ID {args.sample}")
        return
    
    # 交互式模式
    print("数据验证与可视化工具")
    print("=" * 40)
    print("\n请使用以下方式运行:")
    print("  python visualize_data.py --help")
    print("\n或者输入文件路径进行验证:")
    
    radar_path = input("\n雷达文件路径 (.bin): ").strip()
    audio_path = input("音频文件路径 (.wav): ").strip()
    
    if radar_path or audio_path:
        visualize_sample(radar_path or None, audio_path or None)


if __name__ == "__main__":
    main()
