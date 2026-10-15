# Codex 子代理编排工作方法（本仓总纲）

> 移植自 sk-tiaoxiang/dk-yanfa 项目的同款约定（2026-06-12），并按本仓（Android/Gradle）适配。
> 每份用 Codex 执行的实施计划头部贴「执行模式」模板（见文末），细则以本文为准，不必重抄。

## 分工模型

- **主线 Claude**：写实施计划、编排批次、派发任务、审核 diff、运行验证、git 提交。
- **Codex 子代理**：按计划分批**串行**执行（通过 `codex:codex-rescue` 子代理派发，每次 rescue 只调一次 companion `task`）。
- **执行者禁止一切写入型 git 操作**（add/commit/checkout/stash/restore/reset 均不允许），git 只在主线做。
- **禁止用 PowerShell 写文件**（编码会损坏），保存 UTF-8 无 BOM。
- Codex 侧项目指令在仓库根 `AGENTS.md`（含执行者硬性约束一节）。

## 派发与监控

1. 用 Agent 工具派发 `subagent_type: codex:codex-rescue`；rescue 是纯转发器，提示词要一次性写全（任务说明、点名要读的文件、验证命令、约束）。
2. **长任务（>10 分钟）必须后台派发**（Agent `run_in_background: true`），前台等待会撞 Bash 10 分钟上限，forwarder 只会返回"已在后台启动"。
3. 派发后用 companion `status --json` 轮询收尾：
   ```bash
   node "C:/Users/Administrator/.claude/plugins/cache/openai-codex/codex/1.0.4/scripts/codex-companion.mjs" status --json
   ```
4. **status 注册表会谎报 running**（elapsed 用 startedAt 推算，进程死了仍显示运行中）。判活必须看 **pid 存活 + 日志 mtime**；监视循环要加「日志静默 N 分钟 → 判定停滞」分支，否则僵尸任务下监视器永不触发。
5. companion `cancel` 在 Git Bash 下会被 MSYS 路径转换毁掉 `taskkill /PID`——清理僵尸进程用 PowerShell 跑。

## 计划写法（给 Codex 的任务说明）

1. **不写「具体但未核实的默认做法 + 对冲句」**。Codex 会取字面默认而忽略对冲。正确写法：把「先查 X 文件的既有约定，复用之」写成主指令，不给可能错的具体默认值。
2. **显式点名要对齐的约定文件**。Codex 契约遵循能力好（结构/测试风格能对齐既有代码），弱点是跨文件既有约定的主动发现——计划必须列出"执行者先读这些文件再动手"清单。
3. **验证命令先在本地跑通再写入计划**。本仓标准验证命令：`./gradlew compileDebugKotlin`（2026-06-12 已核实，增量约 20 秒）。不要写未核实的旗标。
4. 已拍板的决策连同理由列成表放计划里，避免执行者重新发明。

## 主线审核要点

1. **审核流水线化**：Codex 串行做 T1→T2→T3 时，主线对已完成任务先行 `git diff` 审核（只读不冲突），返修项攒成清单等批次收尾一次发回，省往返。
2. **专盯单测拦不住的运行时断层**（mock 全绿但真实链路断）。本仓高危检查位：
   - Room suspend 函数是否在协程中调用（主线程直调会崩溃）；
   - mock 掩盖的数据库语义（Room 实体可空列、TypeConverter 边界、事务范围）；
   - 新页面是否**同时**登记 `SmartDosingDestinations.kt` 与 `SmartDosingNavHost.kt`；
   - 是否误用旧 `RecipeRepository` 而非 `DatabaseRecipeRepository`；
   - Web 层（Ktor）与 UI 层之间 JSON 字段口径一致性（可空/缺省差异）；
   - 中文文案/词汇表跨文件一致性。
3. 审核通过后由主线统一 git 提交；提交信息遵循本仓现有风格（中文一句话）。

## 执行模式头部模板（贴在每份计划开头）

```markdown
> **执行模式（本仓覆盖约定）**：由主线 Claude 编排，Codex 子代理分批执行（批次串行），
> 主线统一审核 + 提交。**执行子代理禁止一切 git 写操作**（git 只在主线做）。
> **禁止用 PowerShell 写文件**（会编码损坏），保存 UTF-8 无 BOM。
> 每个任务完成后跑 `./gradlew compileDebugKotlin` 验证。细则见 docs/codex-orchestration.md。
```
