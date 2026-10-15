# SmartDosing - Android 智能配料应用

## 项目概述

SmartDosing 是一个基于 Kotlin + Jetpack Compose 的 Android 应用，用于实验室配方管理与配料操作。主要能力：Room 数据库持久化、局域网任务接收（Ktor 服务 + 设备配对 + API Key 鉴权）、蓝牙电子天平称重、配料提示音、重量预警、实验档案与报告导出、本地数据备份恢复。

## 技术栈

- **语言**: Kotlin
- **UI框架**: Jetpack Compose + Material 3
- **数据库**: Room 2.6.1 (SQLite)
- **局域网服务**: Ktor 2.3.7（仅 JSON API，网页管理端已移除）
- **最低API**: 26 (Android 8.0)
- **目标API**: 36

## 项目结构

```
app/src/main/java/com/example/smartdosing/
├── data/                    # 数据层
│   ├── Recipe.kt           # 配方数据模型
│   ├── DatabaseRecipeRepository.kt  # 配方仓库（Room）
│   ├── DatabaseRecipeImportManager.kt # 配方导入（CSV/Excel）
│   ├── ConfigurationTask.kt # 配置任务
│   ├── ConfigurationRecord.kt # 实验记录
│   ├── backup/             # 数据备份与恢复
│   ├── export/             # 实验报告导出
│   ├── device/             # 设备身份与配对
│   ├── settings/           # 偏好设置（DataStore）
│   ├── transfer/           # 局域网任务接收、结果回传、后端 WebSocket
│   └── repository/         # 任务/记录仓库与远端 API
├── database/               # Room 数据库层
│   ├── SmartDosingDatabase.kt # 数据库定义与迁移
│   ├── DataMapper.kt       # 数据映射
│   ├── dao/                # 数据访问对象
│   ├── entities/           # 数据库实体
│   └── converters/         # 类型转换器
├── bluetooth/              # 蓝牙天平（CH9140）与演示模式
├── dosing/                 # 重量预警判定
├── audio/                  # 配料提示音
├── navigation/             # 导航
│   ├── SmartDosingDestinations.kt # 路由定义
│   └── SmartDosingNavHost.kt      # 导航图
├── ui/                     # UI层
│   ├── screens/            # 各功能页面
│   │   ├── home/          # 首页看板
│   │   ├── lab/           # 实验中心
│   │   ├── recipes/       # 配方管理
│   │   ├── dosing/        # 配料操作
│   │   ├── materials/     # 物料清单
│   │   ├── records/       # 实验档案
│   │   ├── tasks/         # 任务中心
│   │   ├── device/        # 设备信息
│   │   └── settings/      # 设置
│   ├── components/         # 共享组件
│   └── theme/             # 主题定义
├── web/                    # 局域网服务
│   ├── WebServerManager.kt # Ktor 路由与鉴权
│   ├── WebService.kt       # 服务生命周期
│   ├── LanTransferForegroundService.kt # 前台保活服务
│   └── transfer/           # 协议适配与对外 DTO
└── MainActivity.kt         # 主活动
```

## 构建命令

```bash
# 编译项目
./gradlew build

# 仅编译 Kotlin（快速检查）
./gradlew compileDebugKotlin

# 单元测试
./gradlew testDebugUnitTest

# 清理构建
./gradlew clean

# 安装到设备
./gradlew installDebug
```

构建需要完整 JDK（含 `jlink`），`JAVA_HOME` 不能指向 PyCharm 等 IDE 自带的精简运行时。

## 重要约定

### 数据库使用
- 配方数据统一通过 `DatabaseRecipeRepository` 访问
- 所有数据库操作需要在协程中调用（suspend 函数）
- 修改实体必须提升 `SmartDosingDatabase.SCHEMA_VERSION` 并补充 Migration，禁止破坏性迁移
- 数据库文件位置: `/data/data/com.example.smartdosing/databases/smartdosing.db`

### 局域网接口
- 除配对接口外，所有 `/api` 请求必须携带 `X-API-Key`
- 对外接口返回专用 DTO，不要直接返回 Room 实体（实体中含 API Key 等敏感字段）

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

- 数据库、局域网任务接收、蓝牙称重、配料主流程：可用
- 操作员身份：本机占位实现，正式登录协议待主机端确定
- TTS 语音播报、网页管理端：已移除（需要时可从 git 历史找回）

## 文档

- `docs/通信协议.md`：局域网传输协议
- `docs/局域网传输401错误排查指南.md`：鉴权问题排查
- `docs/设计规范_SmartDosing.md`：界面设计规范
- `docs/archive/`：历史方案与报告，仅供追溯，内容可能已过时

## 常见问题

1. **编译错误找不到生成代码**: Clean Project 后重新 Build
2. **数据库崩溃**: 检查是否在主线程调用 suspend 函数
3. **`jlink executable ... does not exist`**: `JAVA_HOME` 指向了不完整的 JDK，换成 Android Studio 自带的 `jbr` 或完整 JDK
