function reorganizeData(oldDataRoot, newDataRoot, csvFilePath)
% reorganizeData - 将旧的平铺数据格式转换为新的分层结构
%
% 输入:
%   oldDataRoot: 旧数据根目录（包含audio/文件夹，所有文件平铺在一起）
%   newDataRoot: 新数据根目录（将创建subjects/文件夹层次结构）
%   csvFilePath: 场景CSV文件路径（可选，默认 'radar/scenes_file.csv'）
%
% 输出:
%   在newDataRoot创建新的目录结构:
%   newDataRoot/
%     └── subjects/
%         ├── subject_001/
%         │   ├── samples_metadata.json
%         │   ├── radar/
%         │   │   ├── sample_001_A1_B1_C1_D1_E1.bin
%         │   │   └── ...
%         │   └── audio/
%         │       ├── sample_001_A1_B1_C1_D1_E1.wav
%         │       └── ...
%         └── subject_002/
%             └── ...
%
% 设计原则:
%   - 场景描述直接从CSV文件读取
%   - 文件命名使用场景代码，保证唯一性和可扩展性
%
% 示例:
%   reorganizeData('E:\old_data\', 'E:\new_data\');

    if nargin < 3 || isempty(csvFilePath)
        csvFilePath = 'radar/scenes_file.csv';
    end

    fprintf('========== 数据重组开始 ==========\n');
    fprintf('旧数据目录: %s\n', oldDataRoot);
    fprintf('新数据目录: %s\n', newDataRoot);
    
    % 检查旧数据目录
    oldAudioDir = fullfile(oldDataRoot, 'audio');
    if ~exist(oldAudioDir, 'dir')
        error('找不到旧音频目录: %s', oldAudioDir);
    end
    
    % 创建新目录结构
    newSubjectsDir = fullfile(newDataRoot, 'subjects');
    if ~exist(newSubjectsDir, 'dir')
        mkdir(newSubjectsDir);
        fprintf('创建目录: %s\n', newSubjectsDir);
    end
    
    % 扫描所有JSON元数据文件
    jsonFiles = dir(fullfile(oldAudioDir, '*_metadata.json'));
    fprintf('找到 %d 个元数据文件\n', length(jsonFiles));
    
    % 按被试组织数据
    subjectData = containers.Map('KeyType', 'int32', 'ValueType', 'any');
    
    for i = 1:length(jsonFiles)
        jsonPath = fullfile(jsonFiles(i).folder, jsonFiles(i).name);
        
        % 读取元数据
        fid = fopen(jsonPath, 'r', 'n', 'UTF-8');
        jsonStr = fread(fid, '*char')';
        fclose(fid);
        metadata = jsondecode(jsonStr);
        
        % 提取信息
        subjectId = metadata.subject_id;
        sampleId = metadata.sample_id;
        sceneCode = metadata.scene.code;
        
        % 从CSV获取场景信息（直接读取，不硬编码）
        sceneInfo = getSceneInfo(sceneCode, csvFilePath);
        
        % 构建样本记录
        sample = struct();
        sample.sample_id = sampleId;
        sample.scene = struct();
        sample.scene.code = sceneCode;
        sample.scene.intro = sceneInfo.intro;  % 使用CSV中的描述
        sample.scene.idx = sceneInfo.idx;
        sample.sync_quality = metadata.sync_quality;
        sample.capture_time = metadata.capture_time;
        
        % 文件命名：使用场景代码（保证唯一性和可扩展性）
        safeSceneCode = strrep(sceneCode, '-', '_');
        radarFilename = sprintf('sample_%03d_%s.bin', sampleId, safeSceneCode);
        audioFilename = sprintf('sample_%03d_%s.wav', sampleId, safeSceneCode);
        
        sample.radar_file = fullfile('radar', radarFilename);
        sample.audio_file = fullfile('audio', audioFilename);
        
        % 添加到对应被试
        if isKey(subjectData, subjectId)
            samples = subjectData(subjectId);
            samples = [samples; sample];
            subjectData(subjectId) = samples;
        else
            subjectData(subjectId) = sample;
        end
        
        % 解析原始文件名找到对应的数据文件
        [~, baseFilename, ~] = fileparts(jsonFiles(i).name);
        baseFilename = strrep(baseFilename, '_metadata', ''); % 去掉_metadata后缀
        
        % 源文件路径
        oldRadarFile = fullfile(oldAudioDir, [baseFilename '.bin']);
        oldAudioFile = fullfile(oldAudioDir, [baseFilename '.wav']);
        
        % 目标路径
        subjectDir = fullfile(newSubjectsDir, sprintf('subject_%03d', subjectId));
        if ~exist(subjectDir, 'dir')
            mkdir(subjectDir);
            mkdir(fullfile(subjectDir, 'radar'));
            mkdir(fullfile(subjectDir, 'audio'));
            fprintf('创建被试目录: subject_%03d\n', subjectId);
        end
        
        newRadarFile = fullfile(subjectDir, 'radar', radarFilename);
        newAudioFile = fullfile(subjectDir, 'audio', audioFilename);
        
        % 复制文件
        if exist(oldRadarFile, 'file')
            copyfile(oldRadarFile, newRadarFile);
        else
            warning('找不到雷达文件: %s', oldRadarFile);
        end
        
        if exist(oldAudioFile, 'file')
            copyfile(oldAudioFile, newAudioFile);
        else
            warning('找不到音频文件: %s', oldAudioFile);
        end
        
        if mod(i, 50) == 0
            fprintf('  已处理 %d/%d 个样本\n', i, length(jsonFiles));
        end
    end
    
    % 为每个被试生成统一的元数据文件
    fprintf('\n生成被试元数据文件...\n');
    subjectIds = cell2mat(keys(subjectData));
    for i = 1:length(subjectIds)
        subjectId = subjectIds(i);
        samples = subjectData(subjectId);
        
        % 创建被试元数据结构
        subjectMeta = struct();
        subjectMeta.subject_id = subjectId;
        subjectMeta.num_samples = length(samples);
        subjectMeta.samples = samples;
        
        % 保存
        subjectDir = fullfile(newSubjectsDir, sprintf('subject_%03d', subjectId));
        metaPath = fullfile(subjectDir, 'samples_metadata.json');
        
        jsonStr = jsonencode(subjectMeta, 'PrettyPrint', true);
        fid = fopen(metaPath, 'w', 'n', 'UTF-8');
        fprintf(fid, '%s', jsonStr);
        fclose(fid);
        
        fprintf('  被试 %03d: %d 个样本\n', subjectId, length(samples));
    end
    
    fprintf('\n========== 数据重组完成 ==========\n');
    fprintf('总被试数: %d\n', length(subjectIds));
    fprintf('总样本数: %d\n', length(jsonFiles));
    fprintf('新数据位置: %s\n', newSubjectsDir);
end
