package com.classjam.core

import com.classjam.core.model.Role
import com.classjam.core.model.RolePermissions
import org.junit.Assert.*
import org.junit.Test

class RolePermissionTest {

    @Test
    fun testAdminHasAllPermissions() {
        val role = Role.ADMIN
        assertTrue(RolePermissions.canControlPlayback(role))
        assertTrue(RolePermissions.canManageRoles(role))
        assertTrue(RolePermissions.canKickDevice(role))
        assertTrue(RolePermissions.canChangeOutputGroup(role))
        assertTrue(RolePermissions.canSetMasterVolume(role))
        assertTrue(RolePermissions.canStartOrStopGames(role))
        assertTrue(RolePermissions.canRequestQueue(role))
    }

    @Test
    fun testDjHasQueueAndPlaybackPermissionsOnly() {
        val role = Role.DJ
        assertTrue(RolePermissions.canControlPlayback(role))
        assertTrue(RolePermissions.canRequestQueue(role))

        assertFalse(RolePermissions.canManageRoles(role))
        assertFalse(RolePermissions.canKickDevice(role))
        assertFalse(RolePermissions.canChangeOutputGroup(role))
        assertFalse(RolePermissions.canSetMasterVolume(role))
        assertFalse(RolePermissions.canStartOrStopGames(role))
    }

    @Test
    fun testPlayerHasNoAdminOrDjPermissions() {
        val role = Role.PLAYER
        assertFalse(RolePermissions.canControlPlayback(role))
        assertFalse(RolePermissions.canRequestQueue(role))
        assertFalse(RolePermissions.canManageRoles(role))
        assertFalse(RolePermissions.canKickDevice(role))
        assertFalse(RolePermissions.canChangeOutputGroup(role))
        assertFalse(RolePermissions.canSetMasterVolume(role))
        assertFalse(RolePermissions.canStartOrStopGames(role))
    }
}
