# Android 官方技能审查报告（2026-06-12）

> 审查方式：基于 github.com/android/skills 官方技能（edge-to-edge / adaptive / testing-setup / r8-analyzer）四维度并行审计，
> 主线已对高严重度发现逐条抽查核实（file:line 均验证存在）。
> 审查版本基线：工作区含未提交的配对功能改动。

## 项目事实

- AGP 8.13.1，Kotlin 1.9.24，Compose BOM 2024.06.00，navigation-compose 2.7.0（Nav2）
- compileSdk 35，targetSdk 36，minSdk 26
- `isMinifyEnabled = false`，proguard-rules.pro 为空模板，release 无签名配置
- Room 走 kapt（非 KSP）；Gson 2.10.1（Ktor/Retrofit/手写三条链路使用）
- 测试：全项目仅 16 个真实测试方法（WeightWarningEvaluatorTest、TaskResultCallbackManagerTest）

---

## P0 立即处理（正确性/安全，改动小）

| # | 问题 | 位置 | 处理 |
|---|------|------|------|
| 1 | `compileSdk 35 < targetSdk 36` 非法组合，无法引用 API 36 接口做强制 edge-to-edge 适配 | app/build.gradle.kts:15,20 | compileSdk 升 36 |
| 2 | 局域网 Ktor 服务 **API Key 认证整体禁用**（注释写明"临时禁用"） | WebServerManager.kt:163-164 | 重新启用认证 |
| 3 | `/api/senders` 直接把 Room 实体 `AuthorizedSenderEntity`（含 senderApiKey 敏感列）外发 | WebServerManager.kt:796-800 | 建 DTO 脱敏 |
| 4 | `applicationId = com.example.quickdev` 与 `namespace = com.example.smartdosing` 不一致；真实数据目录是 `/data/data/com.example.quickdev/`，CLAUDE.md 文档与现实矛盾 | app/build.gradle.kts:14,18 | 确认是否有意；至少修文档 |

## P1 edge-to-edge（targetSdk 36 强制全面屏，用户可见问题）

1. **inset 消费链路断裂（系统性）**：外层 Scaffold 只 `padding(innerPadding)` 未 `consumeWindowInsets(innerPadding)`（MainActivity.kt:286-292；全工程 `consumeWindowInsets` 出现 0 次），导致所有自带 Scaffold+TopAppBar 的二级页面状态栏留白翻倍：LabCenterScreen:84、MaterialConfigurationScreen:433、MaterialListScreen:94、DeviceInfoScreen:88、ConfigurationRecordsScreen:87、ConfigurationRecordDetailScreen:73、BluetoothScaleSettingsScreen:529；TaskCenterScreen:182 嵌在 LabCenter 内为**三**倍。
2. **底栏固定高度 × 三键导航裁切**：NavigationBar 强制 `.height(60/66/72.dp)` 且 windowInsets 只取 Bottom（SmartDosingBottomNavigationBar.kt:92-100），三键设备 bottom inset ≈48dp，图标内容区仅剩 12-24dp。去掉固定总高度或把 inset 高度另加。
3. **IME 适配完全缺失**：Manifest 无 `windowSoftInputMode`（0 处），全工程无 imePadding/safeDrawing；MaterialConfigurationScreen:892/1043 手动重量输入、RecipesScreen:829/929、WebSettingsScreen:391 输入框会被软键盘遮挡。加 `adjustResize` + imePadding（置于 verticalScroll 之前）。
4. **主题手动着色破坏 edge-to-edge**：Theme.kt:167-171 在 SDK<R 设备上设不透明 statusBarColor（覆盖 enableEdgeToEdge 的透明 scrim），且手动 isAppearanceLightStatusBars 漏配导航栏侧。移除手动着色或两个标志成对设置。
5. 大屏 Row 布局只有装饰 padding 不消费任何系统栏 inset（MainActivity.kt:307-331）；列表 inset 该走 contentPadding（SettingsScreen:552、RecipesScreen:1121）。
6. 低优：未设 `isNavigationBarContrastEnforced = false`，三键设备底栏有系统 scrim。

合规项：enableEdgeToEdge 调用方式正确（MainActivity.kt:75）；对话框均标准 AlertDialog 无需处理；NavigationRail 默认 insets 正确；无 edge-to-edge opt-out。

## P2-A 自适应（工作台/平板场景）

已有基础：自研 600dp 切 NavigationRail、900dp 切配料双栏，方向正确。差距：

