package com.spyou.eramusic.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Counts [remainingMs] down to zero and then invokes the supplied callback once.
 * Logic is a plain coroutine loop so it can be tested with a virtual-time dispatcher.
 */
class SleepTimer(private val scope: CoroutineScope) {

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

    val isRunning: Boolean get() = job?.isActive == true

    private var job: Job? = null

    fun start(durationMs: Long, onFire: () -> Unit) {
        cancel()
        if (durationMs <= 0) return
        _remainingMs.value = durationMs
        job = scope.launch {
            val tick = 1000L
            while (isActive && _remainingMs.value > 0) {
                delay(tick)
                _remainingMs.value = (_remainingMs.value - tick).coerceAtLeast(0)
            }
            if (isActive) {
                onFire()
                _remainingMs.value = 0
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _remainingMs.value = 0
    }
}
