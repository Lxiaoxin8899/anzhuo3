package com.example.smartdosing.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartdosing.database.dao.DeviceDao
import com.example.smartdosing.database.entities.AuthorizedSenderEntity
import com.example.smartdosing.database.entities.PendingTaskResultSyncEntity
import com.example.smartdosing.database.entities.ReceivedTaskEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceDaoTest {

    private lateinit var database: SmartDosingDatabase
    private lateinit var dao: DeviceDao

    @Before
    fun setUp() {
        // Windows 上 Robolectric 使用 legacy SQLite，连接与打开它的线程绑定；
        // 用直接执行器让 Room 查询都在测试线程上跑，避免偶发 “Illegal connection pointer”
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmartDosingDatabase::class.java
        )
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        dao = database.deviceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sender(uid: String, apiKey: String?, active: Boolean = true) = AuthorizedSenderEntity(
        uid = uid,
        name = "后台-$uid",
        authorizedAt = 1L,
        isActive = active,
        callbackBaseUrl = "http://10.0.0.1:8000",
        senderApiKey = apiKey
    )

    private fun pending(id: String, transferId: String, updatedAt: Long) = PendingTaskResultSyncEntity(
        id = id,
        taskId = "RT-$transferId",
        transferId = transferId,
        senderUID = "S1",
        recordId = "CR-1",
        payloadJson = "{}",
        createdAt = updatedAt,
        updatedAt = updatedAt
    )

    @Test
    fun `API Key 只匹配启用中的发送端`() = runTest {
        dao.upsertSender(sender("S1", "key-1"))
        dao.upsertSender(sender("S2", "key-2", active = false))

        assertEquals("S1", dao.getSenderByApiKey("key-1")?.uid)
        assertNull(dao.getSenderByApiKey("key-2"))
        assertEquals(listOf("key-1"), dao.getAllSenderApiKeys())
        assertNull(dao.getCallbackBaseUrl("S2"))
    }

    @Test
    fun `停用发送端后 API Key 立即失效`() = runTest {
        dao.upsertSender(sender("S1", "key-1"))

        dao.setSenderActive("S1", false)

        assertNull(dao.getSenderByApiKey("key-1"))
        assertEquals(0, dao.getActiveSenderCount())
    }

    @Test
    fun `待同步队列按更新时间排序 同 id 覆盖 补传计数递增`() = runTest {
        dao.upsertPendingTaskResultSync(pending("TRS-B", "B", updatedAt = 20))
        dao.upsertPendingTaskResultSync(pending("TRS-A", "A", updatedAt = 10))
        dao.upsertPendingTaskResultSync(pending("TRS-B", "B", updatedAt = 5).copy(payloadJson = "{\"v\":2}"))

        val items = dao.getPendingTaskResultSyncs()
        assertEquals(listOf("TRS-B", "TRS-A"), items.map { it.id })
        assertEquals("{\"v\":2}", items.first().payloadJson)

        dao.incrementPendingTaskResultSyncAttempt("TRS-A", "超时", updatedAt = 30)
        val retried = dao.getPendingTaskResultSyncs().first { it.id == "TRS-A" }
        assertEquals(1, retried.attempts)
        assertEquals("超时", retried.lastError)

        dao.deletePendingTaskResultSync("B")
        assertEquals(listOf("TRS-A"), dao.getPendingTaskResultSyncs().map { it.id })
    }

    @Test
    fun `标记任务结果已同步`() = runTest {
        dao.insertReceivedTask(
            ReceivedTaskEntity(
                id = "RT-1",
                transferId = "T-1",
                senderUID = "S1",
                senderName = "后台",
                title = "任务",
                recipeCode = "R1",
                recipeName = "配方",
                quantity = 10.0,
                receivedAt = 1L
            )
        )

        dao.markTaskResultSynced("RT-1", "CR-9", syncedAt = 99L)

        val task = dao.getTaskByTransferId("T-1")!!
        assertEquals("CR-9", task.resultRecordId)
        assertEquals(99L, task.resultSyncedAt)
    }
}
