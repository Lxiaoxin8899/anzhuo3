# 手动模式点击放大输入框 - 调研分析

## 问题描述

用户反馈：蓝牙秤连接后会放大输入位置，但手动模式不会。希望手动模式也能点击放大。

## 现有实现分析

### 蓝牙模式放大机制（MaterialConfigurationScreen.kt）

在 `MaterialConfigurationScreen.kt` 第 942 行发现了放大逻辑：

```kotlin
// 是否展开：活动行且蓝牙连接且未确认时展开
val isExpanded = isActive && isBluetoothConnected && !isConfirmed
```

**放大条件**：
1. `isActive` - 当前活动行
2. `isBluetoothConnected` - 蓝牙已连接
3. `!isConfirmed` - 未确认

**放大效果**（第 967-1129 行）：
- 使用 `Card` 包裹，带边框和阴影
- 大号序号徽章（36dp）
- 大号重量显示区（`displaySmall` 字体）
- 大号确认按钮（56dp 高度）
- 使用 `animateContentSize()` 实现平滑过渡

### 当前手动模式实现（DosingOperationScreen.kt）

手动模式使用 `ManualModeActiveMaterialStation` 组件：
- 已经是全屏布局
- 输入框占 40% 高度
- 没有点击放大功能

## 解决方案

### 方案 A：为 ManualInputDisplay 添加点击放大功能（推荐）

点击输入框时弹出全屏输入对话框，类似计算器应用。

**实现方式**：
1. 添加 `isExpanded` 状态
2. 点击 `ManualInputDisplay` 时设置 `isExpanded = true`
3. 展开时显示全屏 Dialog/BottomSheet，包含：
   - 超大号数字显示
   - 全屏数字键盘
   - 确认/取消按钮

```kotlin
var isInputExpanded by remember { mutableStateOf(false) }

// 点击输入框放大
ManualInputDisplay(
    modifier = Modifier.clickable { isInputExpanded = true },
    ...
)

// 放大后的全屏输入
if (isInputExpanded) {
    FullScreenInputDialog(
        weight = currentWeight,
        onWeightChange = onWeightChange,
        onConfirm = {
            isInputExpanded = false
            onConfirmNext()
        },
        onDismiss = { isInputExpanded = false }
    )
}
```

### 方案 B：使用 ModalBottomSheet 放大

点击时从底部弹出大号输入面板。

**优点**：
- 不完全遮挡原界面
- 用户体验更自然
- 可以看到材料信息

### 方案 C：动态调整布局权重

点击时动态调整输入框的权重，从 40% 变为 70%。

```kotlin
var isExpanded by remember { mutableStateOf(false) }

ManualInputDisplay(
    modifier = Modifier
        .weight(if (isExpanded) 0.7f else 0.4f)
        .clickable { isExpanded = !isExpanded }
        .animateContentSize(),
    ...
)
```

## 推荐实施：方案 A + 方案 C 结合

1. **默认状态**：输入框占 40%，点击可放大
2. **放大状态**：弹出全屏 Dialog，超大字体显示，全屏键盘

## 实施步骤

1. 在 `ManualModeActiveMaterialStation` 中添加 `isInputExpanded` 状态
2. 为 `ManualInputDisplay` 添加点击事件
3. 创建 `FullScreenManualInputDialog` 组件
4. 实现放大后的全屏输入界面
5. 编译验证

## 新组件设计：FullScreenManualInputDialog

```
┌─────────────────────────────────────────┐
│  [X]              材料名称              │  ← 顶部栏
├─────────────────────────────────────────┤
│                                         │
│                                         │
│              123.45                     │  ← 超大号数字 (120sp+)
│                                         │
│         目标: 12.5 KG    98%            │
│         ████████████████░░░░            │
│                                         │
├─────────────────────────────────────────┤
│  ┌─────┬─────┬─────┐                   │
│  │  7  │  8  │  9  │                   │
│  ├─────┼─────┼─────┤                   │
│  │  4  │  5  │  6  │     ┌─────────┐   │
│  ├─────┼─────┼─────┤     │  确认   │   │  ← 全屏键盘
│  │  1  │  2  │  3  │     │         │   │
│  ├─────┼─────┼─────┤     ├─────────┤   │
│  │  .  │  0  │  ⌫  │     │  清空   │   │
│  └─────┴─────┴─────┘     └─────────┘   │
└─────────────────────────────────────────┘
```

## 文件修改清单

| 文件 | 修改内容 |
|------|----------|
| DosingOperationScreen.kt | 添加 `isInputExpanded` 状态和 `FullScreenManualInputDialog` 组件 |
| ManualModeActiveMaterialStation | 添加点击放大逻辑 |
| ManualInputDisplay | 添加 `onClick` 参数 |
