package com.example.recemotion.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

// ─── Haptic ──────────────────────────────────────────────────────────────────

fun View.hapticTick() {
    val constant = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            HapticFeedbackConstants.CLOCK_TICK
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ->
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
        else ->
            HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(constant)
}

fun View.hapticProgress() {
    val constant = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            HapticFeedbackConstants.CONFIRM
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            HapticFeedbackConstants.GESTURE_END
        else ->
            HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(constant)
}

fun View.hapticSuccess() {
    val constant = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            HapticFeedbackConstants.CONFIRM
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            HapticFeedbackConstants.GESTURE_END
        else ->
            HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
    postDelayed({ performHapticFeedback(constant) }, 80L)
}

fun View.hapticError() {
    val constant = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            HapticFeedbackConstants.REJECT
        else ->
            HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
}

// ─── Sound ───────────────────────────────────────────────────────────────────

private data class ToneStep(val tone: Int, val durationMs: Int)

private fun View.playToneSequence(steps: List<ToneStep>, intervalMs: Long) {
    if (steps.isEmpty()) return
    val gen = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME / 2)
    } catch (_: Exception) { return }

    fun play(idx: Int) {
        val step = steps[idx]
        gen.startTone(step.tone, step.durationMs)
        if (idx < steps.lastIndex) {
            postDelayed({ play(idx + 1) }, step.durationMs + intervalMs)
        } else {
            postDelayed({ gen.release() }, (step.durationMs + 50).toLong())
        }
    }
    play(0)
}

fun View.soundProgress() {
    val gen = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME / 2)
    } catch (_: Exception) { return }
    gen.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
    postDelayed({ gen.release() }, 130L)
}

fun View.soundSuccess() {
    playToneSequence(
        steps = listOf(
            ToneStep(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 80),
            ToneStep(ToneGenerator.TONE_PROP_BEEP2, 100),
            ToneStep(ToneGenerator.TONE_PROP_ACK, 150)
        ),
        intervalMs = 110L
    )
}

fun View.soundError() {
    val gen = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME / 2)
    } catch (_: Exception) { return }
    gen.startTone(ToneGenerator.TONE_CDMA_LOW_L, 150)
    postDelayed({ gen.release() }, 200L)
}

// ─── Convenience combos ──────────────────────────────────────────────────────

fun View.feedbackProgress() {
    hapticProgress()
    soundProgress()
}

fun View.feedbackSuccess() {
    hapticSuccess()
    soundSuccess()
}

fun View.feedbackError() {
    hapticError()
    soundError()
}
