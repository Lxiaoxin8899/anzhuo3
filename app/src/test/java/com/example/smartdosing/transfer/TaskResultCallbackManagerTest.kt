package com.example.smartdosing.transfer

import android.content.Context
import com.example.smartdosing.data.ConfigurationRecord
import com.example.smartdosing.data.ConfigurationRecordStatus
import com.example.smartdosing.data.device.DeviceIdentity
import com.example.smartdosing.data.device.DeviceUIDManager
import com.example.smartdosing.data.device.ReceiverStatus
import com.example.smartdosing.data.transfer.BackendConnectionManager
import com.example.smartdosing.data.transfer.TaskResultCallbackManager
import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.database.dao.DeviceDao
import com.example.smartdosing.database.entities.PendingTaskResultSyncEntity
import com.example.smartdosing.database.entities.ReceivedTaskEntity
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class TaskResultCallbackManagerTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockDatabase = mockk<SmartDosingDatabase>(relaxed = true)
    private val mockDeviceDao = mockk<DeviceDao>(relaxed = true)
    private val mockBackendConnectionManager = mockk<BackendConnectionManager>(relaxed = true)
    private val mockHttpClient = mockk<OkHttpClient>(relaxed = true)

    private val testReceivedTask = ReceivedTaskEntity(
        id = "RT-ABC12345",
        transferId = "TRANS-99999",
        senderUID = "SENDER-UID-111",
        senderName = "Backend Server A",
        senderIP = "192.168.1.100",
        senderAppVersion = "2.0.0",
        schemaVersion = "1.0",
        title = "测试加注作业",
        recipeCode = "RCP-001",
        recipeName = "草莓香精测试配方",
        quantity = 50.0,
        unit = "g",
        priority = "NORMAL",
        deadline = "2026-05-24 12:00:00",
        customer = "A客户",
        note = "高精度实验",
        localRecipeId = "L-RCP-001",
        receivedAt = System.currentTimeMillis(),
        status = "PENDING"
    )

    private val testRecord = ConfigurationRecord(
        id = "CR-2026-001",
        taskId = "RT-ABC12345",
        recipeId = "L-RCP-001",
        recipeName = "草莓香精测试配方",
        recipeCode = "RCP-001",
        category = "香精类",
        operator = "调香师张三",
        quantity = 50.0,
        unit = "g",
        actualQuantity = 49.85,
        customer = "A客户",
        salesOwner = "销售李四",
        resultStatus = ConfigurationRecordStatus.COMPLETED,
        updatedAt = "2026-05-24 08:00"
    )

    private val testIdentity = DeviceIdentity(
        uid = "SD-DEV8888",
        deviceName = "实验室平板A",
        ipAddress = "192.168.1.120",
        port = 8080,
        appVersion = "1.0.0",
        status = ReceiverStatus.IDLE,
        createdAt = System.currentTimeMillis()
    )

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkObject(SmartDosingDatabase.Companion)
        mockkObject(BackendConnectionManager.Companion)
        mockkObject(DeviceUIDManager)

        every { SmartDosingDatabase.Companion.getDatabase(any()) } returns mockDatabase
        every { mockDatabase.deviceDao() } returns mockDeviceDao
        every { BackendConnectionManager.Companion.getInstance(any()) } returns mockBackendConnectionManager
        every { DeviceUIDManager.getDeviceIdentity(any()) } returns testIdentity

        // 利用反射将私有属性 httpClient 设为我们 Mock 的 okHttpClient
        val managerInstance = TaskResultCallbackManager.getInstance(mockContext)
        val httpClientField = TaskResultCallbackManager::class.java.getDeclaredField("httpClient")
        httpClientField.isAccessible = true
        httpClientField.set(managerInstance, mockHttpClient)

        // 默认数据库行为
        coEvery { mockDeviceDao.getReceivedTaskById(any()) } returns testReceivedTask
        coEvery { mockDeviceDao.getCallbackBaseUrl(any()) } returns "http://192.168.1.100:8000"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * 测试用例 1: WebSocket 同步完全成功，应该直接返回成功并不触发 HTTP。
     */
    @Test
    fun testSyncViaWebSocketSuccess() = runBlocking {
        every { mockBackendConnectionManager.sendTaskResultJson(any()) } returns true

        val success = TaskResultCallbackManager.getInstance(mockContext)
            .syncResultForTask("RT-ABC12345", testRecord, 1.0)

        assertTrue(success)
        // 验证数据库状态更新
        coVerify(exactly = 1) { mockDeviceDao.markTaskResultSynced("RT-ABC12345", "CR-2026-001", any()) }
        coVerify(exactly = 1) { mockDeviceDao.deletePendingTaskResultSync("TRANS-99999") }
        // 验证 HTTP 没被调起
        verify(exactly = 0) { mockHttpClient.newCall(any()) }
    }

    /**
     * 测试用例 2: WebSocket 中断 (发送失败)，回退至 HTTP 接口，且 HTTP 请求成功。
     */
    @Test
    fun testSyncViaWebSocketFailedFallbackToHttpSuccess() = runBlocking {
        // WebSocket 返回发送失败
        every { mockBackendConnectionManager.sendTaskResultJson(any()) } returns false

        // Mock OkHttp 返回 200 OK
        val mockCall = mockk<Call>()
        val mockResponse = mockk<Response>(relaxed = true)
        every { mockHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
        every { mockResponse.isSuccessful } returns true

        val success = TaskResultCallbackManager.getInstance(mockContext)
            .syncResultForTask("RT-ABC12345", testRecord, 1.0)

        assertTrue(success)
        // 验证数据库更新
        coVerify(exactly = 1) { mockDeviceDao.markTaskResultSynced("RT-ABC12345", "CR-2026-001", any()) }
        coVerify(exactly = 1) { mockDeviceDao.deletePendingTaskResultSync("TRANS-99999") }
        // 验证确实调用了 HTTP 发送
        verify(exactly = 1) { mockHttpClient.newCall(any()) }
    }

    /**
     * 测试用例 3: 双通道皆失败 (WebSocket 失败, HTTP 也因网络故障抛出异常)，应当压入待同步队列。
     */
    @Test
    fun testSyncBothChannelsFailedQueuesPendingSync() = runBlocking {
        // 1. WebSocket 失败
        every { mockBackendConnectionManager.sendTaskResultJson(any()) } returns false

        // 2. HTTP 抛出 IOException
        val mockCall = mockk<Call>()
        every { mockHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } throws IOException("网络无法连接")

        val success = TaskResultCallbackManager.getInstance(mockContext)
            .syncResultForTask("RT-ABC12345", testRecord, 1.0)

        assertFalse(success)
        // 验证不会标记数据库成功
        coVerify(exactly = 0) { mockDeviceDao.markTaskResultSynced(any(), any(), any()) }
        // 验证正确写入待同步数据表
        coVerify(exactly = 1) { mockDeviceDao.upsertPendingTaskResultSync(any()) }
    }

    /**
     * 测试用例 4: 多客户端轮流并发结果同步测试，确保操作原子性，不发生异常或崩溃。
     */
    @Test
    fun testConcurrentResultSyncDoesNotCrash() = runBlocking {
        every { mockBackendConnectionManager.sendTaskResultJson(any()) } returns true

        // 启动 10 个并发协程同步
        val jobs = List(10) { index ->
            async(Dispatchers.Default) {
                TaskResultCallbackManager.getInstance(mockContext)
                    .syncResultForTask("RT-ABC12345", testRecord.copy(id = "CR-CONC-$index"), 1.0)
            }
        }

        val results = jobs.awaitAll()
        results.forEach { success ->
            assertTrue(success)
        }

        // 验证 10 次成功的操作中，同步标记函数均被调用
        coVerify(exactly = 10) { mockDeviceDao.markTaskResultSynced("RT-ABC12345", any(), any()) }
    }

    /**
     * 测试用例 5: 自愈机制补传测试 (retryPendingResults)，补传成功后应自动从队列中移除。
     */
    @Test
    fun testRetryPendingResultsSuccess() = runBlocking {
        val pendingList = listOf(
            PendingTaskResultSyncEntity(
                id = "TRS-TRANS-888",
                taskId = "RT-ABC12345",
                transferId = "TRANS-888",
                senderUID = "SENDER-UID-111",
                recordId = "CR-2026-001",
                payloadJson = "{\"test\":true}",
                attempts = 1,
                lastError = "前期失败",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // Mock 数据库拉出 pending 数据
        coEvery { mockDeviceDao.getPendingTaskResultSyncs(any()) } returns pendingList
        // 补传时 WebSocket 连接成功
        every { mockBackendConnectionManager.sendTaskResultJson(any()) } returns true

        TaskResultCallbackManager.getInstance(mockContext).retryPendingResults(limit = 10)

        // 验证补传成功后更新状态且移除队列
        coVerify(exactly = 1) { mockDeviceDao.markTaskResultSynced("RT-ABC12345", "CR-2026-001", any()) }
        coVerify(exactly = 1) { mockDeviceDao.deletePendingTaskResultSync("TRANS-888") }
    }
}
