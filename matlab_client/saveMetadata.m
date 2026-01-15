function saveMetadata(metadata, sceneInfo, staffCombo, subjectId, savePath, repeatIndex, locationInfo, subLocationInfo, sampleId)
% saveMetadata - 保存多模态采集元数据到JSON文件（支持三层场景体系）
%
% 输入:
%   metadata: syncCapture 返回的元数据结构体
%   sceneInfo: 动作组合场景信息结构体，包含字段：
%       - idx: 场景索引
%       - intro: 场景描述
%       - code: 场景代码
%   staffCombo: 人员组合字符串（例如 'yh-ssk'）
%   subjectId: 数字ID（例如 1）
%   savePath: 保存路径（被试目录）
%   repeatIndex: 重复次数索引（1, 2, 3...）
%   locationInfo: 大场景信息结构体，包含字段：
%       - location_id: 大场景ID
%       - location_name: 大场景名称
%       - description: 大场景描述
%   subLocationInfo: 子场景信息结构体，包含字段：
%       - sub_location_id: 子场景ID
%       - sub_location_name: 子场景名称
%   sampleId: 样本ID（全局编号）
%
% 输出:
%   无（直接保存JSON文件）
%
% 示例:
%   saveMetadata(metadata, scene, 'yh-ssk', 1, 'D:\\data\\subjects\\subject_001\\', 1, locationInfo, subLocationInfo, 1);

    try
        % 构建文件名（使用新的命名格式）
        metaFilename = sprintf('sample_%03d_%s_%s_%s_meta.json', sampleId, ...
            locationInfo.location_id, subLocationInfo.sub_location_id, sceneInfo.code);
        metaFilepath = fullfile(savePath, metaFilename);
        
        % 构建完整的元数据结构
        fullMetadata = struct();
        
        % 人员组合和ID
        fullMetadata.staff_combination = staffCombo;
        fullMetadata.subject_id = subjectId;
        
        % 三层场景信息
        fullMetadata.location = struct();
        fullMetadata.location.location_id = locationInfo.location_id;
        fullMetadata.location.location_name = locationInfo.location_name;
        fullMetadata.location.description = locationInfo.description;
        
        fullMetadata.sub_location = struct();
        fullMetadata.sub_location.sub_location_id = subLocationInfo.sub_location_id;
        fullMetadata.sub_location.sub_location_name = subLocationInfo.sub_location_name;
        fullMetadata.sub_location.description = subLocationInfo.description;
        
        fullMetadata.action_scene = struct();
        fullMetadata.action_scene.idx = sceneInfo.idx;
        fullMetadata.action_scene.intro = sceneInfo.intro;
        fullMetadata.action_scene.code = sceneInfo.code;
        
        % 采集配置
        fullMetadata.capture_config = struct();
        fullMetadata.capture_config.sample_id = sampleId;
        fullMetadata.capture_config.repeat_index = repeatIndex;
        fullMetadata.capture_config.radar_delay_ms = metadata.radar_delay_ms;
        fullMetadata.capture_config.phone_delay_ms = metadata.phone_delay_ms;
        
        % 添加offset参数（如果存在）
        if isfield(metadata, 'audio_start_offset_ms')
            fullMetadata.capture_config.audio_start_offset_ms = metadata.audio_start_offset_ms;
        end
        if isfield(metadata, 'radar_start_offset_ms')
            fullMetadata.capture_config.radar_start_offset_ms = metadata.radar_start_offset_ms;
        end
        
        % 音频参数（Android端超声波模式）
        fullMetadata.audio_params = struct();
        fullMetadata.audio_params.sample_rate = 44100;  % Hz
        fullMetadata.audio_params.ultrasonic_freq = 20000;  % Hz
        fullMetadata.audio_params.format = 'wav';
        fullMetadata.audio_params.mode = 'ultrasonic';
        
        % 雷达参数
        fullMetadata.radar_params = struct();
        fullMetadata.radar_params.format = 'bin';
        fullMetadata.radar_params.data_type = 'mmwave_adc';
        
        % 同步质量
        fullMetadata.sync_quality = struct();
        fullMetadata.sync_quality.sntp_offset_ms = metadata.sntp_offset_ms;
        fullMetadata.sync_quality.rtt_ms = metadata.rtt_ms;
        
        % 新版字段（带offset的多个trigger时间）
        if isfield(metadata, 'base_trigger_timestamp_utc')
            fullMetadata.sync_quality.base_trigger_timestamp_utc = metadata.base_trigger_timestamp_utc;
        end
        if isfield(metadata, 'audio_trigger_timestamp_utc')
            fullMetadata.sync_quality.audio_trigger_timestamp_utc = metadata.audio_trigger_timestamp_utc;
        end
        if isfield(metadata, 'radar_trigger_timestamp_utc')
            fullMetadata.sync_quality.radar_trigger_timestamp_utc = metadata.radar_trigger_timestamp_utc;
        end
        
        % 时间信息（可读格式）- 使用基准触发时间
        if isfield(metadata, 'base_trigger_timestamp_utc')
            trigger_timestamp = metadata.base_trigger_timestamp_utc;
        else
            % 向后兼容旧版本（如果没有新字段）
            trigger_timestamp = 0;
        end
        
        if trigger_timestamp > 0
            trigger_datetime = datetime(trigger_timestamp/1000, ...
                'ConvertFrom', 'posixtime', 'TimeZone', 'UTC');
            fullMetadata.sync_quality.trigger_time_readable = char(trigger_datetime);
        else
            fullMetadata.sync_quality.trigger_time_readable = 'N/A';
        end
        
        % 文件映射（使用相对路径）
        fullMetadata.file_mapping = struct();
        fullMetadata.file_mapping.radar_file = fullfile('radar', metadata.radar_file);
        fullMetadata.file_mapping.audio_files = cellfun(@(x) fullfile('audio', x), ...
            metadata.audio_files, 'UniformOutput', false);
        
        % 采集状态
        fullMetadata.capture_status = struct();
        fullMetadata.capture_status.success = strcmp(metadata.success_status, 'success');
        fullMetadata.capture_status.status_message = metadata.success_status;
        fullMetadata.capture_status.timestamp = char(datetime('now'));
        
        % 转换为JSON并保存
        jsonStr = jsonencode(fullMetadata, 'PrettyPrint', true);
        
        % 写入文件
        fid = fopen(metaFilepath, 'w', 'n', 'UTF-8');
        if fid == -1
            error('无法创建元数据文件: %s', metaFilepath);
        end
        
        fprintf(fid, '%s', jsonStr);
        fclose(fid);
        
        fprintf('  [元数据] 已保存: %s\n', metaFilename);
        
    catch ME
        warning('保存元数据失败: %s', ME.message);
    end
end
