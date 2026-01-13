function createScenesInfo(outputPath, csvFilePath)
% createScenesInfo - 从CSV文件生成场景信息JSON文件
%
% 输入:
%   outputPath: 输出JSON文件路径（可选，默认为当前目录/scenes_info.json）
%   csvFilePath: 场景CSV文件路径（可选，默认 'radar/scenes_file.csv'）
%
% 输出:
%   生成scenes_info.json文件，包含CSV中定义的所有场景信息
%
% 设计原则:
%   - 直接从CSV文件读取场景信息，不硬编码
%   - 支持CSV文件的动态修改（增删改场景）
%   - 保证数据采集的可扩展性
%
% 示例:
%   createScenesInfo('D:\multimodal_dataset\scenes_info.json');

    if nargin < 1 || isempty(outputPath)
        outputPath = 'scenes_info.json';
    end
    
    if nargin < 2 || isempty(csvFilePath)
        csvFilePath = 'radar/scenes_file.csv';
    end
    
    fprintf('========== 生成场景信息文件 ==========\n');
    fprintf('读取CSV: %s\n', csvFilePath);
    
    % 从CSV加载场景映射
    scenesMap = loadScenesFromCSV(csvFilePath);
    
    % 构建输出结构
    scenesInfo = struct();
    scenesInfo.version = '1.0';
    scenesInfo.source_csv = csvFilePath;
    scenesInfo.generated_time = datestr(now, 'yyyy-mm-dd HH:MM:SS');
    scenesInfo.total_scenes = scenesMap.Count;
    
    % 场景编码说明（根据实际CSV内容）
    scenesInfo.encoding = struct();
    scenesInfo.encoding.description = '场景代码格式: A{用户状态}-B{用户动作}-C{窥视者距离}-D{窥视者角度}-E{窥视者行为}';
    
    scenesInfo.encoding.A_user_status = struct();
    scenesInfo.encoding.A_user_status.A0 = '环境基线（无用户）';
    scenesInfo.encoding.A_user_status.A1 = '合法用户存在';
    
    scenesInfo.encoding.B_user_action = struct();
    scenesInfo.encoding.B_user_action.B0 = '无动作（基线）';
    scenesInfo.encoding.B_user_action.B1 = '静坐';
    scenesInfo.encoding.B_user_action.B2 = '打字';
    scenesInfo.encoding.B_user_action.B3 = '轻微摇晃';
    
    scenesInfo.encoding.C_peeper_distance = struct();
    scenesInfo.encoding.C_peeper_distance.C0 = '无窥视者';
    scenesInfo.encoding.C_peeper_distance.C1 = '1.0米';
    scenesInfo.encoding.C_peeper_distance.C2 = '2.0米';
    
    scenesInfo.encoding.D_peeper_angle = struct();
    scenesInfo.encoding.D_peeper_angle.D0 = '无';
    scenesInfo.encoding.D_peeper_angle.D1 = '0度';
    scenesInfo.encoding.D_peeper_angle.D2 = '60度';
    
    scenesInfo.encoding.E_peeper_behavior = struct();
    scenesInfo.encoding.E_peeper_behavior.E0 = '无窥视者';
    scenesInfo.encoding.E_peeper_behavior.E1 = '静止站立';
    scenesInfo.encoding.E_peeper_behavior.E2 = '慢速路过';
    scenesInfo.encoding.E_peeper_behavior.E3 = '靠近并驻足';
    scenesInfo.encoding.E_peeper_behavior.E4 = '正常路过';
    
    % 转换为场景列表
    scenesList = [];
    sceneCodes = keys(scenesMap);
    
    for i = 1:length(sceneCodes)
        sceneCode = sceneCodes{i};
        sceneData = scenesMap(sceneCode);
        
        scene = struct();
        scene.idx = sceneData.idx;
        scene.code = sceneData.code;
        scene.intro = sceneData.intro;  % 直接使用CSV中的中文描述
        
        scenesList = [scenesList; scene];
    end
    
    % 按idx排序
    [~, sortIdx] = sort([scenesList.idx]);
    scenesList = scenesList(sortIdx);
    
    scenesInfo.scenes = scenesList;
    
    % 保存为JSON
    jsonStr = jsonencode(scenesInfo, 'PrettyPrint', true);
    fid = fopen(outputPath, 'w', 'n', 'UTF-8');
    fprintf(fid, '%s', jsonStr);
    fclose(fid);
    
    fprintf('场景信息已保存: %s\n', outputPath);
    fprintf('  共 %d 个场景\n', length(scenesList));
end
