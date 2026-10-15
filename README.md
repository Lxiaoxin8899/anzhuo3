# SmartDosing

实验室智能配料 Android 应用：接收主机下发的研发任务，配合蓝牙电子天平逐项称量配料，生成可追溯的实验档案。

## 主要功能

- **局域网接单**：内置 Ktor 服务，发送端配对后凭 API Key 下发任务、同步配方，配料结果自动回传
- **配料操作**：蓝牙天平（CH9140）实时称重、分级重量预警、提示音、超差处理
- **配方与档案**：配方导入（CSV / Excel 模板）、实验记录检索、报告 CSV 导出
- **数据安全**：本地数据库备份与恢复，恢复操作受管理员权限保护

## 构建

需要 Android SDK 36 和完整 JDK（含 `jlink`）。

```bash
./gradlew assembleDebug        # 构建调试包
./gradlew testDebugUnitTest    # 运行单元测试
./gradlew installDebug         # 安装到已连接设备
```

## 文档

| 文档 | 内容 |
|---|---|
| [CLAUDE.md](CLAUDE.md) | 项目结构、开发约定 |
| [docs/通信协议.md](docs/通信协议.md) | 局域网传输协议 |
| [docs/局域网传输401错误排查指南.md](docs/局域网传输401错误排查指南.md) | 鉴权问题排查 |
| [docs/设计规范_SmartDosing.md](docs/设计规范_SmartDosing.md) | 界面设计规范 |
| [docs/archive/](docs/archive/) | 历史方案与报告（可能已过时） |
