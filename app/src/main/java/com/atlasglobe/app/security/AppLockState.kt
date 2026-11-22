package com.atlasglobe.app.security

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** État du verrouillage : se reverrouille si Atlas reste en arrière-plan plus de 15 secondes. */
object AppLockState {
    var unlocked by mutableStateOf(false)
    private var stoppedAt = 0L
    private const val GRACE_MS = 15_000L

    fun onStop(nowMs: Long) { stoppedAt = nowMs }
    fun onStart(nowMs: Long) { if (stoppedAt != 0L && nowMs - stoppedAt > GRACE_MS) unlocked = false }
}
