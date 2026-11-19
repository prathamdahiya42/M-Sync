package com.classjam.games

import com.classjam.core.model.Device
import com.classjam.core.model.Role
import com.classjam.games.engine.GameHostContext
import com.classjam.games.engine.ScoreboardManager
import com.classjam.games.musicalchairs.MusicalChairsGame
import com.classjam.network.protocol.Envelope
import com.classjam.network.protocol.GameEventPayload
import com.classjam.network.protocol.GameType
import com.classjam.network.protocol.MessageTypes
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameLogicDecisionTest {

    private lateinit var scoreboard: ScoreboardManager
    private val broadcastedEvents = mutableListOf<GameEventPayload>()
    private var musicPaused = false

    private val devices = mapOf(
        "host" to Device("host", "Admin", Role.ADMIN),
        "phone_a" to Device("phone_a", "Alice", Role.PLAYER),
        "phone_b" to Device("phone_b", "Bob", Role.PLAYER)
    )

    private val mockContext = object : GameHostContext {
        override val connectedDevices: Map<String, Device> = devices
        override val scoreboard: ScoreboardManager get() = this@GameLogicDecisionTest.scoreboard
        override fun sendTo(deviceId: String, envelope: Envelope) {}
        override fun broadcast(envelope: Envelope) {}
        override fun broadcastGameEvent(event: GameEventPayload) {
            broadcastedEvents.add(event)
        }
        override fun broadcastScoreboard() {}
        override fun pauseMusic() {
            musicPaused = true
        }
        override fun playMusic(trackId: String) {}
    }

    @Before
    fun setUp() {
        scoreboard = ScoreboardManager()
        broadcastedEvents.clear()
        musicPaused = false
    }

    @Test
    fun testMusicalChairsWinnerDecisionWithSkewedNetworkPackets() {
        val game = MusicalChairsGame()
        game.onStart(mockContext)

        // Force music to stop at time T = 10_000_000_000 ns
        val stopTimeNs = 10_000_000_000L
        game.onTick(stopTimeNs + 100_000_000_000L, mockContext) // Force trigger stop
        assertTrue(musicPaused)

        // SKEWED PACKET SIMULATION:
        // Alice tapped at stopTimeNs + 120ms (true faster tap)
        // Bob tapped at stopTimeNs + 350ms (true slower tap)
        val aliceCorrectedTapNs = stopTimeNs + 120_000_000L
        val bobCorrectedTapNs = stopTimeNs + 350_000_000L

        // However, Bob's packet arrives FIRST at the server over Wi-Fi
        val bobPacket = GameEventPayload(
            gameType = GameType.MUSICAL_CHAIRS,
            event = "SIT_TAP",
            senderDeviceId = "phone_b",
            correctedTimestampNs = bobCorrectedTapNs
        )
        game.onMessage(
            "phone_b",
            Envelope(MessageTypes.GAME_EVENT, payload = bobPacket.toJson()),
            mockContext
        )

        // Alice's packet arrives SECOND at the server (delayed by Wi-Fi queuing)
        val alicePacket = GameEventPayload(
            gameType = GameType.MUSICAL_CHAIRS,
            event = "SIT_TAP",
            senderDeviceId = "phone_a",
            correctedTimestampNs = aliceCorrectedTapNs
        )
        game.onMessage(
            "phone_a",
            Envelope(MessageTypes.GAME_EVENT, payload = alicePacket.toJson()),
            mockContext
        )

        // Tick to conclude the round
        game.onTick(stopTimeNs + 7_000_000_000L, mockContext)

        // VERIFY:
        // Alice MUST be declared winner (+10 points) because her corrected tap was 120ms,
        // even though Bob's packet reached the host earlier.
        // Bob MUST lose (-5 points).
        assertEquals(10, scoreboard.getScore("phone_a"))
        assertEquals(-5, scoreboard.getScore("phone_b"))

        val roundOverEvent = broadcastedEvents.find { it.event == "ROUND_OVER" }
        assertNotNull(roundOverEvent)
        assertTrue(roundOverEvent?.extraData?.contains("Alice sat down first") == true)
        assertTrue(roundOverEvent?.extraData?.contains("Bob was too slow") == true)
    }

    @Test
    fun testScoreboardPointsAccumulationAndReset() {
        scoreboard.addPoints("phone_a", 10)
        scoreboard.addPoints("phone_b", -5)
        assertEquals(10, scoreboard.getScore("phone_a"))
        assertEquals(-5, scoreboard.getScore("phone_b"))

        scoreboard.addPoints("phone_a", 10)
        assertEquals(20, scoreboard.getScore("phone_a"))

        scoreboard.reset()
        assertEquals(0, scoreboard.getScore("phone_a"))
        assertEquals(0, scoreboard.getScore("phone_b"))
        assertEquals(1, scoreboard.roundNumber)
    }
}
