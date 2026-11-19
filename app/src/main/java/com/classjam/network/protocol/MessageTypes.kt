package com.classjam.network.protocol

object MessageTypes {
    // Session & Membership
    const val JOIN = "JOIN"
    const val WELCOME = "WELCOME"
    const val LEAVE = "LEAVE"
    const val KICK = "KICK"
    const val ERROR = "ERROR"
    const val DEVICE_LIST = "DEVICE_LIST"
    const val ROLE_UPDATE = "ROLE_UPDATE"
    const val GRANT_DJ = "GRANT_DJ"
    const val REVOKE_DJ = "REVOKE_DJ"

    // Time Sync
    const val PING = "PING"
    const val PONG = "PONG"

    // Music Distribution & Sync
    const val LIBRARY_LIST = "LIBRARY_LIST"
    const val QUEUE_ADD = "QUEUE_ADD"
    const val MANIFEST = "MANIFEST"
    const val READY = "READY"
    const val PLAY_AT = "PLAY_AT"
    const val PAUSE = "PAUSE"
    const val SEEK = "SEEK"
    const val SKIP = "SKIP"
    const val MASTER_VOLUME = "MASTER_VOLUME"
    const val OUTPUT_GROUP = "OUTPUT_GROUP"
    const val SYNC_STATE = "SYNC_STATE"

    // Games
    const val GAME_START = "GAME_START"
    const val GAME_EVENT = "GAME_EVENT"
    const val GAME_END = "GAME_END"
    const val GAME_SCOREBOARD = "GAME_SCOREBOARD"
}
