package com.example.smartdosing.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationRecordSampleData
import com.example.smartdosing.data.ConfigurationRecordStatus
import com.example.smartdosing.data.ConfigurationTask
import com.example.smartdosing.data.TaskPriority
import com.example.smartdosing.data.TaskSampleData
import com.example.smartdosing.data.TaskStatus
import kotlin.math.min

/**
 * 研发配置任务仓库接口，后续接入后端 API 时只需替换实现。
 */
interface ConfigurationTaskRepository {
    suspend fun fetchTasks(status: TaskStatus? = null): List<ConfigurationTask>
    suspend fun fetchTask(taskId: String): ConfigurationTask?
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus): ConfigurationTask?
    // 接单相关方法
    suspend fun acceptTask(taskId: String, acceptedBy: String): ConfigurationTask?
    // 上位机状态汇报
    suspend fun reportTaskProgress(
        taskId: String, 
        status: TaskStatus,
        acceptedBy: String? = null,
        startedAt: String? = null,
        completedAt: String? = null,
        note: String? = null
    ): Boolean
}

/**
 * 研发配置记录仓库接口
 */
data class ConfigurationRecordFilter(
    val customer: String? = null,
    val salesOwner: String? = null,
    val operator: String? = null,
    val status: ConfigurationRecordStatus? = null,
    val sortAscending: Boolean = false,
    val limit: Int? = null,
    val offset: Int? = null
)

interface ConfigurationRecordRepository {
    suspend fun fetchRecords(filter: ConfigurationRecordFilter = ConfigurationRecordFilter()): List<ConfigurationRecord>
    suspend fun fetchRecord(recordId: String): ConfigurationRecord?
    suspend fun createRecord(record: ConfigurationRecord): ConfigurationRecord
    suspend fun updateRecordStatus(recordId: String, status: ConfigurationRecordStatus, note: String? = null): ConfigurationRecord?
}

/**
 * 目前使用的内存实现，后端就绪后可替换为网络版。
 */
class InMemoryConfigurationTaskRepository : ConfigurationTaskRepository {
    private val mutex = Mutex()
    private val tasks = TaskSampleData.dailyTasks().toMutableList()

    override suspend fun fetchTasks(status: TaskStatus?): List<ConfigurationTask> {
        return mutex.withLock {
            status?.let { target -> tasks.filter { it.status == target } } ?: tasks.toList()
        }
    }

    override suspend fun fetchTask(taskId: String): ConfigurationTask? {
        return mutex.withLock { tasks.find { it.id == taskId } }
    }

    override suspend fun updateTaskStatus(taskId: String, status: TaskStatus): ConfigurationTask? {
        return mutex.withLock {
            val index = tasks.indexOfFirst { it.id == taskId }
            if (index < 0) return@withLock null
            val updated = tasks[index].copy(
                priority = tasks[index].priority.takeIf { it != TaskPriority.URGENT } ?: TaskPriority.URGENT,
                status = status
            )
            tasks[index] = updated
            updated
        }
    }

    override suspend fun acceptTask(taskId: String, acceptedBy: String): ConfigurationTask? {
        Log.d("TaskRepository", "开始接单 - taskId: $taskId, acceptedBy: $acceptedBy")
        return mutex.withLock {
            val index = tasks.indexOfFirst { it.id == taskId }
            if (index < 0) {
                Log.e("TaskRepository", "接单失败 - 未找到任务: $taskId")
                return@withLock null
            }
            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val updated = tasks[index].copy(
                status = TaskStatus.IN_PROGRESS,
                acceptedBy = acceptedBy,
                acceptedAt = now,
                statusUpdatedAt = now
            )
            tasks[index] = updated
            Log.d("TaskRepository", "接单成功 - taskId: $taskId, 新状态: ${updated.status}")
            // 模拟上报状态
            reportTaskProgress(taskId, TaskStatus.IN_PROGRESS, acceptedBy, null, null, "任务已接单")
            updated
        }
    }

    override suspend fun reportTaskProgress(
        taskId: String,
        status: TaskStatus,
        acceptedBy: String?,
        startedAt: String?,
        completedAt: String?,
        note: String?
    ): Boolean {
        // 内存版本仅记录日志
        val logMessage = "上位机状态汇报: 任务ID=$taskId, 状态=$status, 接单人=$acceptedBy, 备注=$note"
        Log.i("TaskRepository", logMessage)
        println(logMessage)
        return true
    }
}

class InMemoryConfigurationRecordRepository : ConfigurationRecordRepository {
    private val mutex = Mutex()
    private val records = ConfigurationRecordSampleData.records().toMutableList()

    override suspend fun fetchRecords(filter: ConfigurationRecordFilter): List<ConfigurationRecord> {
        return mutex.withLock {
            var result = records.toList()
            filter.customer?.takeIf { it.isNotBlank() }?.let { customer ->
                result = result.filter { it.customer == customer }
            }
            filter.salesOwner?.takeIf { it.isNotBlank() }?.let { owner ->
                result = result.filter { it.salesOwner == owner }
            }
            filter.operator?.takeIf { it.isNotBlank() }?.let { operator ->
                result = result.filter { it.operator == operator }
            }
            filter.status?.let { status ->
                result = result.filter { it.resultStatus == status }
            }
            result = if (filter.sortAscending) result.sortedBy { it.updatedAt } else result.sortedByDescending { it.updatedAt }
            val start = filter.offset ?: 0
            val end = min(result.size, start + (filter.limit ?: result.size))
            if (start < result.size) result.subList(start, end) else emptyList()
        }
    }

