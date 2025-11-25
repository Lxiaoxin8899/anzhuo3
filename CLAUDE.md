# SmartDosing - Android 智能配料应用

## 项目概述

SmartDosing 是一个基于 Kotlin + Jetpack Compose 的 Android 应用，用于管理配方和配料操作。应用支持 SQLite 数据库持久化、Web 服务接口、TTS 语音播报等功能。

## 技术栈

- **语言**: Kotlin
- **UI框架**: Jetpack Compose + Material 3
- **数据库**: Room 2.6.1 (SQLite)
- **Web服务**: Ktor 2.3.7
- **最低API**: 26 (Android 8.0)
- **目标API**: 36

## 项目结构

```
app/src/main/java/com/example/smartdosing/
├── data/                    # 数据层
│   ├── Recipe.kt           # 配方数据模型
│   ├── RecipeRepository.kt # 内存配方仓库（旧）
│   ├── DatabaseRecipeRepository.kt  # 数据库配方仓库（推荐）
│   ├── DatabaseRecipeImportManager.kt # 导入管理器
│   ├── DosingRecord.kt     # 配料记录
│   ├── ConfigurationTask.kt # 配置任务
│   └── repository/         # 其他仓库
├── database/               # Room 数据库层
│   ├── SmartDosingDatabase.kt # 数据库定义
│   ├── DataMapper.kt       # 数据映射
│   ├── dao/                # 数据访问对象
│   │   ├── RecipeDao.kt
│   │   ├── MaterialDao.kt
│   │   ├── TemplateDao.kt
│   │   ├── ImportLogDao.kt
│   │   └── DosingRecordDao.kt
│   ├── entities/           # 数据库实体
│   └── converters/         # 类型转换器
├── navigation/             # 导航
│   ├── SmartDosingDestinations.kt # 路由定义
│   └── SmartDosingNavHost.kt      # 导航图
├── ui/                     # UI层
│   ├── SmartDosingApp.kt   # 主App组件
│   ├── screens/            # 各功能页面
│   │   ├── home/          # 首页
│   │   ├── recipes/       # 配方管理
│   │   ├── dosing/        # 配料操作
│   │   ├── records/       # 记录查看
│   │   ├── tasks/         # 任务中心
│   │   └── settings/      # 设置
│   ├── components/         # 共享组件
│   └── theme/             # 主题定义
├── web/                    # Web 服务
│   ├── WebServerManager.kt
│   ├── WebService.kt
│   └── HtmlGenerator.kt
├── tts/                    # TTS 语音
│   └── XiaomiTTSManager.kt
└── MainActivity.kt         # 主活动
```

## 构建命令

```bash
# 编译项目
./gradlew build

# 仅编译 Kotlin（快速检查）
./gradlew compileDebugKotlin

# 清理构建
./gradlew clean

# 安装到设备
./gradlew installDebug
```

## 重要约定

### 数据库使用
- 新功能使用 `DatabaseRecipeRepository`，不要使用旧的 `RecipeRepository`
- 所有数据库操作需要在协程中调用（suspend 函数）
- 数据库文件位置: `/data/data/com.example.smartdosing/databases/smartdosing.db`

### 代码风格
- 使用 Kotlin 协程处理异步操作
- UI 使用 Jetpack Compose 声明式写法
- 遵循 MVVM 架构模式
- 中文注释，英文代码命名

### 导航
- 路由定义在 `SmartDosingDestinations.kt`
- 导航图在 `SmartDosingNavHost.kt`
- 新增页面需要在两个文件中添加对应定义

## 当前状态

- 数据库层: 100% 完成
- Repository层: 100% 完成
- 导入模块: 100% 完成
- UI层适配: 进行中
- Web服务: 基本可用

## 常见问题

1. **编译错误找不到生成代码**: Clean Project 后重新 Build
2. **数据库崩溃**: 检查是否在主线程调用 suspend 函数
3. **数据未保存**: 确认使用 `DatabaseRecipeRepository`
