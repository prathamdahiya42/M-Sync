package com.classjam.sync

import org.junit.Assert.*
import org.junit.Test

class ClockSyncEngineTest {

    @Test
    fun testClockOffsetCalculationWithIdealSymmetricNetwork() {
        // Simulated: 20 samples where RTT is 10ms (10,000,000 ns)
        // Client t0 = 100_000_000 ns
        // Server t1 = 205_000_000 ns (Host is ahead by exactly +100ms = 100_000_000 ns)
        // Client t2 = 110_000_000 ns
        // offset = t1 - (t0 + t2)/2 = 205_000_000 - 105_000_000 = 100_000_000 ns (+100 ms)
        val samples = (1..20).map { i ->
            ClockSample(
                rttNs = 10_000_000L + (i * 100_000L), // RTT 10ms - 12ms
                offsetNs = 100_000_000L // Constant 100ms offset
            )
        }

        val estimatedOffset = ClockSyncEngine.computeMedianOffset(samples, topN = 5)
        assertEquals(100_000_000L, estimatedOffset)
    }

    @Test
    fun testFilteringOutliersAndJitter() {
        // 15 samples with low RTT and true offset of 50ms (50,000,000 ns)
        val goodSamples = (1..15).map {
            ClockSample(
                rttNs = 8_000_000L + (it * 50_000L),
                offsetNs = 50_000_000L
            )
        }

        // 5 bad samples with large RTT delays (lag spikes) and distorted offsets
        val badSamples = (1..5).map {
            ClockSample(
                rttNs = 150_000_000L + (it * 10_000_000L),
                offsetNs = 95_000_000L // Distorted offset due to asymmetric lag
            )
        }

        val allSamples = goodSamples + badSamples
        val estimatedOffset = ClockSyncEngine.computeMedianOffset(allSamples, topN = 5)

        // The top 5 lowest RTT will strictly pick from goodSamples, filtering out all lag spikes
        assertEquals(50_000_000L, estimatedOffset)
    }

    @Test
    fun testNegativeOffsetWhenClientClockIsAhead() {
        // If Client local time is ahead of Host, offset must be negative
        val samples = (1..10).map {
            ClockSample(
                rttNs = 6_000_000L,
                offsetNs = -35_000_000L // -35ms
            )
        }

        val estimatedOffset = ClockSyncEngine.computeMedianOffset(samples, topN = 5)
        assertEquals(-35_000_000L, estimatedOffset)
    }
}
