⚠️ 本文描述的是生产投料阶段内容，已归档，仅供参考。

## SmartDosing 设计规范（V1）

### 1. 设计目标
- 面向工控场景，保证在 8～13 寸平板、手机及桌面端均保持一致的工业风格；
- 在 Compose 侧提供统一的 Design Tokens，便于 UI/UX、开发协同；
- `SmartDosingTheme` 统一注入颜色、间距、圆角、阴影、语义色，可避免重复硬编码。

### 2. Design Tokens

| Token | 描述 | 访问方式 |
| --- | --- | --- |
| 间距 `SmartDosingSpacing` | none/xxs/xs/sm/md/lg/xl/xxl/giant | `SmartDosingTokens.spacing.lg` |
| 圆角 `SmartDosingRadius` | xs/sm/md/lg/xl | `SmartDosingTokens.radius.md` |
| 海拔 `SmartDosingElevation` | level0~level5（对应 0dp～14dp） | `SmartDosingTokens.elevation.level3` |
| 扩展语义色 `SmartDosingExtendedColors` | success/warning/danger/info/neutral/border | `SmartDosingTokens.colors.success` |

示例：

```kotlin
@Composable
fun PrimaryActionCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(SmartDosingTokens.radius.lg),
        modifier = Modifier.padding(SmartDosingTokens.spacing.lg),
        elevation = CardDefaults.cardElevation(SmartDosingTokens.elevation.level3)
    ) {
        Box(modifier = Modifier.padding(SmartDosingTokens.spacing.xl)) {
            content()
        }
    }
}
```

### 3. 色彩体系
- `primary`：Industrial Blue (`#1976D2`)
- `secondary`：Industrial Green (`#4CAF50`)
- `tertiary`：Industrial Orange (`#FF9800`)
- 扩展语义色：success/ warning/ danger/ info/ neutral/ border 在 `SmartDosingExtendedColors` 中维护，深浅主题自动切换。

### 4. 布局与适配建议
1. **Breakpoint**：≤600dp（Phone）、600～960dp（Tablet）、≥960dp（Desktop）。
2. **网格单位**：基于 `SmartDosingSpacing.sm (8dp)` 的 4dp/8dp 基础网格。
3. **圆角统一**：按钮/Chip 使用 `radius.sm`，卡片/对话框使用 `radius.lg`，Hero 模块使用 `radius.xl`。
4. **阴影层级**：浮层/对话框建议 level4+，Large 卡片 level2~3。

### 5. 语音播报范围说明
- 语音播报仅在“投料作业”流程中使用（播报物料、步骤及错误提示），其他界面不再引入语音状态或配置项。

### 6. 后续工作
- 导航框架升级（底栏/侧栏自适应）
- 页面级改造：首页 → 配方管理 → 投料作业 → 记录/设置
- 组件库沉淀：Action Card、指标卡、告警条、工业键盘等

> 若需要新增 Token，请在 `SmartDosingTokens` 中定义并更新本文件，确保设计/开发同步。
