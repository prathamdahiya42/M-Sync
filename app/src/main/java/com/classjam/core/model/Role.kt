package com.classjam.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    ADMIN,
    DJ,
    PLAYER
}
