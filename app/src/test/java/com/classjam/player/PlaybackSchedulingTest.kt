package com.classjam.player

import com.classjam.network.protocol.PausePayload
import com.classjam.network.protocol.PlayAtPayload
import com.classjam.network.protocol.SeekPayload
import org.junit.Assert.*
import org.junit.Test

class PlaybackSchedulingTest {

    @Test
    fun testPlayAtPayloadFutureTargetTime() {
        val nowNs = 1_000_000_000_000L
        val futureStartNs = nowNs + 2_000_000_000L // 2 seconds ahead

        val payload = PlayAtPayload(
            trackId = "track_42",
            startPositionMs = 0L,
            hostStartTimeNs = futureStartNs,
            outputGroupDeviceIds = listOf("phone_a", "phone_b")
        )

        assertEquals("track_42", payload.trackId)
        assertEquals(0L, payload.startPositionMs)
        assertEquals(futureStartNs, payload.hostStartTimeNs)
        assertTrue(payload.hostStartTimeNs > nowNs)
        assertEquals(2, payload.outputGroupDeviceIds.size)

        val json = payload.toJson()
        val decoded = PlayAtPayload.fromJson(json)
        assertNotNull(decoded)
        assertEquals("track_42", decoded?.trackId)
        assertEquals(futureStartNs, decoded?.hostStartTimeNs)
    }

    @Test
    fun testPausePayloadScheduling() {
        val nowNs = 500_000_000L
        val scheduledPauseNs = nowNs + 300_000_000L

        val payload = PausePayload(hostScheduledTimeNs = scheduledPauseNs)
        val json = payload.toJson()
        val decoded = PausePayload.fromJson(json)

        assertNotNull(decoded)
        assertEquals(scheduledPauseNs, decoded?.hostScheduledTimeNs)
    }

    @Test
    fun testSeekPayloadScheduling() {
        val nowNs = 10_000_000L
        val scheduledSeekNs = nowNs + 1_500_000_000L

        val payload = SeekPayload(targetPositionMs = 45000L, hostStartTimeNs = scheduledSeekNs)
        val json = payload.toJson()
        val decoded = SeekPayload.fromJson(json)

        assertNotNull(decoded)
        assertEquals(45000L, decoded?.targetPositionMs)
        assertEquals(scheduledSeekNs, decoded?.hostStartTimeNs)
    }
}