    override suspend fun fetchRecord(recordId: String): ConfigurationRecord? {
        return mutex.withLock { records.find { it.id == recordId } }
    }

    override suspend fun createRecord(record: ConfigurationRecord): ConfigurationRecord {
        return mutex.withLock {
            records.add(0, record)
            record
        }
    }

    override suspend fun updateRecordStatus(
        recordId: String,
        status: ConfigurationRecordStatus,
        note: String?
    ): ConfigurationRecord? {
        return mutex.withLock {
            val index = records.indexOfFirst { it.id == recordId }
            if (index < 0) return@withLock null
            val updated = records[index].copy(
                resultStatus = status,
                note = note ?: records[index].note
            )
            records[index] = updated
            updated
        }
    }
}

/**
 * 网络版实现：真实接入时替换 Fake API 即可。
 */
class NetworkConfigurationTaskRepository(
    private val api: ConfigurationTaskApi
) : ConfigurationTaskRepository {
    // 内存缓存：记录本地已接单（但可能未同步到服务器）的任务ID和接单人
    private val offlineAcceptedMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    override suspend fun fetchTasks(status: TaskStatus?): List<ConfigurationTask> {
        val serverTasks = api.fetchTasks(status).map { it.toEntity() }
        // 合并在本地已接单的任务状态
        return serverTasks.map { task ->
            if (offlineAcceptedMap.containsKey(task.id)) {
                task.copy(
                    status = TaskStatus.IN_PROGRESS,
                    acceptedBy = offlineAcceptedMap[task.id] ?: task.acceptedBy
                )
            } else {
                task
            }
        }
    }

    override suspend fun fetchTask(taskId: String): ConfigurationTask? {
        val task = api.fetchTask(taskId)?.toEntity()
        return if (task != null && offlineAcceptedMap.containsKey(taskId)) {
            task.copy(
                status = TaskStatus.IN_PROGRESS,
                acceptedBy = offlineAcceptedMap[taskId] ?: task.acceptedBy
            )
        } else {
            task
        }
    }

    override suspend fun updateTaskStatus(taskId: String, status: TaskStatus): ConfigurationTask? =
        api.updateTaskStatus(taskId, status)?.toEntity()

    override suspend fun acceptTask(taskId: String, acceptedBy: String): ConfigurationTask? {
        // 无论网络成功与否，先标记本地状态，保证“离线/本地优先即时响应”
        offlineAcceptedMap[taskId] = acceptedBy
        return api.acceptTask(taskId, acceptedBy)?.toEntity()
    }

    override suspend fun reportTaskProgress(
        taskId: String,
        status: TaskStatus,
        acceptedBy: String?,
        startedAt: String?,
        completedAt: String?,
        note: String?
    ): Boolean = api.reportTaskProgress(taskId, status, acceptedBy, startedAt, completedAt, note)
}

class NetworkConfigurationRecordRepository(
    private val api: ConfigurationRecordApi
) : ConfigurationRecordRepository {
    override suspend fun fetchRecords(filter: ConfigurationRecordFilter): List<ConfigurationRecord> =
        api.fetchRecords(filter.toDto()).map { it.toEntity() }

    override suspend fun fetchRecord(recordId: String): ConfigurationRecord? =
        api.fetchRecord(recordId)?.toEntity()

    override suspend fun createRecord(record: ConfigurationRecord): ConfigurationRecord =
        api.createRecord(record.toPayload()).toEntity()

    override suspend fun updateRecordStatus(
        recordId: String,
        status: ConfigurationRecordStatus,
        note: String?
    ): ConfigurationRecord? = api.updateRecordStatus(recordId, status, note)?.toEntity()
}

private fun ConfigurationRecordFilter.toDto(): ConfigurationRecordFilterDto = ConfigurationRecordFilterDto(
    customer = customer,
    salesOwner = salesOwner,
    operator = operator,
    status = status,
    sortAscending = sortAscending,
    limit = limit,
    offset = offset
)

/**
 * 提供当前的仓库实例，方便后续切换实现。
 */
object ConfigurationRepositoryProvider {
    private const val useNetworkRepository = true  // 使用网络API从后端获取数据

    private val networkTaskApi: ConfigurationTaskApi by lazy { HttpConfigurationTaskApi() }
    private val networkRecordApi: ConfigurationRecordApi by lazy { HttpConfigurationRecordApi() }

    val taskRepository: ConfigurationTaskRepository by lazy {
        if (useNetworkRepository) {
            NetworkConfigurationTaskRepository(networkTaskApi)
        } else {
            InMemoryConfigurationTaskRepository()
        }
    }

    val recordRepository: ConfigurationRecordRepository by lazy {
        if (useNetworkRepository) {
            NetworkConfigurationRecordRepository(networkRecordApi)
        } else {
            InMemoryConfigurationRecordRepository()
        }
    }
}
