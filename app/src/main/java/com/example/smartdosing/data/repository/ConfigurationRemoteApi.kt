
package com.example.smartdosing.data.repository

import com.example.smartdosing.data.ApiResponse
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationRecordSampleData
import com.example.smartdosing.data.ConfigurationRecordStatus
import com.example.smartdosing.data.ConfigurationTask
import com.example.smartdosing.data.TaskSampleData
import com.example.smartdosing.data.TaskStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

private const val DEFAULT_BASE_URL = "http://127.0.0.1:8080/api/"

/** DTO ?? */
data class ConfigurationTaskDto(
    val id: String,
    val title: String,
    val recipeId: String,
    val recipeName: String,
    val recipeCode: String,
    val quantity: Double,
    val unit: String,
    val priority: String,
    val requestedBy: String,
    val perfumer: String,
    val customer: String,
    val salesOwner: String,
    val status: TaskStatus,
    val deadline: String,
    val createdAt: String,
    val publishedAt: String?,
    val statusUpdatedAt: String,
    val targetDevices: List<String>,
    val note: String,
    val tags: List<String>
)

data class ConfigurationRecordDto(
    val id: String,
    val taskId: String,
    val recipeId: String,
    val recipeName: String,
    val recipeCode: String,
    val category: String,
    val operator: String,
    val quantity: Double,
    val actualQuantity: Double,
    val unit: String,
    val customer: String,
    val salesOwner: String,
    val resultStatus: ConfigurationRecordStatus,
    val updatedAt: String,
    val tags: List<String>,
    val note: String
)

data class ConfigurationRecordPayload(
    val taskId: String,
    val recipeId: String,
    val recipeCode: String,
    val recipeName: String,
    val category: String,
    val operator: String,
    val quantity: Double,
    val actualQuantity: Double,
    val unit: String,
    val customer: String,
    val salesOwner: String,
    val tags: List<String> = emptyList(),
    val note: String = "",
    val resultStatus: ConfigurationRecordStatus = ConfigurationRecordStatus.COMPLETED,
    val recordId: String? = null
)

data class ConfigurationRecordFilterDto(
    val customer: String? = null,
    val salesOwner: String? = null,
    val operator: String? = null,
    val status: ConfigurationRecordStatus? = null,
    val sortAscending: Boolean = false,
    val limit: Int? = null,
    val offset: Int? = null
)

interface ConfigurationTaskApi {
    suspend fun fetchTasks(status: TaskStatus? = null): List<ConfigurationTaskDto>
    suspend fun fetchTask(taskId: String): ConfigurationTaskDto?
    suspend fun updateTaskStatus(taskId: String, status: TaskStatus): ConfigurationTaskDto?
}

interface ConfigurationRecordApi {
    suspend fun fetchRecords(filter: ConfigurationRecordFilterDto? = null): List<ConfigurationRecordDto>
    suspend fun fetchRecord(recordId: String): ConfigurationRecordDto?
    suspend fun createRecord(payload: ConfigurationRecordPayload): ConfigurationRecordDto
    suspend fun updateRecordStatus(recordId: String, status: ConfigurationRecordStatus, note: String? = null): ConfigurationRecordDto?
}

/** Retrofit ?? */
private fun loggingInterceptor(): HttpLoggingInterceptor =
    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(loggingInterceptor())
    .callTimeout(15, TimeUnit.SECONDS)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

private data class TaskStatusUpdateDto(val status: TaskStatus)
private data class RecordStatusUpdateDto(val status: ConfigurationRecordStatus, val note: String? = null)

private interface ConfigurationTaskService {
    @GET("tasks")
    suspend fun getTasks(@Query("status") status: String?): ApiResponse<List<ConfigurationTaskDto>>

    @GET("tasks/{id}")
    suspend fun getTask(@Path("id") taskId: String): ApiResponse<ConfigurationTaskDto>

    @PATCH("tasks/{id}")
    suspend fun updateTask(
        @Path("id") taskId: String,
        @Body body: TaskStatusUpdateDto
    ): ApiResponse<ConfigurationTaskDto>
}

private interface ConfigurationRecordService {
    @GET("configuration-records")
    suspend fun getRecords(
        @Query("customer") customer: String?,
        @Query("salesOwner") salesOwner: String?,
        @Query("operator") operator: String?,
        @Query("status") status: String?,
        @Query("sort") sort: String?,
        @Query("limit") limit: Int?,
        @Query("offset") offset: Int?
    ): ApiResponse<List<ConfigurationRecordDto>>

    @GET("configuration-records/{id}")
    suspend fun getRecord(@Path("id") id: String): ApiResponse<ConfigurationRecordDto>

