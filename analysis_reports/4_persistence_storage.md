# SmartDosing 持久化存储分析 - 配置记录如何保存

## 1. 结论先行（配置记录保存方式）

项目中的“配置记录（ConfigurationRecord）”目前**不是通过 Room 数据库保存**，而是通过 **应用私有目录文件 + 本地 Web API（Ktor）** 实现持久化：

- **保存介质**：`context.filesDir/configuration_records.json`
- **数据格式**：`List<ConfigurationRecord>` 的 JSON（Gson 序列化/反序列化）
- **编码**：Kotlin `readText()/writeText()` 默认使用 UTF-8（中文字段可直接存储）
- **写入时机**：创建记录 / 更新状态时立即写回文件
- **读入时机**：`ConfigurationRecordStore` 初始化时从磁盘加载到内存缓存
- **对外访问方式**：本机回环地址 `http://127.0.0.1:8080/api/configuration-records`（Retrofit 调用 Ktor）

---

## 2. 端到端数据链路（从“保存配置”到落盘）

### 2.1 UI 触发保存

入口在 `app/src/main/java/com/example/smartdosing/navigation/SmartDosingNavHost.kt`：

- `saveMaterialConfiguration(...)`
  - 将 `MaterialConfigurationData` 转为 `ConfigurationRecord`（`toConfigurationRecord()`）
  - 调用 `ConfigurationRepositoryProvider.recordRepository.createRecord(...)`

### 2.2 Repository 走本机 HTTP（Retrofit）

`app/src/main/java/com/example/smartdosing/data/repository/ConfigurationRepositories.kt`：

- `ConfigurationRepositoryProvider.recordRepository`
  - 当前固定为 `useNetworkRepository = true`
  - 实际使用 `NetworkConfigurationRecordRepository(HttpConfigurationRecordApi())`

`app/src/main/java/com/example/smartdosing/data/repository/ConfigurationRemoteApi.kt`：

- `DEFAULT_BASE_URL = "http://127.0.0.1:8080/api/"`
- Retrofit 接口 `POST configuration-records` → `createRecord(payload)`

### 2.3 Ktor Server 接收请求并持久化

`app/src/main/java/com/example/smartdosing/web/WebServerManager.kt`：

- 路由：`route("/configuration-records")`
  - `POST`：`recordStore.createRecord(payload)`，随后写入文件
  - `PATCH /{id}/status`：`recordStore.updateStatus(...)`，随后写入文件
- 真实落盘逻辑在私有类 `ConfigurationRecordStore`
  - 文件：`configuration_records.json`
  - 位置：`File(context.filesDir, STORE_FILE_NAME)`
  - 读：`storageFile.readText()`（Kotlin 默认 UTF-8）
  - 写：`storageFile.writeText(gson.toJson(records))`（Kotlin 默认 UTF-8）

---

## 3. 文件落盘位置（Android 侧）

运行时实际路径（不同 Android 版本可能略有差异）通常为：

- `/data/user/0/com.example.smartdosing/files/configuration_records.json`
- 或 `/data/data/com.example.smartdosing/files/configuration_records.json`

说明：
- 这是**应用私有目录**，无需存储权限，但外部无法直接访问。
- App 卸载 / 清除应用数据会删除该文件。

---

## 4. 现状风险点（可能导致“持久化问题”的来源）

1. **单文件全量覆盖写**：每次新增/更新都把整个 `records` 列表序列化并覆盖写回；记录变多后 I/O 成本会增加。
2. **非原子写入**：`writeText(...)` 过程中若异常中断，可能造成 JSON 文件部分写入/损坏；下次启动解析失败会丢失历史数据（只能从日志看出）。
3. **数据结构缺少版本字段**：未来字段调整（增删改）时，文件兼容性与迁移策略需要额外设计。
4. **与 Room 双轨并存**：项目中配方/导入日志/投料记录等已走 Room，但配置记录走文件，查询/备份/恢复/统计的实现路径不统一。

---

## 5. 建议（按收益优先级）

1. **增强文件写入可靠性**：引入“原子写”策略（临时文件写入后 rename，或使用 `android.util.AtomicFile`），避免文件损坏导致历史记录丢失。
2. **加入 schemaVersion + 迁移策略**：JSON 外层包一层 `{schemaVersion, data}`，便于未来兼容。
3. **中长期统一到 Room**：将配置记录（含材料明细）入库，获得事务、索引查询、分页、迁移等能力，并与现有数据库体系一致。

---

## 6. 快速验证（是否真的落盘）

在 Debug 环境可用 ADB 检查（需 `run-as` 权限）：

```bash
adb shell run-as com.example.smartdosing ls -l files
adb shell run-as com.example.smartdosing cat files/configuration_records.json
```
