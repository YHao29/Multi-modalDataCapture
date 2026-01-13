% test_hierarchical_scenes.m - 测试三层场景配置加载
%
% 用途：验证三层场景配置文件是否正确加载

clear;
clc;

fprintf('========== 测试三层场景配置加载 ==========\n\n');

try
    % 调用加载函数
    [locations, subLocations, actionScenes] = loadHierarchicalScenes();
    
    % 显示大场景
    fprintf('大场景 (%d个):\n', length(locations));
    for i = 1:length(locations)
        fprintf('  [%s] %s - %s\n', locations(i).location_id, ...
            locations(i).location_name, locations(i).description);
    end
    
    % 显示子场景
    fprintf('\n子场景 (%d个):\n', length(subLocations));
    for i = 1:length(subLocations)
        fprintf('  [%s] %s (属于%s) - %s\n', subLocations(i).sub_location_id, ...
            subLocations(i).sub_location_name, subLocations(i).location_id, ...
            subLocations(i).description);
    end
    
    % 显示动作组合场景示例
    fprintf('\n动作组合场景 (%d个):\n', length(actionScenes));
    fprintf('  前3个示例:\n');
    for i = 1:min(3, length(actionScenes))
        fprintf('  [%d] %s (%s)\n', actionScenes(i).idx, ...
            actionScenes(i).intro, actionScenes(i).code);
    end
    
    % 验证数据一致性
    fprintf('\n========== 数据一致性验证 ==========\n');
    
    % 检查子场景的location_id是否都在大场景中存在
    location_ids = {locations.location_id};
    valid_count = 0;
    invalid_count = 0;
    
    for i = 1:length(subLocations)
        if ismember(subLocations(i).location_id, location_ids)
            valid_count = valid_count + 1;
        else
            invalid_count = invalid_count + 1;
            warning('子场景 %s 关联的大场景 %s 不存在', ...
                subLocations(i).sub_location_id, subLocations(i).location_id);
        end
    end
    
    fprintf('  有效子场景: %d/%d\n', valid_count, length(subLocations));
    if invalid_count == 0
        fprintf('  ✓ 所有子场景配置正确\n');
    else
        fprintf('  ✗ 发现 %d 个配置错误的子场景\n', invalid_count);
    end
    
    fprintf('\n========== 测试场景ID生成 ==========\n');
    
    % 模拟生成场景ID
    sample_id = 1;
    test_location = locations(1);
    
    % 找到属于该大场景的子场景
    test_subLocation = [];
    for i = 1:length(subLocations)
        if strcmp(subLocations(i).location_id, test_location.location_id)
            test_subLocation = subLocations(i);
            break;
        end
    end
    
    if ~isempty(test_subLocation)
        test_action = actionScenes(1);
        sceneId = sprintf('sample_%03d_%s_%s_%s', sample_id, ...
            test_location.location_id, test_subLocation.sub_location_id, test_action.code);
        fprintf('  示例场景ID: %s\n', sceneId);
        fprintf('  解析:\n');
        fprintf('    - 样本ID: %d\n', sample_id);
        fprintf('    - 大场景: %s (%s)\n', test_location.location_id, test_location.location_name);
        fprintf('    - 子场景: %s (%s)\n', test_subLocation.sub_location_id, test_subLocation.sub_location_name);
        fprintf('    - 动作组合: %s\n', test_action.code);
    end
    
    fprintf('\n========== 测试完成 ==========\n');
    fprintf('✓ 三层场景配置加载成功\n');
    
catch ME
    fprintf('\n✗ 测试失败: %s\n', ME.message);
    fprintf('错误位置: %s (行 %d)\n', ME.stack(1).name, ME.stack(1).line);
end
