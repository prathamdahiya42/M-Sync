package com.classjam.network.server

import com.classjam.core.model.Device
import com.classjam.core.model.Role
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RoleManagementTest {

    private lateinit var server: KtorHostServer

    @Before
    fun setUp() {
        server = KtorHostServer(
            hostDeviceId = "host-device-0",
            hostDisplayName = "Host Admin",
            port = 8080,
            roomName = "Test Jam",
            roomCode = "1234",
            sessionToken = "token-test",
            maxClients = 10
        )
    }

    @Test
    fun testInitialHostRoleIsAdmin() {
        val devices = server.connectedDevices.value
        assertEquals(1, devices.size)
        val host = devices["host-device-0"]
        assertNotNull(host)
        assertEquals(Role.ADMIN, host?.role)
        assertTrue(host?.inOutputGroup == true)
        assertFalse(host?.isMuted == true)
    }

    @Test
    fun testGrantDjPromotesTargetAndDemotesPreviousDj() {
        // Add two client devices
        val client1 = Device(deviceId = "client-1", displayName = "Alex", role = Role.PLAYER)
        val client2 = Device(deviceId = "client-2", displayName = "Sam", role = Role.PLAYER)
        server.updateDevice(client1)
        server.updateDevice(client2)

        // 1. Grant DJ to client1
        server.grantDj("client-1")
        assertEquals(Role.DJ, server.connectedDevices.value["client-1"]?.role)
        assertEquals("client-1", server.currentDjDeviceId)

        // 2. Grant DJ to client2 -> client1 must be demoted to PLAYER
        server.grantDj("client-2")
        assertEquals(Role.PLAYER, server.connectedDevices.value["client-1"]?.role)
        assertEquals(Role.DJ, server.connectedDevices.value["client-2"]?.role)
        assertEquals("client-2", server.currentDjDeviceId)
    }

    @Test
    fun testRevokeDjRevertsToPlayer() {
        val client1 = Device(deviceId = "client-1", displayName = "Alex", role = Role.PLAYER)
        server.updateDevice(client1)
        server.grantDj("client-1")
        assertEquals(Role.DJ, server.connectedDevices.value["client-1"]?.role)

        server.revokeDj("client-1")
        assertEquals(Role.PLAYER, server.connectedDevices.value["client-1"]?.role)
        assertNull(server.currentDjDeviceId)
    }

    @Test
    fun testToggleMute() {
        val client1 = Device(deviceId = "client-1", displayName = "Alex", role = Role.PLAYER, isMuted = false)
        server.updateDevice(client1)

        server.toggleMute("client-1")
        assertTrue(server.connectedDevices.value["client-1"]?.isMuted == true)

        server.toggleMute("client-1")
        assertFalse(server.connectedDevices.value["client-1"]?.isMuted == true)
    }

    @Test
    fun testToggleOutputGroup() {
        val client1 = Device(deviceId = "client-1", displayName = "Alex", role = Role.PLAYER, inOutputGroup = true)
        server.updateDevice(client1)

        server.toggleOutputGroup("client-1")
        assertFalse(server.connectedDevices.value["client-1"]?.inOutputGroup == true)

        server.toggleOutputGroup("client-1")
        assertTrue(server.connectedDevices.value["client-1"]?.inOutputGroup == true)
    }

    @Test
    fun testKickDeviceRemovesFromLobbyAndClearsDj() {
        val client1 = Device(deviceId = "client-1", displayName = "Alex", role = Role.PLAYER)
        server.updateDevice(client1)
        server.grantDj("client-1")

        server.kickDevice("client-1")
        assertFalse(server.connectedDevices.value.containsKey("client-1"))
        assertNull(server.currentDjDeviceId)
    }

    @Test
    fun testCannotKickHost() {
        server.kickDevice("host-device-0")
        assertTrue(server.connectedDevices.value.containsKey("host-device-0"))
        assertEquals(Role.ADMIN, server.connectedDevices.value["host-device-0"]?.role)
    }
}
