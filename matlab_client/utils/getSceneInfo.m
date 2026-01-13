function sceneInfo = getSceneInfo(sceneCode, csvFilePath)
% getSceneInfo - 根据场景代码获取场景信息
%
% 输入:
%   sceneCode: 场景代码 (如 'A1-B1-C1-D1-E1')
%   csvFilePath: CSV文件路径 (可选)
%
% 输出:
%   sceneInfo: 结构体，包含:
%       - code: 场景代码
%       - intro: 场景中文描述（直接从CSV读取）
%       - idx: 场景索引号
%
% 设计原则:
%   - 严格按照CSV文件中的场景描述
%   - 不自行生成或翻译场景名称
%   - 支持CSV文件的动态修改
%
% 示例:
%   info = getSceneInfo('A1-B1-C1-D1-E1');
%   disp(info.intro);  % 合法用户静坐，窥视者1.0米-0°-静止站立

    persistent cachedScenesMap;
    persistent cachedCsvPath;
    
    if nargin < 2 || isempty(csvFilePath)
        csvFilePath = 'radar/scenes_file.csv';
    end
    
    % 缓存机制：避免重复读取CSV
    if isempty(cachedScenesMap) || ~strcmp(cachedCsvPath, csvFilePath)
        cachedScenesMap = loadScenesFromCSV(csvFilePath);
        cachedCsvPath = csvFilePath;
    end
    
    % 查找场景信息
    if isKey(cachedScenesMap, sceneCode)
        sceneInfo = cachedScenesMap(sceneCode);
    else
        % 场景代码不在CSV中，返回基本信息
        warning('场景代码 %s 未在CSV文件中定义', sceneCode);
        sceneInfo = struct();
        sceneInfo.code = sceneCode;
        sceneInfo.intro = sprintf('未知场景(%s)', sceneCode);
        sceneInfo.idx = -1;
    end
end
