function [locations, subLocations, actionScenes] = loadHierarchicalScenes(version)
% loadHierarchicalScenes - 加载三层场景配置
%
% 输出:
%   locations: 大场景数组，包含字段：
%       - location_id: 场景ID (如 'L01')
%       - location_name: 场景名称 (如 '咖啡厅')
%       - description: 场景描述
%   subLocations: 子场景数组，包含字段：
%       - sub_location_id: 子场景ID (如 'SL01')
%       - location_id: 所属大场景ID
%       - sub_location_name: 子场景名称 (如 '角落')
%       - description: 子场景描述
%   actionScenes: 动作组合场景数组，包含字段：
%       - idx: 场景索引
%       - intro: 场景描述
%       - code: 场景代码 (如 'A1-B1-C1-D1-E1')
%
% 示例:
%   [locs, subLocs, actions] = loadHierarchicalScenes('v2');

    % 获取当前脚本所在目录（假设在 matlab_client/utils/）
    script_dir = fileparts(mfilename('fullpath'));
    radar_dir = fullfile(script_dir, '..', 'radar');
    
    % 定义配置文件路径
    locations_file = fullfile(radar_dir, ['locations', version, '.csv']);
    sub_locations_file = fullfile(radar_dir, ['sub_locations', version, '.csv']);
    scenes_file = fullfile(radar_dir, ['scenes_file', version, '.csv']);
    
    %% 加载大场景配置
    if ~exist(locations_file, 'file')
        error('大场景配置文件不存在: %s', locations_file);
    end
    
    try
        % 尝试UTF-8编码
        locations_table = readtable(locations_file, 'Encoding', 'UTF-8', 'FileType', 'text');
        
        % 检测乱码
        if height(locations_table) > 0
            sample_text = char(locations_table.location_name(1));
            if contains(sample_text, char(65533))
                locations_table = readtable(locations_file, 'Encoding', 'GBK', 'FileType', 'text');
            end
        end
        
        % 转换为结构体数组
        locations = struct([]);
        for i = 1:height(locations_table)
            locations(i).location_id = char(locations_table.location_id(i));
            locations(i).location_name = char(locations_table.location_name(i));
            locations(i).description = char(locations_table.description(i));
        end
    catch ME
        error('加载大场景配置失败: %s', ME.message);
    end
    
    %% 加载子场景配置
    if ~exist(sub_locations_file, 'file')
        error('子场景配置文件不存在: %s', sub_locations_file);
    end
    
    try
        % 尝试UTF-8编码
        sub_locations_table = readtable(sub_locations_file, 'Encoding', 'UTF-8', 'FileType', 'text');
        
        % 检测乱码
        if height(sub_locations_table) > 0
            sample_text = char(sub_locations_table.sub_location_name(1));
            if contains(sample_text, char(65533))
                sub_locations_table = readtable(sub_locations_file, 'Encoding', 'GBK', 'FileType', 'text');
            end
        end
        
        % 转换为结构体数组
        subLocations = struct([]);
        for i = 1:height(sub_locations_table)
            subLocations(i).sub_location_id = char(sub_locations_table.sub_location_id(i));
            subLocations(i).location_id = char(sub_locations_table.location_id(i));
            subLocations(i).sub_location_name = char(sub_locations_table.sub_location_name(i));
            subLocations(i).description = char(sub_locations_table.description(i));
        end
    catch ME
        error('加载子场景配置失败: %s', ME.message);
    end
    
    %% 加载动作组合场景配置（保持原有逻辑）
    if ~exist(scenes_file, 'file')
        error('动作组合场景配置文件不存在: %s', scenes_file);
    end
    
    try
        % 尝试UTF-8编码
        scenes_table = readtable(scenes_file, 'Encoding', 'UTF-8', 'FileType', 'text');
        
        % 检测乱码
        if height(scenes_table) > 0
            sample_text = char(scenes_table.intro(1));
            if contains(sample_text, char(65533))
                scenes_table = readtable(scenes_file, 'Encoding', 'GBK', 'FileType', 'text');
            end
        end
        
        % 转换为结构体数组
        actionScenes = struct([]);
        for i = 1:height(scenes_table)
            actionScenes(i).idx = scenes_table.idx(i);
            actionScenes(i).intro = char(scenes_table.intro(i));
            actionScenes(i).code = char(scenes_table.code(i));
        end
    catch ME
        error('加载动作组合场景配置失败: %s', ME.message);
    end
    
    %% 验证数据一致性
    % 检查子场景的location_id是否都在大场景中存在
    location_ids = {locations.location_id};
    for i = 1:length(subLocations)
        if ~ismember(subLocations(i).location_id, location_ids)
            warning('子场景 %s 关联的大场景 %s 不存在', ...
                subLocations(i).sub_location_id, subLocations(i).location_id);
        end
    end
    
    fprintf('场景配置加载完成:\n');
    fprintf('  - 大场景: %d 个\n', length(locations));
    fprintf('  - 子场景: %d 个\n', length(subLocations));
    fprintf('  - 动作组合: %d 个\n', length(actionScenes));
end
