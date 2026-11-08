package com.local.voicelink

import android.content.Context
import android.content.SharedPreferences

/**
 * Snapshot of settings passed to background tasks without holding references
 * to context or shared preferences.
 */
data class SettingsSnapshot(
    val targetPackage: String,
    val postLaunchDelayMs: Int,
    val pollIntervalMs: Int,
    val maxAttempts: Int,
    val gateGraceMs: Int,
    val coordXPercent: Int,
    val coordYPercent: Int,
    val useCoordinateFallback: Boolean,
    val closeWithBackGestures: Boolean,
    val closeGestureCount: Int,
    val forceStopAfterClose: Boolean,
    val verboseLogging: Boolean
)

/**
 * SharedPreferences wrapper for VoiceLink configuration.
 * Exposes typed getters and setters that persist immediately.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("voicelink_prefs", Context.MODE_PRIVATE)

    var targetPackage: String
        get() = prefs.getString(KEY_TARGET_PACKAGE, DEF_TARGET_PACKAGE) ?: DEF_TARGET_PACKAGE
        set(v) { prefs.edit().putString(KEY_TARGET_PACKAGE, v).apply() }

    var postLaunchDelayMs: Int
        get() = prefs.getInt(KEY_POST_LAUNCH_DELAY, DEF_POST_LAUNCH_DELAY)
        set(v) { prefs.edit().putInt(KEY_POST_LAUNCH_DELAY, v.coerceIn(0, 5000)).apply() }

    var pollIntervalMs: Int
        get() = prefs.getInt(KEY_POLL_INTERVAL, DEF_POLL_INTERVAL)
        set(v) { prefs.edit().putInt(KEY_POLL_INTERVAL, v.coerceIn(100, 1000)).apply() }

    var maxAttempts: Int
        get() = prefs.getInt(KEY_MAX_ATTEMPTS, DEF_MAX_ATTEMPTS)
        set(v) { prefs.edit().putInt(KEY_MAX_ATTEMPTS, v.coerceIn(5, 60)).apply() }

    var gateGraceMs: Int
        get() = prefs.getInt(KEY_GATE_GRACE, DEF_GATE_GRACE).coerceIn(0, 15000)
        set(v) { prefs.edit().putInt(KEY_GATE_GRACE, v.coerceIn(0, 15000)).apply() }

    var coordXPercent: Int
        get() = prefs.getInt(KEY_COORD_X, DEF_COORD_X)
        set(v) { prefs.edit().putInt(KEY_COORD_X, v.coerceIn(0, 100)).apply() }

    var coordYPercent: Int
        get() = prefs.getInt(KEY_COORD_Y, DEF_COORD_Y)
        set(v) { prefs.edit().putInt(KEY_COORD_Y, v.coerceIn(0, 100)).apply() }

    var useCoordinateFallback: Boolean
        get() = prefs.getBoolean(KEY_USE_COORD_FALLBACK, DEF_USE_COORD_FALLBACK)
        set(v) { prefs.edit().putBoolean(KEY_USE_COORD_FALLBACK, v).apply() }

    var closeWithBackGestures: Boolean
        get() = prefs.getBoolean(KEY_CLOSE_BACK_GESTURES, DEF_CLOSE_BACK_GESTURES)
        set(v) { prefs.edit().putBoolean(KEY_CLOSE_BACK_GESTURES, v).apply() }

    var closeGestureCount: Int
        get() = prefs.getInt(KEY_CLOSE_GESTURE_COUNT, DEF_CLOSE_GESTURE_COUNT).coerceIn(0, 10)
        set(v) { prefs.edit().putInt(KEY_CLOSE_GESTURE_COUNT, v.coerceIn(0, 10)).apply() }

    var forceStopAfterClose: Boolean
        get() = prefs.getBoolean(KEY_FORCE_STOP, DEF_FORCE_STOP)
        set(v) { prefs.edit().putBoolean(KEY_FORCE_STOP, v).apply() }

    var verboseLogging: Boolean
        get() = prefs.getBoolean(KEY_VERBOSE, DEF_VERBOSE)
        set(v) { prefs.edit().putBoolean(KEY_VERBOSE, v).apply() }

    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        targetPackage = targetPackage,
        postLaunchDelayMs = postLaunchDelayMs,
        pollIntervalMs = pollIntervalMs,
        maxAttempts = maxAttempts,
        gateGraceMs = gateGraceMs,
        coordXPercent = coordXPercent,
        coordYPercent = coordYPercent,
        useCoordinateFallback = useCoordinateFallback,
        closeWithBackGestures = closeWithBackGestures,
        closeGestureCount = closeGestureCount,
        forceStopAfterClose = forceStopAfterClose,
        verboseLogging = verboseLogging
    )

    companion object {
        const val KEY_TARGET_PACKAGE = "target_package"
        const val KEY_POST_LAUNCH_DELAY = "post_launch_delay_ms"
        const val KEY_POLL_INTERVAL = "poll_interval_ms"
        const val KEY_MAX_ATTEMPTS = "max_attempts"
        const val KEY_GATE_GRACE = "gate_grace_ms"
        const val KEY_COORD_X = "coord_x_percent"
        const val KEY_COORD_Y = "coord_y_percent"
        const val KEY_USE_COORD_FALLBACK = "use_coordinate_fallback"
        const val KEY_CLOSE_BACK_GESTURES = "close_with_back_gestures"
        const val KEY_CLOSE_GESTURE_COUNT = "close_gesture_count"
        const val KEY_FORCE_STOP = "force_stop_after_close"
        const val KEY_VERBOSE = "verbose_logging"

        const val DEF_TARGET_PACKAGE = "com.openai.chatgpt"
        const val DEF_POST_LAUNCH_DELAY = 1500
        const val DEF_POLL_INTERVAL = 250
        const val DEF_MAX_ATTEMPTS = 20
        const val DEF_GATE_GRACE = 4000
        const val DEF_COORD_X = 88
        const val DEF_COORD_Y = 93
        const val DEF_USE_COORD_FALLBACK = true
        const val DEF_CLOSE_BACK_GESTURES = true
        const val DEF_CLOSE_GESTURE_COUNT = 2
        const val DEF_FORCE_STOP = true
        const val DEF_VERBOSE = true
    }
}

