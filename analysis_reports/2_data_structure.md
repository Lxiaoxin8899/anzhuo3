# SmartDosing 文件导入功能分析 - Part 2: 数据结构

## 二、配方数据结构详细分析

### 2.1 核心数据模型

#### Material 数据类 (投料材料)

**定义位置**: DosingOperationScreen.kt 第27行

```kotlin
data class Material(
    val id: String,           // 材料唯一编号 - 必需，用于唯一标识
    val name: String,         // 材料显示名称 - 必需，用于语音播报和显示
    val targetWeight: Float   // 目标投料重量 - 必需，单位KG
)
```

**字段说明**:
- **id**: 材料编号，通常为"ABC-001"格式，用于唯一识别和追溯
- **name**: 材料名称，需简洁清晰，适合TTS语音播报 (1-20字符推荐)
- **targetWeight**: 投料重量，浮点数，支持小数点 (如10.5, 22.0)

**约束条件**:
```
- id: 非空, 长度1-20, 仅支持字母数字和"-_"
- name: 非空, 长度1-50, 避免特殊符号
- targetWeight: > 0, <= 1000 (KG), 建议最多1位小数
```

**示例数据**:
```kotlin
listOf(
    Material("abc-001", "苹果香精", 10.5f),
    Material("def-002", "柠檬酸", 22.0f),
    Material("ghi-003", "甜蜜素", 5.2f)
)
```

---

#### RecipeItemData 数据类 (配方卡片)

**定义位置**: RecipesScreen.kt 第219-226行

```kotlin
data class RecipeItemData(
    val id: String,              // 配方ID
    val name: String,            // 配方名称
    val category: String,        // 分类 (香精/酸类/甜味剂/其他)
    val materialCount: Int,      // 包含的材料数量
    val totalWeight: String,     // 总重量 (格式: "5.2KG")
    val lastUsed: String         // 最后使用时间 (格式: "2024-01-15")
)
```

**样例数据**:
```kotlin
RecipeItemData(
    id = "recipe-001",
    name = "苹果香精配方",
    category = "香精类",
    materialCount = 3,
    totalWeight = "37.7KG",
    lastUsed = "2024-01-15"
)
```

**分类选项**:
```kotlin
val categories = listOf("全部", "香精", "酸类", "甜味剂", "其他")
```

---

#### RecordItemData 数据类 (投料记录)

**定义位置**: RecordsScreen.kt 第109-116行

```kotlin
data class RecordItemData(
    val id: String,              // 记录ID
    val recipeName: String,      // 配方名称
    val timestamp: String,       // 投料时间 (格式: "2024-01-15 14:30")
    val status: String,          // 投料状态 (完成/进行中/异常)
    val accuracy: String,        // 投料精度 (格式: "98%")
    val duration: String         // 耗时 (格式: "5分钟")
)
```

**样例数据**:
```kotlin
RecordItemData(
    id = "record-001",
    recipeName = "苹果香精配方",
    timestamp = "2024-01-15 14:30",
    status = "完成",
    accuracy = "98%",
    duration = "5分钟"
)
```

**状态值定义**:
```kotlin
enum class DosingStatus {
    COMPLETED("完成"),        // 投料完成，所有材料已确认
    IN_PROGRESS("进行中"),    // 正在投料
    FAILED("异常"),            // 投料出现异常
    CANCELLED("已取消")        // 用户主动取消
}
```

---

### 2.2 数据模型关系图

```
配方导入流程:
┌──────────────────────────┐
│  CSV/JSON/Excel 文件      │
│  (外部格式)              │
└──────────────┬───────────┘
               │ 解析
               ▼
┌──────────────────────────┐
│ List<Material>           │
│ (投料材料列表)            │
│ - id, name, weight       │
└──────────────┬───────────┘
               │
               ├─────────────────────┐
               │                     │
               ▼                     ▼
    ┌──────────────────┐  ┌──────────────────┐
    │ DosingScreen     │  │ RecipeItemData   │
    │ (投料操作)       │  │ (配方管理)        │
    └──────────────────┘  └──────────────────┘
               │
               │ 投料完成后
               ▼
    ┌──────────────────┐
    │ RecordItemData   │
    │ (投料记录)        │
    └──────────────────┘
```

---

### 2.3 数据字段完整映射表

| 导入来源 | 目标字段 | 类型 | 必需 | 说明 | 示例 |
|---------|---------|------|------|------|------|
| CSV/Col1 | Material.id | String | ✓ | 材料编号 | abc-001 |
| CSV/Col2 | Material.name | String | ✓ | 材料名称 | 苹果香精 |
| CSV/Col3 | Material.targetWeight | Float | ✓ | 重量(KG) | 10.5 |
| - | RecipeItemData.category | String | ✗ | 配方分类 | 香精类 |
| - | RecipeItemData.materialCount | Int | ✗ | 计算得出 | 3 |
| - | RecipeItemData.totalWeight | String | ✗ | 汇总计算 | 37.7KG |
| 系统 | RecipeItemData.lastUsed | Date | ✗ | 自动记录 | 2024-01-15 |

---

### 2.4 当前数据存储分析

**关键观察**: 应用目前使用内存存储，没有数据库

```kotlin
// DosingOperationScreen.kt
var recipe by remember { mutableStateOf<List<Material>?>(null) }
// 仅在内存中存储，应用关闭后丢失

// SettingsScreen.kt - 虽然有"备份数据"和"恢复数据"选项
SettingsItem.Action(
    title = "备份数据",
    subtitle = "备份配方和投料记录",
    icon = Icons.Default.Info,
    onClick = { }  // 未实现
),
SettingsItem.Action(
    title = "恢复数据",
    subtitle = "从备份文件恢复数据",
    icon = Icons.Default.Add,
    onClick = { }  // 未实现
)
```

**推荐改进**: 添加Room数据库

```kotlin
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val category: String,
    val createdAt: Long,
    val lastUsed: Long
)

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey
    val id: String,
    val recipeId: String,
    val name: String,
    val targetWeight: Float,
    @ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"])
    val recipeIdFk: String
)

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey
    val id: String,
    val recipeId: String,
    val timestamp: Long,
    val status: String,
    val accuracy: Float,
    val duration: Long
)
```

---

### 2.5 扩展字段建议

为了支持更完整的导入功能，建议扩展数据模型:

```kotlin
data class MaterialExtended(
    val id: String,
    val name: String,
    val targetWeight: Float,
    val unit: String = "KG",           // 重量单位 (KG/g/磅)
    val category: String = "其他",      // 材料分类
    val supplier: String? = null,       // 供应商
    val batchNumber: String? = null,    // 批号
    val expiryDate: LocalDate? = null,  // 过期日期
    val notes: String? = null           // 备注
)

data class RecipeExtended(
    val id: String,
    val name: String,
    val version: String = "1.0",        // 版本控制
    val category: String,
    val description: String? = null,    // 配方描述
    val materials: List<MaterialExtended>,
    val totalWeight: Float,             // 计算总重量
    val createdAt: LocalDateTime,       // 创建时间
    val lastModified: LocalDateTime,    // 最后修改
    val createdBy: String? = null,      // 创建人
    val tags: List<String> = emptyList() // 标签
)
```

这样可以在未来支持更复杂的Excel导入，包含更多元数据。

