function scenesMap = loadScenesFromCSV(csvFilePath)
% loadScenesFromCSV - 从CSV文件加载场景映射表
%
% 输入:
%   csvFilePath: CSV文件路径 (可选，默认为 'radar/scenes_file.csv')
%
% 输出:
%   scenesMap: containers.Map，key为场景代码(如'A1-B1-C1-D1-E1')，
%              value为结构体，包含idx和intro字段
%
% 设计原则:
%   - 直接从CSV文件读取，不硬编码场景信息
%   - 支持CSV文件的动态修改（增删改场景）
%   - 保证数据采集的可扩展性
%
% 示例:
%   scenesMap = loadScenesFromCSV('radar/scenes_file.csv');
%   sceneInfo = scenesMap('A1-B1-C1-D1-E1');
%   disp(sceneInfo.intro);  % 输出: 合法用户静坐，窥视者1.0米-0°-静止站立

    if nargin < 1 || isempty(csvFilePath)
        % 默认路径（相对于matlab_client目录）
        csvFilePath = 'radar/scenes_file.csv';
    end
    
    % 检查文件是否存在
    if ~exist(csvFilePath, 'file')
        error('场景配置文件不存在: %s', csvFilePath);
    end
    
    % 读取CSV文件（使用GBK编码，适配中文Windows）
    try
        % 尝试使用detectImportOptions（R2019b+）
        opts = detectImportOptions(csvFilePath, 'Encoding', 'GB2312');
        opts.VariableNamingRule = 'preserve';
        scenesTable = readtable(csvFilePath, opts);
    catch
        % 回退方案：直接读取
        fid = fopen(csvFilePath, 'r', 'n', 'GB2312');
        if fid == -1
            % 尝试GBK
            fid = fopen(csvFilePath, 'r', 'n', 'GBK');
        end
        if fid == -1
            error('无法打开CSV文件: %s', csvFilePath);
        end
        
        % 跳过表头
        headerLine = fgetl(fid);
        
        % 读取数据
        scenesTable = table();
        idx = [];
        intro = {};
        code = {};
        
        while ~feof(fid)
            line = fgetl(fid);
            if ischar(line) && ~isempty(line)
                parts = strsplit(line, ',');
                if length(parts) >= 3
                    idx(end+1) = str2double(parts{1});
                    intro{end+1} = parts{2};
                    code{end+1} = parts{3};
                end
            end
        end
        fclose(fid);
        
        scenesTable = table(idx', intro', code', ...
            'VariableNames', {'idx', 'intro', 'code'});
    end
    
    % 创建场景映射表
    scenesMap = containers.Map('KeyType', 'char', 'ValueType', 'any');
    
    for i = 1:height(scenesTable)
        sceneCode = char(scenesTable.code{i});
        sceneInfo = struct();
        sceneInfo.idx = scenesTable.idx(i);
        sceneInfo.intro = char(scenesTable.intro{i});
        sceneInfo.code = sceneCode;
        
        scenesMap(sceneCode) = sceneInfo;
    end
    
    fprintf('已从CSV加载 %d 个场景配置\n', scenesMap.Count);
end