1. **三套互相矛盾的断点**：WindowSizeClass.kt:104-108（600/900）、RecipesScreen.kt:109-111（<900）、SmartDosingBottomNavigationBar.kt:55-71（360/520/600）；且 Expanded=900 偏离官方 840，840-899dp 窗口拿不到双栏。统一到官方 `WIDTH_DP_MEDIUM/EXPANDED_LOWER_BOUND`。
2. 断点判定用 `LocalConfiguration.screenWidthDp`（不含系统栏、自由窗口不可靠），应迁移 `currentWindowAdaptiveInfo()`（WindowSizeClass.kt:100-102）。
3. **结构性阻塞**：技能推荐的 Nav3 SceneStrategy 多窗格、NavigationSuiteScaffold、MediaQuery 全部需要 Nav3 + 新 Compose 工具链；当前 Kotlin 1.9.24 + BOM 2024.06 + Nav2.7 不可达。
4. 档案库列表→详情是教科书式 list-detail 候选，宽屏仍整页跳转（SmartDosingDestinations.kt:63-66）。
5. 宽屏单列 LazyColumn 卡片拉伸超 1000dp（TaskCenterScreen:144、RecipesScreen:1121、ConfigurationRecordsScreen:140、MaterialListScreen:256），可改 `GridCells.Adaptive`。
6. 零键鼠支持（工作台外接键盘"回车确认/Esc 关弹窗"全缺失）。
7. 低优：TaskCenterScreen:69 `selectedStatus` 用 remember 非 rememberSaveable，窗口变化丢状态；死代码 DosingScreen.kt（491 行未路由）误导适配现状。

合规项：无方向锁/resizeableActivity 限制；投料页大屏不强制全屏；核心投料流程状态已用 rememberSaveable+Saver。

## P2-B 测试基建

最该补测试的模块（按风险排序）：

1. **web/ Ktor 路由 + PairingRequestStore**——局域网暴露面、认证已禁用、配对发 key/限流/过期零覆盖
2. **BackendConnectionManager**——WebSocket 重连状态机（含本次陈旧回调守卫），并发时序 bug 高发
3. **DatabaseRecipeImportManager**——CSV/ZIP 解析错位直接影响物理配料安全，纯解析最适合 JVM 单测
4. **TaskReceiver**——任务校验 + 状态机守门人，目前只被 mock 间接碰过
5. **database/ 层**——DAO/DataMapper/Converter 零覆盖，补传队列真实 SQL 语义未验证

基建缺口：ktor-server-test-host、MockWebServer（4.12.0）、Robolectric、turbine、Jacoco、CI 全缺；room-testing 放错 sourceset；测试依赖未进 version catalog。
**最大结构性障碍**：无 DI，全是 `getInstance(context)` 单例，逼出现有测试的反射注入/mockkObject；最低成本解法是给单例构造器开可注入参数默认值。
现有测试问题：TaskResultCallbackManagerTest 不重置单例 INSTANCE（泄漏到后续测试类）、init 块异步与 coVerify 存在竞态、应改 runTest + MockWebServer。

## P3 R8/发布就绪

**当前直接开 `isMinifyEnabled = true` 是高风险**：

1. Gson 2.10.1 无内置 keep 规则 + 全项目 0 个 @SerializedName + proguard-rules.pro 空模板 → 字段混淆**无声毁掉**全部 HTTP/局域网 JSON 协议与本地记录回读。涉及 data/、data.transfer/、data.repository/、web/ 四层约 40 个模型类（完整清单与可用的 `-keepclassmembers` 规则文本见审查代理输出，已验证逐类被 Gson 使用）。**首选根治：Gson 升 ≥2.11.0 + 补 @SerializedName**。
2. TypeToken 匿名子类（WebServerManager.kt:1766、DatabaseConverters.kt:26）× R8 full mode = 运行时崩溃。
3. Ktor-Netty 需 `-dontwarn io.netty.**` 等规则，否则编译期 missing class 失败；NioServerSocketChannel 反射构造需窄 keep。
4. Retrofit 2.9.0 suspend 接口在 full mode 有已知泛型缺口，升 2.11.0+。
5. release 无 signingConfig（app/build.gradle.kts:28-34），产物不可安装，release 链路从未真机验证。
6. 开 R8 时同步开 `isShrinkResources = true` + `android.r8.optimizedResourceShrinking=true`（用了 material-icons-extended，收益可观）；启用 `-keepattributes SourceFile,LineNumberTable` 并归档 mapping.txt。
7. 蓝牙 CH9140Library.jar 厂商已预混淆、无 consumer 规则：先不加规则，开 R8 后烟测蓝牙秤，异常再补窄规则。

合规项：无冗余/过宽历史 keep 规则（零包袱）；枚举走默认规则即可；应用自身唯一反射目标是 framework 类；OkHttp/Coroutines/Compose 自带规则。

---

## 建议路线

1. **本周可做（小改动）**：P0 四项 + edge-to-edge 前四项（consumeWindowInsets、底栏高度、adjustResize+imePadding、主题着色）——适合按 Codex 编排工作法出一份实施计划分批执行。
2. **下一批**：测试三件套（ktor-test-host + MockWebServer + Robolectric）+ 单例注入口子 + 按风险序补前 3 个模块的测试。
3. **结构性规划（独立立项）**：工具链升级（Kotlin/Compose BOM/Nav3）解锁自适应多窗格；Gson/Retrofit 升级 + keep 规则 + release 签名后开 R8。
