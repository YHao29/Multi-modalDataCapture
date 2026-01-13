function saveMetadata(metadata, sceneInfo, staffCombo, subjectId, savePath, repeatIndex)
% saveMetadata - 保存多模态采集元数据到JSON文件
%
% 输入:
%   metadata: syncCapture 返回的元数据结构体
%   sceneInfo: 场景信息结构体，包含字段：
%       - idx: 场景索引
%       - intro: 场景描述
%       - code: 场景代码
%   staffCombo: 人员组合字符串（例如 'yh-ssk'）
%   subjectId: 数字ID（例如 1）
%   savePath: 保存路径
%   repeatIndex: 重复次数索引（1, 2, 3...）
%
% 输出:
%   无（直接保存JSON文件）
%
% 示例:
%   sceneInfo = struct('idx', 5, 'intro', '上方有人在动慢走速度1.0m/s-0-静止站立', 'code', 'A1-B1-C1-D1-E1');
%   saveMetadata(metadata, sceneInfo, 'yh-ssk', 1, 'D:\data\', 1);

    try
        % 构建文件名（使用数字ID作为前缀）
        metaFilename = sprintf('%d-%s-%02d_meta.json', subjectId, sceneInfo.code, repeatIndex);
        metaFilepath = fullfile(savePath, metaFilename);
        
        % 构建完整的元数据结构
        fullMetadata = struct();
        
        % 人员组合和ID
        fullMetadata.staff_combination = staffCombo;
        fullMetadata.subject_id = subjectId;
        
        % 场景信息
        fullMetadata.scene_info = struct();
        fullMetadata.scene_info.idx = sceneInfo.idx;
        fullMetadata.scene_info.intro = sceneInfo.intro;
        fullMetadata.scene_info.code = sceneInfo.code;
        
        % 采集配置
        fullMetadata.capture_config = struct();
        fullMetadata.capture_config.repeat_index = repeatIndex;
        fullMetadata.capture_config.radar_delay_ms = metadata.radar_delay_ms;
        fullMetadata.capture_config.phone_delay_ms = metadata.phone_delay_ms;
        
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
        fullMetadata.sync_quality.trigger_timestamp_utc = metadata.trigger_timestamp_utc;
        
        % 时间信息（可读格式）
        trigger_datetime = datetime(metadata.trigger_timestamp_utc/1000, ...
            'ConvertFrom', 'posixtime', 'TimeZone', 'UTC');
        fullMetadata.sync_quality.trigger_time_readable = char(trigger_datetime);
        
        % 文件映射
        fullMetadata.file_mapping = struct();
        fullMetadata.file_mapping.radar_file = metadata.radar_file;
        fullMetadata.file_mapping.audio_files = metadata.audio_files;
        
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