    @POST("configuration-records")
    suspend fun createRecord(@Body payload: ConfigurationRecordPayload): ApiResponse<ConfigurationRecordDto>

    @PATCH("configuration-records/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: String,
        @Body body: RecordStatusUpdateDto
    ): ApiResponse<ConfigurationRecordDto>
}

class HttpConfigurationTaskApi(
    baseUrl: String = DEFAULT_BASE_URL,
    client: OkHttpClient = defaultHttpClient()
) : ConfigurationTaskApi {
    private val service = retrofit(baseUrl, client).create(ConfigurationTaskService::class.java)

    override suspend fun fetchTasks(status: TaskStatus?): List<ConfigurationTaskDto> {
        val response = service.getTasks(status?.name)
        return response.data.takeIf { response.success } ?: emptyList()
    }

    override suspend fun fetchTask(taskId: String): ConfigurationTaskDto? =
        runCatching {
            val response = service.getTask(taskId)
            if (response.success) response.data else null
        }.getOrNull()

    override suspend fun updateTaskStatus(taskId: String, status: TaskStatus): ConfigurationTaskDto? =
        runCatching {
            val response = service.updateTask(taskId, TaskStatusUpdateDto(status))
            if (response.success) response.data else null
        }.getOrNull()
}

class HttpConfigurationRecordApi(
    baseUrl: String = DEFAULT_BASE_URL,
    client: OkHttpClient = defaultHttpClient()
) : ConfigurationRecordApi {
    private val service = retrofit(baseUrl, client).create(ConfigurationRecordService::class.java)

    override suspend fun fetchRecords(filter: ConfigurationRecordFilterDto?): List<ConfigurationRecordDto> {
        val sort = if (filter?.sortAscending == true) "asc" else "desc"
        val response = service.getRecords(
            customer = filter?.customer,
            salesOwner = filter?.salesOwner,
            operator = filter?.operator,
            status = filter?.status?.name,
            sort = sort,
            limit = filter?.limit,
            offset = filter?.offset
        )
        return if (response.success) response.data ?: emptyList() else emptyList()
    }

    override suspend fun fetchRecord(recordId: String): ConfigurationRecordDto? =
        runCatching {
            val response = service.getRecord(recordId)
            if (response.success) response.data else null
        }.getOrNull()

    override suspend fun createRecord(payload: ConfigurationRecordPayload): ConfigurationRecordDto =
        service.createRecord(payload).data!!

    override suspend fun updateRecordStatus(
        recordId: String,
        status: ConfigurationRecordStatus,
        note: String?
    ): ConfigurationRecordDto? = runCatching {
        val response = service.updateStatus(recordId, RecordStatusUpdateDto(status, note))
        if (response.success) response.data else null
    }.getOrNull()
}

/** Fake API 用于开发/测试环境 */
class FakeConfigurationTaskApi : ConfigurationTaskApi {
    private val tasks = TaskSampleData.dailyTasks().map { it.toDto() }.toMutableList()

    override suspend fun fetchTasks(status: TaskStatus?): List<ConfigurationTaskDto> {
        delay(200)
        return status?.let { target -> tasks.filter { it.status == target } } ?: tasks.toList()
    }

    override suspend fun fetchTask(taskId: String): ConfigurationTaskDto? {
        delay(80)
        return tasks.find { it.id == taskId }
    }

    override suspend fun updateTaskStatus(taskId: String, status: TaskStatus): ConfigurationTaskDto? {
        delay(120)
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return null
        val updated = tasks[index].copy(status = status)
        tasks[index] = updated
        return updated
    }
}

class FakeConfigurationRecordApi : ConfigurationRecordApi {
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val records = ConfigurationRecordSampleData.records().map { it.toDto() }.toMutableList()

    override suspend fun fetchRecords(filter: ConfigurationRecordFilterDto?): List<ConfigurationRecordDto> {
        delay(240)
        var result = records.toList()
        filter?.let { f ->
            f.customer?.takeIf { it.isNotBlank() }?.let { customer ->
                result = result.filter { it.customer == customer }
            }
            f.salesOwner?.takeIf { it.isNotBlank() }?.let { owner ->
                result = result.filter { it.salesOwner == owner }
            }
            f.operator?.takeIf { it.isNotBlank() }?.let { operator ->
                result = result.filter { it.operator == operator }
            }
            f.status?.let { status ->
                result = result.filter { it.resultStatus == status }
            }
            result = if (f.sortAscending) result.sortedBy { it.updatedAt } else result.sortedByDescending { it.updatedAt }
            val start = f.offset ?: 0
            val end = minOf(result.size, start + (f.limit ?: result.size))
            result = if (start < result.size) result.subList(start, end) else emptyList()
        }
        return result
    }

