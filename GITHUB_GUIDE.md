# 多模态数据采集系统 - GitHub 推送指南

## 推送前准备

已创建 `.gitignore` 文件，排除了以下内容：
- 数据文件（.bin, .wav 等大文件）
- 构建产物（build/, .gradle/ 等）
- IDE 配置文件
- 日志和临时文件
- 系统文件

## 推送步骤

### 1. 在 GitHub 上创建新仓库

1. 登录 GitHub (https://github.com)
2. 点击右上角 "+" → "New repository"
3. 填写仓库信息：
   - Repository name: `multimodal-data-capture` 或自定义名称
   - Description: `多模态数据采集系统 - 毫米波雷达和超声波同步采集`
   - 选择 Public 或 Private
   - **不要**勾选 "Initialize this repository with a README"（项目已有 README）
4. 点击 "Create repository"

### 2. 初始化本地 Git 仓库并推送

打开 PowerShell，执行以下命令：

```powershell
# 进入项目目录
cd E:\ScreenDataCapture\Multimodal_data_capture

# 初始化 Git 仓库
git init

# 添加所有文件
git add .

# 查看将要提交的文件（可选，确认没有大文件）
git status

# 首次提交
git commit -m "Initial commit: 多模态数据采集系统完整实现

- AudioCenterServer: Java 服务端（Spring Boot + Netty + SNTP）
- MATLAB 客户端工具：AudioClient, 时间同步, 同步采集
- 批量采集主控程序：main_multimodal_data_capture.m
- 雷达启动延迟测量工具
- 完整文档和测试指南
"

# 添加远程仓库（替换为你的 GitHub 仓库地址）
# 格式：git remote add origin https://github.com/你的用户名/仓库名.git
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git

# 推送到 GitHub
git push -u origin master
```

### 3. 如果推送失败

#### 问题：推送被拒绝（rejected）

```powershell
# 强制推送（如果确定本地版本是最新的）
git push -u origin master --force
```

#### 问题：需要输入用户名和密码

GitHub 已不支持密码认证，需要使用 Personal Access Token：

1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token
3. 勾选 `repo` 权限
4. 复制生成的 token
5. 推送时输入：
   - Username: 你的 GitHub 用户名
   - Password: 粘贴刚才复制的 token

#### 问题：大文件导致推送失败

如果有大文件未被 .gitignore 排除：

```powershell
# 查看文件大小
git ls-files -s

# 移除已暂存的大文件
git rm --cached path/to/large/file

# 更新 .gitignore
# 然后重新提交
git add .gitignore
git commit -m "Update .gitignore to exclude large files"
```

### 4. 后续更新推送

```powershell
# 添加修改的文件
git add .

# 提交更改
git commit -m "描述你的修改内容"

# 推送到 GitHub
git push
```

## 建议的分支策略

### 保护 master 分支

```powershell
# 创建开发分支
git checkout -b develop

# 在开发分支上工作
# ... 修改代码 ...

# 提交到开发分支
git add .
git commit -m "功能描述"
git push -u origin develop

# 合并到 master（在 GitHub 上创建 Pull Request，或本地合并）
git checkout master
git merge develop
git push
```

## 注意事项

### ⚠️ 确认排除的文件

推送前请确认以下文件**不会**被上传（已在 .gitignore 中）：
- ✓ 数据文件（.bin, .wav）
- ✓ 构建产物（build/, .gradle/）
- ✓ 日志文件（logs/, *.log）
- ✓ IDE 配置（.idea/, .vscode/）

### 📦 可选择性上传的文件

如果需要上传示例数据或配置：

```powershell
# 强制添加被 .gitignore 排除的文件
git add -f path/to/file

# 或者在 .gitignore 中添加例外
# 在 .gitignore 中添加：
# !example_data.bin  # 感叹号表示不排除
```

### 🔒 敏感信息检查

确保以下文件不包含敏感信息：
- `config/system_config.json` - 检查是否有密码、密钥
- `AudioCenterServer/src/main/resources/application.properties` - 检查数据库密码等

## 示例：完整推送流程

```powershell
# 1. 进入项目目录
cd E:\ScreenDataCapture\Multimodal_data_capture

# 2. 初始化 Git
git init
git add .
git commit -m "Initial commit: 多模态数据采集系统"

# 3. 连接到 GitHub（替换为你的仓库地址）
git remote add origin https://github.com/username/multimodal-data-capture.git

# 4. 推送
git push -u origin master

# 完成！访问 https://github.com/username/multimodal-data-capture 查看
```

## 常见问题

**Q: 如何修改远程仓库地址？**
```powershell
git remote set-url origin https://github.com/new-username/new-repo.git
```

**Q: 如何查看当前远程仓库？**
```powershell
git remote -v
```

**Q: 如何撤销上次提交？**
```powershell
git reset --soft HEAD~1  # 保留修改
# 或
git reset --hard HEAD~1  # 丢弃修改
```

**Q: 如何添加标签（版本）？**
```powershell
git tag -a v1.0 -m "Version 1.0: 基础功能完成"
git push origin v1.0
```
