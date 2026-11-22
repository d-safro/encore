package dev.safro.encore.wear.playback

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VolumeLevel(val current: Int, val max: Int) {
    val fraction get() = if (max <= 0) 0f else current.toFloat() / max
}

/** The watch's media volume (headphones over Bluetooth take it as their own), turned with the crown. */
class Volume(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val _level = MutableStateFlow(read())
    val level: StateFlow<VolumeLevel> = _level.asStateFlow()

    private fun read() = VolumeLevel(am.getStreamVolume(AudioManager.STREAM_MUSIC), am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))

    fun refresh() { _level.value = read() }

    fun change(steps: Int) {
        if (steps == 0) return
        val dir = if (steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(kotlin.math.abs(steps)) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0) }
        refresh()
    }
}

/**
 * Turns crown scrolling (pixels) into volume steps: each [pxPerStep] of turning is one step, and what's left
 * over carries on to the next turn, so slow turning still moves it.
 */
class CrownSteps(private val pxPerStep: Float = 48f) {
    private var carry = 0f

    fun add(deltaPx: Float): Int {
        carry += deltaPx
        val steps = (carry / pxPerStep).toInt()
        carry -= steps * pxPerStep
        return steps
    }
}