    override suspend fun fetchRecord(recordId: String): ConfigurationRecordDto? {
        delay(100)
        return records.find { it.id == recordId }
    }

    override suspend fun createRecord(payload: ConfigurationRecordPayload): ConfigurationRecordDto {
        delay(150)
        val dto = ConfigurationRecordDto(
            id = payload.recordId?.takeIf { it.isNotBlank() } ?: "CR-",
            taskId = payload.taskId.ifBlank { "RD-" },
            recipeId = payload.recipeId.ifBlank { payload.recipeCode },
            recipeName = payload.recipeName,
            recipeCode = payload.recipeCode,
            category = payload.category,
            operator = payload.operator,
            quantity = payload.quantity,
            actualQuantity = payload.actualQuantity,
            unit = payload.unit,
            customer = payload.customer.ifBlank { "未知客户" },
            salesOwner = payload.salesOwner.ifBlank { "未分配" },
            resultStatus = payload.resultStatus,
            updatedAt = formatter.format(Date()),
            tags = payload.tags.takeIf { it.isNotEmpty() } ?: listOf("新建"),
            note = payload.note
        )
        records.add(0, dto)
        return dto
    }

    override suspend fun updateRecordStatus(
        recordId: String,
        status: ConfigurationRecordStatus,
        note: String?
    ): ConfigurationRecordDto? {
        delay(120)
        val index = records.indexOfFirst { it.id == recordId }
        if (index < 0) return null
        val updated = records[index].copy(
            resultStatus = status,
            note = note ?: records[index].note,
            updatedAt = formatter.format(Date())
        )
        records[index] = updated
        return updated
    }
}

/** DTO ? Domain ?? */
fun ConfigurationTaskDto.toEntity(): ConfigurationTask = ConfigurationTask(
    id = id,
    title = title,
    recipeId = recipeId,
    recipeName = recipeName,
    recipeCode = recipeCode,
    quantity = quantity,
    unit = unit,
    priority = com.example.smartdosing.data.TaskPriority.valueOf(priority),
    requestedBy = requestedBy,
    perfumer = perfumer,
    customer = customer,
    salesOwner = salesOwner,
    status = status,
    deadline = deadline,
    createdAt = createdAt,
    publishedAt = publishedAt,
    statusUpdatedAt = statusUpdatedAt,
    targetDevices = targetDevices,
    note = note,
    tags = tags
)

fun ConfigurationTask.toDto(): ConfigurationTaskDto = ConfigurationTaskDto(
    id = id,
    title = title.ifBlank { recipeName },
    recipeId = recipeId,
    recipeName = recipeName,
    recipeCode = recipeCode,
    quantity = quantity,
    unit = unit,
    priority = priority.name,
    requestedBy = requestedBy,
    perfumer = perfumer.ifBlank { requestedBy },
    customer = customer,
    salesOwner = salesOwner,
    status = status,
    deadline = deadline,
    createdAt = createdAt,
    publishedAt = publishedAt,
    statusUpdatedAt = statusUpdatedAt,
    targetDevices = targetDevices,
    note = note,
    tags = tags
)

fun ConfigurationRecordDto.toEntity(): ConfigurationRecord = ConfigurationRecord(
    id = id,
    taskId = taskId,
    recipeId = recipeId,
    recipeName = recipeName,
    recipeCode = recipeCode,
    category = category,
    operator = operator,
    quantity = quantity,
    unit = unit,
    actualQuantity = actualQuantity,
    customer = customer,
    salesOwner = salesOwner,
    resultStatus = resultStatus,
    updatedAt = updatedAt,
    tags = tags,
    note = note
)

fun ConfigurationRecord.toDto(): ConfigurationRecordDto = ConfigurationRecordDto(
    id = id,
    taskId = taskId,
    recipeId = recipeId,
    recipeName = recipeName,
    recipeCode = recipeCode,
    category = category,
    operator = operator,
    quantity = quantity,
    actualQuantity = actualQuantity,
    unit = unit,
    customer = customer,
    salesOwner = salesOwner,
    resultStatus = resultStatus,
    updatedAt = updatedAt,
    tags = tags,
    note = note
)

fun ConfigurationRecord.toPayload(): ConfigurationRecordPayload = ConfigurationRecordPayload(
    taskId = taskId,
    recipeId = recipeId,
    recipeCode = recipeCode,
    recipeName = recipeName,
    category = category,
    operator = operator,
    quantity = quantity,
    actualQuantity = actualQuantity,
    unit = unit,
    customer = customer,
    salesOwner = salesOwner,
    tags = tags,
    note = note,
    resultStatus = resultStatus,
    recordId = id
)
