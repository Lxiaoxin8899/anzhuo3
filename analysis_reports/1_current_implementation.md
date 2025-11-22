# SmartDosing 文件导入功能分析 - Part 1: 现有实现

## 一、现有CSV导入实现分析

### 1.1 导入流程概述

**核心位置**: `DosingOperationScreen.kt` 第130-159行

SmartDosing应用的CSV导入采用Android原生的文件选择机制，具体流程如下：

```
用户点击导入按钮 
    ↓
打开系统文件选择器 (ActivityResultContracts.OpenDocument)
    ↓
用户选择CSV文件 → 获取文件URI
    ↓
请求持久化读权限 (FLAG_GRANT_READ_URI_PERMISSION)
    ↓
使用ContentResolver打开输入流
    ↓
BufferedReader逐行读取
    ↓
按逗号分割，验证字段数
    ↓
构建Material对象
    ↓
返回List<Material>供UI使用
```

### 1.2 代码实现细节

```kotlin
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri: Uri? ->
    uri?.let {
        try {
            // 第1步: 获取文件读权限
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            
            val parsedRecipe = mutableListOf<Material>()
            
            // 第2步: 打开输入流
            context.contentResolver.openInputStream(it)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    
                    // 第3步: 逐行读取解析
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val tokens = line!!.split(',')
                        
                        // 第4步: 字段验证
                        if (tokens.size == 3) {
                            val material = Material(
                                id = tokens[0].trim(),
                                name = tokens[1].trim(),
                                targetWeight = tokens[2].trim().toFloat()
                            )
                            parsedRecipe.add(material)
                        }
                    }
                }
            }
            
            // 第5步: 更新UI状态
            if (parsedRecipe.isNotEmpty()) {
                recipe = parsedRecipe
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// 使用方式
Button(
    onClick = { launcher.launch(arrayOf("*/*")) },
    modifier = Modifier.width(300.dp).height(80.dp),
    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
    shape = RoundedCornerShape(12.dp)
) {
    Text(text = "导入CSV配方文件", fontSize = 20.sp, fontWeight = FontWeight.Medium)
}
```

### 1.3 核心优势分析

| 优势 | 说明 |
|------|------|
| **原生支持** | 使用Android官方的ActivityResultContracts，无需额外权限 |
| **权限安全** | 采用URI权限模型而非广泛的文件系统权限 |
| **内存高效** | 流式读取，不会因文件过大而OOM |
| **自动资源释放** | use {}表达式自动关闭流 |
| **分区存储兼容** | 支持Android 11+的分区存储模式 |

### 1.4 现有实现的主要限制

#### 限制1: 缺少错误处理

```kotlin
// 问题: 以下情况会导致崩溃
val targetWeight = tokens[2].trim().toFloat()  // 非数字会抛异常
// 示例: "abc-001,苹果香精,invalid" → NumberFormatException
```

#### 限制2: CSV格式处理不足

```kotlin
// 问题1: 无法处理包含逗号的字段
// 输入: abc-001,"苹果香精, 高质量",10.5
// 结果: tokens.size = 4，格式验证失败

// 问题2: 无法处理带BOM的文件
// UTF-8 BOM会导致第一个字段前有特殊字符

// 问题3: 无法处理转义字符
// "材料名\"带引号",10.5 会被错误分割
```

#### 限制3: 用户反馈不足

```kotlin
// 问题: 异常被吞掉，用户看不到错误信息
catch (e: Exception) {
    e.printStackTrace()  // 仅输出到Logcat，用户看不到
}

// 缺少进度指示
// 大文件导入时用户无法看到加载状态
```

#### 限制4: 数据验证缺失

```kotlin
// 问题: 没有对解析出的数据进行验证
// 可能的问题:
// - 空字符串ID/名称
// - 负数重量
// - 重复的ID
// - 内存溢出 (极大列表)
```

### 1.5 CSV格式现状

**当前期望格式**:
```csv
材料编号,材料名称,重量
abc-001,苹果香精,10.5
def-002,柠檬酸,22.0
ghi-003,甜蜜素,5.2
```

**现有格式限制**:
- 无头行支持
- 无转义处理
- 无注释支持
- 无类型检验

**需要改进的地方**:
1. 添加头行识别 (标记列意义)
2. 实现RFC 4180标准支持 (引号、转义)
3. 添加可选字段支持 (分类、单位等)
4. 实现列映射 (允许列顺序灵活变化)

