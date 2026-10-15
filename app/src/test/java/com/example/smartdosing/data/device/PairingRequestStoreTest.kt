package com.example.smartdosing.data.device

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PairingRequestStoreTest {

    private var now = 1_000_000L

    @Before
    fun setUp() {
        PairingRequestStore.resetForTest()
        PairingRequestStore.clock = { now }
    }

    @After
    fun tearDown() {
        PairingRequestStore.resetForTest()
    }

    private fun create(uid: String = "SENDER-1", ip: String = "192.168.1.10"): String? =
        PairingRequestStore.createRequest(
            senderUID = uid,
            senderName = "后台",
            senderIP = ip,
            senderPort = 8000,
            callbackBaseUrl = "http://$ip:8000"
        )

    @Test
    fun `确认后会话变为 APPROVED 并带上 API Key`() {
        val id = create()!!

        val apiKey = PairingRequestStore.approve(id)

        assertNotNull(apiKey)
        val session = PairingRequestStore.getSession(id)!!
        assertEquals(PairingStatus.APPROVED, session.status)
        assertEquals(apiKey, session.generatedApiKey)
        assertTrue(PairingRequestStore.getWaitingRequests().isEmpty())
    }

    @Test
    fun `拒绝后不能再确认`() {
        val id = create()!!

        assertTrue(PairingRequestStore.reject(id))

        assertNull(PairingRequestStore.approve(id))
        assertEquals(PairingStatus.REJECTED, PairingRequestStore.getSession(id)!!.status)
    }

    @Test
    fun `超过 60 秒的请求不能确认且从等待列表消失`() {
        val id = create()!!

        now += 60_001L

        assertFalse(PairingRequestStore.approveWithApiKey(id, "key"))
        assertTrue(PairingRequestStore.getWaitingRequests().isEmpty())
    }

    @Test
    fun `60 秒内仍可确认`() {
        val id = create()!!
        now += 59_000L
        assertTrue(PairingRequestStore.approveWithApiKey(id, "key"))
    }

    @Test
    fun `同一 IP 每分钟最多 5 次 过一分钟后恢复`() {
        repeat(5) { index -> assertNotNull(create(uid = "S-$index")) }

        assertNull(create(uid = "S-6"))
        // 其它 IP 不受影响
        assertNotNull(create(uid = "S-7", ip = "192.168.1.11"))

        now += 60_001L
        assertNotNull(create(uid = "S-8"))
    }

    @Test
    fun `同一发送端重复发起只保留最新的等待请求`() {
        val first = create()!!
        now += 1L
        val second = create()!!

        assertNull(PairingRequestStore.getSession(first))
        assertEquals(listOf(second), PairingRequestStore.getWaitingRequests().map { it.pairingId })
    }

    @Test
    fun `已完成的会话保留 5 分钟供轮询`() {
        val id = create()!!
        PairingRequestStore.approve(id)

        now += 4 * 60_000L
        assertNotNull(PairingRequestStore.getSession(id))

        now += 2 * 60_000L
        assertNull(PairingRequestStore.getSession(id))
    }
}
