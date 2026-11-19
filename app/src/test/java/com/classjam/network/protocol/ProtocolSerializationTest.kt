package com.classjam.network.protocol

import com.classjam.core.model.Device
import com.classjam.core.model.QrPayload
import com.classjam.core.model.Role
import org.junit.Assert.*
import org.junit.Test

class ProtocolSerializationTest {

    @Test
    fun testQrPayloadSerializationAndDeserialization() {
        val payload = QrPayload(
            v = 1,
            ssid = "ClassJam_Hotspot_5G",
            password = "partysecretpass",
            ip = "192.168.43.1",
            port = 8080,
            token = "abc12345",
            roomName = "Alex's Party Jam",
            roomCode = "7821"
        )

        val json = payload.toJson()
        assertTrue(json.contains("ClassJam_Hotspot_5G"))
        assertTrue(json.contains("192.168.43.1"))

        val decoded = QrPayload.fromJson(json)
        assertNotNull(decoded)
        assertEquals(1, decoded?.v)
        assertEquals("ClassJam_Hotspot_5G", decoded?.ssid)
        assertEquals("partysecretpass", decoded?.password)
        assertEquals("192.168.43.1", decoded?.ip)
        assertEquals(8080, decoded?.port)
        assertEquals("abc12345", decoded?.token)
        assertEquals("Alex's Party Jam", decoded?.roomName)
        assertEquals("7821", decoded?.roomCode)
    }

    @Test
    fun testEnvelopeWrappingAndUnwrapping() {
        val joinPayload = JoinPayload(
            deviceId = "test-device-uuid-99",
            displayName = "Jordan Phone",
            token = "session_token_xyz",
            roomCode = "1234"
        )

        val envelope = Envelope(
            type = MessageTypes.JOIN,
            seq = 42L,
            ts = 1700000000000L,
            payload = joinPayload.toJson()
        )

        val envelopeJson = envelope.toJson()
        val parsedEnvelope = Envelope.fromJson(envelopeJson)

        assertNotNull(parsedEnvelope)
        assertEquals(MessageTypes.JOIN, parsedEnvelope?.type)
        assertEquals(42L, parsedEnvelope?.seq)
        assertEquals(1700000000000L, parsedEnvelope?.ts)

        val parsedJoin = JoinPayload.fromJson(parsedEnvelope!!.payload)
        assertNotNull(parsedJoin)
        assertEquals("test-device-uuid-99", parsedJoin?.deviceId)
        assertEquals("Jordan Phone", parsedJoin?.displayName)
        assertEquals("session_token_xyz", parsedJoin?.token)
        assertEquals("1234", parsedJoin?.roomCode)
    }

    @Test
    fun testWelcomePayload() {
        val welcome = WelcomePayload(
            deviceId = "device-1",
            assignedRole = Role.PLAYER,
            roomName = "Bass Room",
            roomCode = "9999",
            maxClients = 10,
            serverTimeNs = 1234567890L
        )

        val json = welcome.toJson()
        val decoded = WelcomePayload.fromJson(json)
        assertNotNull(decoded)
        assertEquals(Role.PLAYER, decoded?.assignedRole)
        assertEquals(10, decoded?.maxClients)
        assertEquals(1234567890L, decoded?.serverTimeNs)
    }

    @Test
    fun testDeviceListPayload() {
        val devices = listOf(
            Device(deviceId = "host-1", displayName = "Host", role = Role.ADMIN, isReady = true),
            Device(deviceId = "client-2", displayName = "Alex", role = Role.DJ, isReady = true),
            Device(deviceId = "client-3", displayName = "Sam", role = Role.PLAYER, isReady = false)
        )

        val payload = DeviceListPayload(devices = devices)
        val json = payload.toJson()
        val decoded = DeviceListPayload.fromJson(json)

        assertNotNull(decoded)
        assertEquals(3, decoded?.devices?.size)
        assertEquals(Role.ADMIN, decoded?.devices?.get(0)?.role)
        assertEquals(Role.DJ, decoded?.devices?.get(1)?.role)
        assertEquals(Role.PLAYER, decoded?.devices?.get(2)?.role)
    }

    @Test
    fun testPingPongPayload() {
        val ping = PingPayload(clientSendNs = 1000000L, seq = 1)
        val pingJson = ping.toJson()
        val pingDecoded = PingPayload.fromJson(pingJson)
        assertNotNull(pingDecoded)
        assertEquals(1000000L, pingDecoded?.clientSendNs)

        val pong = PongPayload(
            clientSendNs = 1000000L,
            serverReceiveNs = 1000500L,
            serverTransmitNs = 1000550L,
            seq = 1
        )
        val pongJson = pong.toJson()
        val pongDecoded = PongPayload.fromJson(pongJson)
        assertNotNull(pongDecoded)
        assertEquals(1000500L, pongDecoded?.serverReceiveNs)
    }
}
