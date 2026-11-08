package com.local.voicelink

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TapService : AccessibilityService() {
    private val running = AtomicBoolean(false)
    private val worker = HandlerThread("VoiceLinkSequence").apply { start() }
    private val handler = Handler(worker.looper)
    @Volatile private var generation = 0
    private var stopping = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        log("Accessibility service connected and ready")
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { cancel(); log("Accessibility interrupted; sequence cancelled") }
    override fun onDestroy() {
        cancel()
        if (instance === this) instance = null
        worker.quitSafely()
        super.onDestroy()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            TriggerReceiver.ACTION_START -> armStart()
            TriggerReceiver.ACTION_STOP -> armStop()
        }
        return START_NOT_STICKY
    }
    private fun cancel() {
        generation++
        handler.removeCallbacksAndMessages(null)
        running.set(false)
        stopping = false
    }

    fun armStart(snapshot: SettingsSnapshot = Settings(this).snapshot(), launch: () -> Boolean = { true }) {
        handler.post {
            if (stopping || !running.compareAndSet(false, true)) {
                log("START ignored: sequence already running")
                return@post
            }
            val token = ++generation
            val started = SystemClock.elapsedRealtime()
            log("ARMED: ${snapshot.targetPackage}")
            if (!runCatching(launch).getOrDefault(false)) {
                running.set(false)
                log("FAILED: target activity could not be launched")
                return@post
            }
            val deadline = started + (snapshot.postLaunchDelayMs + snapshot.maxAttempts * snapshot.pollIntervalMs +
                snapshot.gateGraceMs).toLong().coerceIn(4000L, 30000L)
            later(token, snapshot.postLaunchDelayMs.toLong()) {
                resolve(snapshot, token, started, deadline, 1, false)
            }
        }
    }

    private fun later(token: Int, delay: Long, action: () -> Unit) {
        handler.postDelayed({
            if (generation == token && running.get()) {
                try { action() } catch (e: Exception) {
                    log("FAILED: ${e.javaClass.simpleName}: ${e.message}")
                    running.set(false)
                }
            }
        }, delay)
    }

    private fun resolve(s: SettingsSnapshot, token: Int, started: Long, deadline: Long, attempt: Int, coordinateUsed: Boolean) {
        if (NodeWalker.voiceActive(this, s.targetPackage)) { done(started, "already active"); return }
        if (SystemClock.elapsedRealtime() >= deadline || attempt > s.maxAttempts) {
            log("FAILED: voice mode not found/confirmed; check ChatGPT login, microphone permission and voice availability")
            running.set(false)
            return
        }
        var result = NodeWalker.tryVoice(this, s.targetPackage)
        var used = coordinateUsed
        if (!result.success && s.useCoordinateFallback && !used && attempt >= minOf(4, s.maxAttempts)) {
            result = NodeWalker.tryCoordinate(this, s.targetPackage, s.coordXPercent, s.coordYPercent)
            used = result.success
        }
        if (s.verboseLogging) log("RESOLVING $attempt/${s.maxAttempts}: ${result.tier}: ${result.detail}; accepted=${result.success}")
        if (result.success) {
            log("GATE_CHECK: tap accepted via ${result.tier}; waiting for voice UI")
            verify(s, token, started, deadline, attempt, used, result.tier,
                SystemClock.elapsedRealtime() + s.gateGraceMs.toLong().coerceAtLeast(1500), false)
        } else later(token, s.pollIntervalMs.toLong()) { resolve(s, token, started, deadline, attempt + 1, used) }
    }

    private fun verify(s: SettingsSnapshot, token: Int, started: Long, deadline: Long, attempt: Int,
                       used: Boolean, tier: String, gateDeadline: Long, gateTapped: Boolean) {
        later(token, s.pollIntervalMs.toLong()) {
            if (NodeWalker.voiceActive(this, s.targetPackage)) { done(started, tier); return@later }
            val now = SystemClock.elapsedRealtime()
            if (now >= deadline) {
                log("FAILED: tap accepted but voice mode did not appear; no success reported")
                running.set(false)
                return@later
            }
            if (now >= gateDeadline) {
                // A transition can consume a tap; allow semantic retries, but never repeat a blind coordinate.
                resolve(s, token, started, deadline, attempt + 1, used)
                return@later
            }
            val tapped = if (!gateTapped) NodeWalker.tryGate(this, s.targetPackage).success else true
            if (tapped && !gateTapped) log("Optional voice onboarding control tapped")
            verify(s, token, started, deadline, attempt, used, tier, gateDeadline, tapped)
        }
    }

    private fun done(started: Long, tier: String) {
        log("DONE: voice UI confirmed in ${SystemClock.elapsedRealtime() - started}ms via $tier (connection/microphone remain managed by ChatGPT)")
        running.set(false)
    }

    fun armStop(snapshot: SettingsSnapshot = Settings(this).snapshot()) {
        handler.post {
            cancel()
            stopping = true
            val token = generation
            log("STOP: cancelling sequence")
            if (NodeWalker.targetRoot(this, snapshot.targetPackage) == null) {
                try {
                    packageManager.getLaunchIntentForPackage(snapshot.targetPackage)?.let {
                        startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } catch (e: Exception) { log("Cannot foreground target for close: ${e.message}") }
                handler.postDelayed({ close(snapshot, token, 0) }, 700)
            } else close(snapshot, token, 0)
        }
    }
    private fun close(s: SettingsSnapshot, token: Int, count: Int) {
        if (token != generation) return
        if (count == 0 && NodeWalker.tryEndVoice(this, s.targetPackage)) {
            log("End voice control tapped")
            handler.postDelayed({ close(s, token, 1) }, 350)
            return
        }
        if (s.closeWithBackGestures && count < s.closeGestureCount && NodeWalker.targetRoot(this, s.targetPackage) != null) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ close(s, token, count + 1) }, 350)
            return
        }
        performGlobalAction(GLOBAL_ACTION_HOME)
        // Unprivileged Android apps cannot force-stop another package or remove its task.
        if (s.forceStopAfterClose) log("Target sent Home. Force-stop/removal from recents requires system privileges; unavailable to this app.")
        else log("Target sent Home")
        stopping = false
    }

    companion object {
        @Volatile var instance: TapService? = null
            private set
        private val ringBuffer = ArrayDeque<String>(200)
        fun log(msg: String) {
            synchronized(ringBuffer) {
                val line = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} $msg"
                Log.d("VoiceLink", line)
                if (ringBuffer.size >= 200) ringBuffer.removeFirst()
                ringBuffer.addLast(line)
            }
        }
        fun getLogLines(): List<String> = synchronized(ringBuffer) { ringBuffer.toList() }
        fun clearLog() = synchronized(ringBuffer) { ringBuffer.clear() }
    }
}
