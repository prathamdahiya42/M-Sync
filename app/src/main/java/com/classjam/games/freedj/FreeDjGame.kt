package com.classjam.games.freedj

import com.classjam.games.engine.Game
import com.classjam.games.engine.GameHostContext
import com.classjam.network.protocol.Envelope
import com.classjam.network.protocol.GameEventPayload
import com.classjam.network.protocol.GameType

class FreeDjGame : Game {
    override val gameType: GameType = GameType.NONE

    override fun onStart(context: GameHostContext) {
        context.broadcastGameEvent(
            GameEventPayload(
                gameType = gameType,
                event = "FREE_DJ_ACTIVE",
                extraData = "Free DJ Mode: Enjoy the synchronized music and pass control around!"
            )
        )
    }

    override fun onTick(nowNs: Long, context: GameHostContext) {}

    override fun onMessage(senderDeviceId: String, envelope: Envelope, context: GameHostContext) {}

    override fun onEnd(context: GameHostContext) {}
}
