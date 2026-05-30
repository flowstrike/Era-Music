package com.spyou.eramusic.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    @Test
    fun firesAfterDurationAndResetsRemaining() = runTest {
        var fired = 0
        val timer = SleepTimer(this)
        timer.start(3000L) { fired++ }

        advanceTimeBy(2000)
        assertEquals(0, fired)

        advanceUntilIdle()
        assertEquals(1, fired)
        assertEquals(0L, timer.remainingMs.value)
    }

    @Test
    fun cancelStopsTimerBeforeFiring() = runTest {
        var fired = 0
        val timer = SleepTimer(this)
        timer.start(3000L) { fired++ }

        advanceTimeBy(1000)
        timer.cancel()
        advanceUntilIdle()

        assertEquals(0, fired)
        assertEquals(0L, timer.remainingMs.value)
    }

    @Test
    fun zeroDurationDoesNotFire() = runTest {
        var fired = 0
        val timer = SleepTimer(this)
        timer.start(0L) { fired++ }
        advanceUntilIdle()
        assertEquals(0, fired)
    }
}
