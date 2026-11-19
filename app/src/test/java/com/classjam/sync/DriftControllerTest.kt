package com.classjam.sync

import org.junit.Assert.*
import org.junit.Test

class DriftControllerTest {

    @Test
    fun testDriftUnder20MsIgnored() {
        val action1 = DriftController.determineCorrectionAction(driftMs = 0L, expectedPositionMs = 5000L)
        assertEquals(DriftCorrectionState.InSync, action1)

        val action2 = DriftController.determineCorrectionAction(driftMs = 15L, expectedPositionMs = 5000L)
        assertEquals(DriftCorrectionState.InSync, action2)

        val action3 = DriftController.determineCorrectionAction(driftMs = -18L, expectedPositionMs = 5000L)
        assertEquals(DriftCorrectionState.InSync, action3)
    }

    @Test
    fun testDriftBetween20And200MsNudgesSpeed() {
        // Actual is 45ms ahead of expected: slow down to 0.98f
        val actionAhead = DriftController.determineCorrectionAction(driftMs = 45L, expectedPositionMs = 12000L)
        assertTrue(actionAhead is DriftCorrectionState.SpeedNudge)
        val nudgeAhead = actionAhead as DriftCorrectionState.SpeedNudge
        assertEquals(0.98f, nudgeAhead.newSpeed, 0.001f)
        assertEquals(45L, nudgeAhead.driftMs)

        // Actual is 80ms behind expected: speed up to 1.02f
        val actionBehind = DriftController.determineCorrectionAction(driftMs = -80L, expectedPositionMs = 12000L)
        assertTrue(actionBehind is DriftCorrectionState.SpeedNudge)
        val nudgeBehind = actionBehind as DriftCorrectionState.SpeedNudge
        assertEquals(1.02f, nudgeBehind.newSpeed, 0.001f)
        assertEquals(-80L, nudgeBehind.driftMs)
    }

    @Test
    fun testDriftOver200MsTriggersHardSeek() {
        // Desync of +350ms: Hard seek to expected position
        val actionOverAhead = DriftController.determineCorrectionAction(driftMs = 350L, expectedPositionMs = 25000L)
        assertTrue(actionOverAhead is DriftCorrectionState.HardSeek)
        val seekAhead = actionOverAhead as DriftCorrectionState.HardSeek
        assertEquals(25000L, seekAhead.targetPositionMs)
        assertEquals(350L, seekAhead.driftMs)

        // Desync of -400ms: Hard seek
        val actionOverBehind = DriftController.determineCorrectionAction(driftMs = -400L, expectedPositionMs = 25000L)
        assertTrue(actionOverBehind is DriftCorrectionState.HardSeek)
        val seekBehind = actionOverBehind as DriftCorrectionState.HardSeek
        assertEquals(25000L, seekBehind.targetPositionMs)
        assertEquals(-400L, seekBehind.driftMs)
    }
}
