function saveMetadataV2(subjectId, sampleId, sceneCode, syncQuality, captureTime, dataRoot, csvFilePath)
% saveMetadataV2 - 新版元数据保存函数，采用统一的被试级别元数据文件
%
% 输入:
%   subjectId: 被试ID (整数，例如 1)
%   sampleId: 样本ID (整数，例如 1)
%   sceneCode: 场景代码 (字符串，格式如 'A1-B1-C1-D1-E1')
%   syncQuality: 同步质量信息结构体
%       - ntp_offset_ms: NTP偏移量(毫秒)
%       - audio_start_time: 音频开始时间戳
%       - radar_start_time: 雷达开始时间戳
%   captureTime: 采集时间字符串 (格式 'yyyy-MM-dd HH:mm:ss')
%   dataRoot: 数据根目录（包含subjects/文件夹）
%   csvFilePath: 场景CSV文件路径（可选，默认 'radar/scenes_file.csv'）
%
% 设计原则:
%   - 场景描述直接从CSV文件读取，不硬编码
%   - 支持CSV文件动态修改（增删改场景）
%   - 文件命名使用场景代码，保证唯一性和可扩展性
%
% 示例:
%   syncInfo = struct('ntp_offset_ms', 15.2, ...
%                     'audio_start_time', '2024-01-15 10:30:00.123', ...
%                     'radar_start_time', '2024-01-15 10:30:00.138');
%   saveMetadataV2(1, 5, 'A1-B1-C1-D1-E1', syncInfo, '2024-01-15 10:30:00', 'E:\data\');

    if nargin < 7 || isempty(csvFilePath)
        csvFilePath = 'radar/scenes_file.csv';
    end

    % 从CSV获取场景信息（直接读取，不硬编码）
    sceneInfo = getSceneInfo(sceneCode, csvFilePath);
    
    % 创建样本记录
    sample = struct();
    sample.sample_id = sampleId;
    sample.scene = struct();
    sample.scene.code = sceneCode;
    sample.scene.intro = sceneInfo.intro;  % 直接使用CSV中的中文描述
    sample.scene.idx = sceneInfo.idx;       % 场景索引号
    sample.sync_quality = syncQuality;
    sample.capture_time = captureTime;
    
    % 文件命名：使用场景代码，保证唯一性和可扩展性
    % 格式: sample_{sampleId}_{sceneCode}.bin/wav
    % 例如: sample_005_A1_B1_C1_D1_E1.bin
    safeSceneCode = strrep(sceneCode, '-', '_');  % 替换连字符，避免文件名问题
    radarFilename = sprintf('sample_%03d_%s.bin', sampleId, safeSceneCode);
    audioFilename = sprintf('sample_%03d_%s.wav', sampleId, safeSceneCode);
    sample.radar_file = fullfile('radar', radarFilename);
    sample.audio_file = fullfile('audio', audioFilename);
    
    % 被试文件夹路径
    subjectDir = fullfile(dataRoot, 'subjects', sprintf('subject_%03d', subjectId));
    metaPath = fullfile(subjectDir, 'samples_metadata.json');
    
    % 如果被试文件夹不存在，创建它
    if ~exist(subjectDir, 'dir')
        mkdir(subjectDir);
        mkdir(fullfile(subjectDir, 'radar'));
        mkdir(fullfile(subjectDir, 'audio'));
        fprintf('创建被试文件夹: subject_%03d\n', subjectId);
    end
    
    % 读取或创建元数据
    if exist(metaPath, 'file')
        % 读取现有元数据
        fid = fopen(metaPath, 'r', 'n', 'UTF-8');
        jsonStr = fread(fid, '*char')';
        fclose(fid);
        subjectMeta = jsondecode(jsonStr);
        
        % 追加新样本
        subjectMeta.samples = [subjectMeta.samples; sample];
        subjectMeta.num_samples = length(subjectMeta.samples);
    else
        % 创建新元数据
        subjectMeta = struct();
        subjectMeta.subject_id = subjectId;
        subjectMeta.num_samples = 1;
        subjectMeta.samples = sample;
    end
    
    % 保存元数据
    jsonStr = jsonencode(subjectMeta, 'PrettyPrint', true);
    fid = fopen(metaPath, 'w', 'n', 'UTF-8');
    fprintf(fid, '%s', jsonStr);
    fclose(fid);
    
    fprintf('元数据已保存: 被试 %03d, 样本 %03d, 场景 %s\n', ...
            subjectId, sampleId, sceneCode);
    fprintf('  描述: %s\n', sceneInfo.intro);
end
