function createDatasetInfo(dataRoot, outputPath)
% createDatasetInfo - 生成数据集元信息文件
%
% 输入:
%   dataRoot: 数据集根目录（包含subjects/文件夹）
%   outputPath: 输出路径（可选，默认为dataRoot/dataset_info.json）
%
% 输出:
%   在dataRoot生成dataset_info.json文件
%
% 示例:
%   createDatasetInfo('D:\multimodal_dataset\');

    if nargin < 2
        outputPath = fullfile(dataRoot, 'dataset_info.json');
    end
    
    fprintf('========== 生成数据集元信息 ==========\n');
    
    % 初始化数据集信息结构
    datasetInfo = struct();
    datasetInfo.dataset_name = 'Multimodal_Human_Activity_Detection';
    datasetInfo.version = '1.0.0';
    datasetInfo.created_date = datestr(now, 'yyyy-mm-dd');
    datasetInfo.description = '多模态（毫米波雷达+超声波）人体活动检测数据集，包含40种场景';
    datasetInfo.modalities = {'mmwave_radar', 'ultrasonic_audio'};
    
    % 扫描subjects文件夹
    subjectsDir = fullfile(dataRoot, 'subjects');
    if ~exist(subjectsDir, 'dir')
        warning('subjects文件夹不存在: %s', subjectsDir);
        return;
    end
    
    subjectFolders = dir(subjectsDir);
    subjectFolders = subjectFolders([subjectFolders.isdir] & ~ismember({subjectFolders.name}, {'.', '..'}));
    
    numSubjects = length(subjectFolders);
    totalSamples = 0;
    sceneCount = containers.Map('KeyType', 'char', 'ValueType', 'int32');
    subjectSampleCount = struct();
    
    fprintf('扫描 %d 个被试文件夹...\n', numSubjects);
    
    % 遍历每个被试
    for i = 1:numSubjects
        subjectName = subjectFolders(i).name;
        subjectPath = fullfile(subjectsDir, subjectName);
        metaPath = fullfile(subjectPath, 'samples_metadata.json');
        
        if exist(metaPath, 'file')
            % 读取元数据
            fid = fopen(metaPath, 'r', 'n', 'UTF-8');
            jsonStr = fread(fid, '*char')';
            fclose(fid);
            subjectMeta = jsondecode(jsonStr);
            
            numSamples = length(subjectMeta.samples);
            totalSamples = totalSamples + numSamples;
            subjectSampleCount.(sprintf('subject_%03d', subjectMeta.subject_id)) = numSamples;
            
            % 统计场景分布
            for j = 1:numSamples
                sceneCode = subjectMeta.samples(j).scene.code;
                if isKey(sceneCount, sceneCode)
                    sceneCount(sceneCode) = sceneCount(sceneCode) + 1;
                else
                    sceneCount(sceneCode) = 1;
                end
            end
            
            fprintf('  被试 %s: %d 样本\n', subjectName, numSamples);
        else
            warning('找不到元数据文件: %s', metaPath);
        end
    end
    
    % 统计信息
    datasetInfo.statistics = struct();
    datasetInfo.statistics.num_subjects = numSubjects;
    datasetInfo.statistics.num_scenes = length(sceneCount);
    datasetInfo.statistics.total_samples = totalSamples;
    if numSubjects > 0
        datasetInfo.statistics.samples_per_subject = totalSamples / numSubjects;
    end
    
    % 场景统计
    sceneStats = struct();
    sceneKeys = keys(sceneCount);
    for i = 1:length(sceneKeys)
        sceneStats.(sceneKeys{i}) = sceneCount(sceneKeys{i});
    end
    datasetInfo.statistics.by_scene = sceneStats;
    datasetInfo.statistics.by_subject = subjectSampleCount;
    
    % 硬件配置
    datasetInfo.hardware_config = struct();
    datasetInfo.hardware_config.radar = struct();
    datasetInfo.hardware_config.radar.model = 'TI mmWave AWR1843';
    datasetInfo.hardware_config.radar.num_rx_antennas = 4;
    datasetInfo.hardware_config.radar.chirp_samples = 256;
    datasetInfo.hardware_config.radar.sample_rate_hz = 4000000;
    datasetInfo.hardware_config.radar.bandwidth_mhz = 152.1;
    datasetInfo.hardware_config.radar.center_freq_ghz = 77;
    
    datasetInfo.hardware_config.audio = struct();
    datasetInfo.hardware_config.audio.device = 'Android Phone';
    datasetInfo.hardware_config.audio.sample_rate_hz = 44100;
    datasetInfo.hardware_config.audio.ultrasonic_freq_hz = 20000;
    datasetInfo.hardware_config.audio.format = 'WAV';
    datasetInfo.hardware_config.audio.bit_depth = 16;
    
    % 保存为JSON
    jsonStr = jsonencode(datasetInfo, 'PrettyPrint', true);
    fid = fopen(outputPath, 'w', 'n', 'UTF-8');
    fprintf(fid, '%s', jsonStr);
    fclose(fid);
    
    fprintf('\n数据集信息已保存: %s\n', outputPath);
    fprintf('  总被试数: %d\n', numSubjects);
    fprintf('  总样本数: %d\n', totalSamples);
    fprintf('  场景数: %d\n', length(sceneCount));
end
