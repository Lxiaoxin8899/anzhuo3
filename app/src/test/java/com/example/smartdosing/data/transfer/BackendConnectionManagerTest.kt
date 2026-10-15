package com.example.smartdosing.data.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartdosing.database.SmartDosingDatabase
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 设备主动连后端的 WebSocket 状态机：握手、断线重连、连接失败重试、手动停止
 */
@RunWith(AndroidJUnit4::class)
class BackendConnectionManagerTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var manager: BackendConnectionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        if (::manager.isInitialized) manager.destroy()
        server.shutdown()
        TaskResultCallbackManager.clearInstance()
        SmartDosingDatabase.closeAndClearInstance()
    }

    private fun createManager(target: ConnectionTarget? = defaultTarget()) =
        BackendConnectionManager(
            context = context,
            httpClient = OkHttpClient(),
            baseReconnectDelayMs = 50L,
            targetProvider = { target }
        ).also { manager = it }

    private fun defaultTarget() = ConnectionTarget(server.url("/").toString().trimEnd('/'), "device-key")

    /** 模拟后端：收到 HELLO 后回 CONNECTED（可关闭），可选地随后主动断开 */
    private class FakeBackend(
        private val closeAfterConnected: Boolean = false,
        private val acceptHello: Boolean = true
    ) : WebSocketListener() {
        val received = LinkedBlockingQueue<String>()

        override fun onMessage(webSocket: WebSocket, text: String) {
            received += text
            if (acceptHello && JsonParser.parseString(text).asJsonObject.get("type").asString == "HELLO") {
                webSocket.send("""{"type":"CONNECTED"}""")
                if (closeAfterConnected) webSocket.close(1000, "server restart")
            }
        }

        // 回应设备端的 close 帧，否则 MockWebServer.shutdown() 会一直等连接释放
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, null)
        }

        override fun onOpen(webSocket: WebSocket, response: Response) = Unit
    }

    private fun upgrade(backend: FakeBackend) = MockResponse().withWebSocketUpgrade(backend)

    private fun waitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun `握手成功后进入已连接 HELLO 携带设备 Key`() {
        val backend = FakeBackend()
        server.enqueue(upgrade(backend))
        createManager().start()

        assertTrue(waitUntil { manager.isConnected })

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/lan/devices/connect", request.path)
        val hello = JsonParser.parseString(backend.received.poll(1, TimeUnit.SECONDS)).asJsonObject
        assertEquals("HELLO", hello.get("type").asString)
        assertEquals("device-key", hello.getAsJsonObject("data").get("apiKey").asString)
    }

    @Test
    fun `握手前不允许发送业务消息`() {
        server.enqueue(upgrade(FakeBackend(acceptHello = false)))
        createManager().start()
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))

        assertFalse(manager.isConnected)
        assertFalse(manager.sendTaskResultJson("""{"transferId":"T-1"}"""))
    }

    @Test
    fun `后端断开后按退避自动重连`() {
        server.enqueue(upgrade(FakeBackend(closeAfterConnected = true)))
        val second = FakeBackend()
        server.enqueue(upgrade(second))
        createManager().start()

        assertTrue(waitUntil { server.requestCount >= 2 })
        assertTrue(waitUntil { manager.isConnected })
        assertNotNull(second.received.poll(2, TimeUnit.SECONDS))
    }

    @Test
    fun `握手被拒绝时仍会继续重试`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(upgrade(FakeBackend()))
        createManager().start()

        assertTrue(waitUntil { manager.isConnected })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `手动停止后不再重连`() {
        server.enqueue(upgrade(FakeBackend()))
        createManager().start()
        assertTrue(waitUntil { manager.isConnected })

        manager.stop()
        Thread.sleep(300)

        assertFalse(manager.isConnected)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `没有可用后端配置时不发起连接`() {
        createManager(target = null).start()
        Thread.sleep(200)

        assertEquals(0, server.requestCount)
        assertNull(server.takeRequest(10, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `reconnect 立即建立新连接 旧连接的关闭回调不影响新状态`() {
        server.enqueue(upgrade(FakeBackend()))
        val second = FakeBackend()
        server.enqueue(upgrade(second))
        createManager().start()
        assertTrue(waitUntil { manager.isConnected })

        manager.reconnect("配对已更新")

        assertTrue(waitUntil { server.requestCount == 2 && manager.isConnected })
        // 等旧 socket 的 onClosed 回调落地，确认没有把新连接标记为断开
        Thread.sleep(300)
        assertTrue(manager.isConnected)
        assertEquals(2, server.requestCount)
    }
}
