package com.classjam.network.server

import com.classjam.core.model.Device
import com.classjam.core.model.Role
import com.classjam.core.model.Track
import com.classjam.network.protocol.ManifestPayload
import com.classjam.network.protocol.ReadyPayload
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TrackDistributionLogicTest {

    private lateinit var server: KtorHostServer

    @Before
    fun setUp() {
        server = KtorHostServer(
            hostDeviceId = "host-0",
            hostDisplayName = "Host Admin",
            port = 8080,
            roomName = "Party Jam",
            roomCode = "1234",
            sessionToken = "tok",
            maxClients = 10
        )
    }

    @Test
    fun testAllOutputGroupReadyState() {
        val client1 = Device(deviceId = "client-1", displayName = "Alex", inOutputGroup = true, isReady = false)
        val client2 = Device(deviceId = "client-2", displayName = "Sam", inOutputGroup = true, isReady = false)
        server.updateDevice(client1)
        server.updateDevice(client2)

        // Initially not all ready because clients are not ready
        assertFalse(server.isAllOutputGroupReady())

        // Client 1 finishes downloading and reports ready
        server.updateDevice(client1.copy(isReady = true, downloadProgress = 100))
        assertFalse(server.isAllOutputGroupReady())

        // Client 2 finishes downloading and reports ready
        server.updateDevice(client2.copy(isReady = true, downloadProgress = 100))
        assertTrue(server.isAllOutputGroupReady())
    }

    @Test
    fun testReadyPhonesOnlyOverrideBypassesUnreadyClients() {
        val client1 = Device(deviceId = "client-1", displayName = "Alex", inOutputGroup = true, isReady = true)
        val client2 = Device(deviceId = "client-2", displayName = "Slow Sam", inOutputGroup = true, isReady = false)
        server.updateDevice(client1)
        server.updateDevice(client2)

        // Slow Sam is not ready
        assertFalse(server.isAllOutputGroupReady())

        // Admin enables override
        server.setReadyPhonesOnlyOverride(true)
        assertTrue(server.isAllOutputGroupReady())
    }

    @Test
    fun testExcludedFromOutputGroupDoesNotBlockReady() {
        val client1 = Device(deviceId = "client-1", displayName = "Alex", inOutputGroup = true, isReady = true)
        val client2 = Device(deviceId = "client-2", displayName = "Spectator", inOutputGroup = false, isReady = false)
        server.updateDevice(client1)
        server.updateDevice(client2)

        // client2 is not in output group, so their unready status should not block playback
        assertTrue(server.isAllOutputGroupReady())
    }

    @Test
    fun testManifestPayloadSerialization() {
        val track = Track(
            id = "t1",
            title = "Midnight Jam",
            artist = "Funk Masters",
            durationMs = 210000L,
            sizeBytes = 5242880L,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )
        val manifest = ManifestPayload(
            currentTrack = track,
            queue = emptyList(),
            outputGroupDeviceIds = listOf("host-0", "client-1"),
            allPhonesReady = true
        )

        val json = manifest.toJson()
        assertTrue(json.contains("Midnight Jam"))
        val decoded = ManifestPayload.fromJson(json)
        assertNotNull(decoded)
        assertEquals("Midnight Jam", decoded?.currentTrack?.title)
        assertTrue(decoded?.allPhonesReady == true)
        assertEquals(2, decoded?.outputGroupDeviceIds?.size)
    }

    @Test
    fun testReadyPayloadSerialization() {
        val ready = ReadyPayload(trackId = "t1", isReady = true, progress = 100)
        val json = ready.toJson()
        val decoded = ReadyPayload.fromJson(json)
        assertNotNull(decoded)
        assertEquals("t1", decoded?.trackId)
        assertTrue(decoded?.isReady == true)
        assertEquals(100, decoded?.progress)
    }
}
