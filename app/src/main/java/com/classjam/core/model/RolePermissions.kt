package com.classjam.core.model

object RolePermissions {
    fun canControlPlayback(role: Role): Boolean {
        return role == Role.ADMIN || role == Role.DJ
    }

    fun canManageRoles(role: Role): Boolean {
        return role == Role.ADMIN
    }

    fun canKickDevice(role: Role): Boolean {
        return role == Role.ADMIN
    }

    fun canChangeOutputGroup(role: Role): Boolean {
        return role == Role.ADMIN
    }

    fun canSetMasterVolume(role: Role): Boolean {
        return role == Role.ADMIN
    }

    fun canStartOrStopGames(role: Role): Boolean {
        return role == Role.ADMIN
    }

    fun canRequestQueue(role: Role): Boolean {
        return role == Role.ADMIN || role == Role.DJ
    }
}
