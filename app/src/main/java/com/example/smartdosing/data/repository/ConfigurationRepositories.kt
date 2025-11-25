package com.example.smartdosing.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        delay(120) // 模拟网络延迟
        return mutex.withLock {
            status?.let { target -> tasks.filter { it.status == target } } ?: tasks.toList()
        }
    }

    override suspend fun fetchTask(taskId: String): ConfigurationTask? {
        delay(60)
        return mutex.withLock { tasks.find { it.id == taskId } }
    }

    override suspend fun updateTaskStatus(taskId: String, status: TaskStatus): ConfigurationTask? {
        delay(80)
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
}

class InMemoryConfigurationRecordRepository : ConfigurationRecordRepository {
    private val mutex = Mutex()
    private val records = ConfigurationRecordSampleData.records().toMutableList()

    override suspend fun fetchRecords(filter: ConfigurationRecordFilter): List<ConfigurationRecord> {
        delay(120)
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
        delay(80)
        return mutex.withLock { records.find { it.id == recordId } }
    }

    override suspend fun createRecord(record: ConfigurationRecord): ConfigurationRecord {
        delay(100)
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
        delay(100)
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
    override suspend fun fetchTasks(status: TaskStatus?): List<ConfigurationTask> =
        api.fetchTasks(status).map { it.toEntity() }

    override suspend fun fetchTask(taskId: String): ConfigurationTask? =
        api.fetchTask(taskId)?.toEntity()

    override suspend fun updateTaskStatus(taskId: String, status: TaskStatus): ConfigurationTask? =
        api.updateTaskStatus(taskId, status)?.toEntity()
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
    private const val useNetworkRepository = true

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
