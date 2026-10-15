package com.example.smartdosing.web

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartdosing.data.DatabaseRecipeImportManager
import com.example.smartdosing.data.DatabaseRecipeRepository
import com.example.smartdosing.data.device.PairingRequestStore
import com.example.smartdosing.database.SmartDosingDatabase
import com.example.smartdosing.database.entities.AuthorizedSenderEntity
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 局域网 API 鉴权：缺 Key 401、无效 Key 403、配对接口公开、发送端列表不泄露 Key、UID 与 Key 必须匹配
 */
@RunWith(AndroidJUnit4::class)
class WebServerAuthTest {

    private lateinit var context: Context
    private lateinit var manager: WebServerManager

    private val senderKey = "sender-secret-key-001"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetSingletons()
        PairingRequestStore.resetForTest()
        runBlocking {
            SmartDosingDatabase.getDatabase(context).deviceDao().upsertSender(
                AuthorizedSenderEntity(
                    uid = "SENDER-1",
                    name = "后台",
                    authorizedAt = 1L,
                    callbackBaseUrl = "http://10.0.0.1:8000",
                    senderApiKey = senderKey
                )
            )
        }
        manager = WebServerManager(context)
    }

    @After
    fun tearDown() {
        PairingRequestStore.resetForTest()
        resetSingletons()
    }

    private fun resetSingletons() {
        DatabaseRecipeImportManager.clearInstance()
        DatabaseRecipeRepository.clearInstance()
        SmartDosingDatabase.closeAndClearInstance()
    }

    private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { with(manager) { smartDosingModule() } }
        block()
    }

    @Test
    fun `缺少 API Key 返回 401`() = withServer {
        val response = client.get("/api/recipes")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `无效 API Key 返回 403`() = withServer {
        val response = client.get("/api/recipes") { header("X-API-Key", "not-a-real-key") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `已配对发送端的 Key 可以访问 且发送端列表不包含 Key`() = withServer {
        val response = client.get("/api/device/senders") { header("X-API-Key", senderKey) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("SENDER-1"))
        assertFalse(body.contains(senderKey))
        assertFalse(body.contains("senderApiKey"))
        assertFalse(body.contains("callbackBaseUrl"))
    }

    @Test
    fun `发起配对与轮询结果无需 Key`() = withServer {
        val pair = client.post("/api/device/pair") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"senderUID":"NEW-1","senderName":"新后台","senderIP":"10.0.0.9","senderPort":8000,"callbackBaseUrl":"http://10.0.0.9:8000"}"""
            )
        }
        assertEquals(HttpStatusCode.OK, pair.status)
        val pairingId = Regex("\"pairingId\"\\s*:\\s*\"(PAIR-[^\"]+)\"").find(pair.bodyAsText())!!.groupValues[1]

        val poll = client.get("/api/device/pair/$pairingId")
        assertNotEquals(HttpStatusCode.Unauthorized, poll.status)
        assertNotEquals(HttpStatusCode.Forbidden, poll.status)
    }

    @Test
    fun `待确认配对列表不属于公开接口`() = withServer {
        val response = client.get("/api/device/pair/pending")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `下发任务时 senderUID 与 Key 不匹配返回 403`() = withServer {
        val response = client.post("/api/transfer/task") {
            header("X-API-Key", senderKey)
            contentType(ContentType.Application.Json)
            setBody("""{"transferId":"T-1","senderUID":"SENDER-OTHER","senderName":"冒充者"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("不匹配"))
    }

    @Test
    fun `Debug 无认证开关打开后跳过鉴权`() = withServer {
        context.getSharedPreferences(WebService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(WebService.KEY_DEBUG_NO_AUTH, true).commit()

        val response = client.get("/api/recipes")

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
