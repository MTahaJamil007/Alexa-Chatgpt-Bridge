package com.local.voicelink

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

object NodeWalker {
    data class TapResult(val success: Boolean, val tier: String, val detail: String)
    private val ids = listOf("voice_button", "voice_mode_button", "composer_voice", "voice_mode", "voice_chat_button")

    fun targetRoot(service: AccessibilityService, pkg: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return root.takeIf { it.packageName?.toString() == pkg }
    }

    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        val result = ArrayList<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty() && result.size < 2500) {
            val node = pending.removeFirst()
            if (node.isVisibleToUser) result.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { pending.add(it) }
        }
        return result
    }

    fun voiceActive(service: AccessibilityService, pkg: String): Boolean =
        targetRoot(service, pkg)?.let { root -> nodes(root).any {
            VoiceLabels.isActive(it.contentDescription) || VoiceLabels.isActive(it.text)
        } } ?: false

    fun tryVoice(service: AccessibilityService, pkg: String): TapResult {
        // Re-read and package-check every tier; never act on VoiceLink or the keyboard.
        targetRoot(service, pkg)?.let { root ->
            for (id in ids) {
                val matches = runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }.getOrNull().orEmpty()
                for (node in matches) if (tapNode(service, node)) return TapResult(true, "view id", id)
            }
        }
        for (description in listOf(false, true)) {
            val root = targetRoot(service, pkg) ?: continue
            for (node in nodes(root)) {
                val label = if (description) node.contentDescription else node.text
                if (VoiceLabels.isVoice(label) && tapNode(service, node))
                    return TapResult(true, if (description) "description" else "text", label.toString())
            }
        }
        return TapResult(false, "selectors", "no visible voice control in target window")
    }

    fun tryEndVoice(service: AccessibilityService, pkg: String): Boolean {
        if (!voiceActive(service, pkg)) return false
        val root = targetRoot(service, pkg) ?: return false
        return nodes(root).any {
            val label = (it.contentDescription ?: it.text)?.toString()?.trim()?.lowercase()
            label in setOf("end", "end voice chat", "end voice mode", "end conversation", "exit voice mode", "close voice mode") &&
                tapNode(service, it)
        }
    }

    fun tryGate(service: AccessibilityService, pkg: String): TapResult {
        val root = targetRoot(service, pkg) ?: return TapResult(false, "gate", "target not foreground")
        val visible = nodes(root)
        // Do not tap the composer voice button again while waiting for its transition.
        val composer = visible.any { it.isEditable }
        if (!composer) for (node in visible) {
            if ((VoiceLabels.isGate(node.text) || VoiceLabels.isGate(node.contentDescription)) && tapNode(service, node))
                return TapResult(true, "gate", "${node.text ?: node.contentDescription}")
        }
        return TapResult(false, "gate", "no onboarding control")
    }

    fun tryCoordinate(service: AccessibilityService, pkg: String, xPct: Int, yPct: Int): TapResult {
        val root = targetRoot(service, pkg) ?: return TapResult(false, "coordinate", "target not foreground")
        val bounds = Rect().also { root.getBoundsInScreen(it) }
        val visible = nodes(root)
        if (visible.any { it.text?.toString()?.trim()?.lowercase() in setOf("log in", "sign in", "sign up") })
            return TapResult(false, "coordinate", "sign in to ChatGPT first")
        val editor = visible.firstOrNull { it.isEditable && it.isEnabled }
        if (editor != null) {
            val field = Rect().also { editor.getBoundsInScreen(it) }
            // The video shows the voice button at the right end of the composer, above the IME.
            val buttons = visible.filter { node ->
                val r = Rect().also { node.getBoundsInScreen(it) }
                node.isClickable && node.isEnabled && !r.isEmpty && r.width() <= bounds.width() / 4 &&
                    r.centerX() > bounds.left + bounds.width() * 0.85 &&
                    kotlin.math.abs(r.centerY() - field.centerY()) < field.height().coerceAtLeast(48)
            }.sortedByDescending { val r = Rect(); it.getBoundsInScreen(r); r.centerX() }
            buttons.firstOrNull()?.let {
                if (tapNode(service, it)) return TapResult(true, "composer button", "rightmost control beside message field")
            }
            if (dispatchTap(service, bounds.left + bounds.width() * 0.94f, field.exactCenterY()))
                return TapResult(true, "composer coordinate", "94% across target, message field centre")
        }
        // Exclude the IME from the fallback area; percentages are never applied to the keyboard.
        service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }.forEach {
            val ime = Rect().also { r -> it.getBoundsInScreen(r) }
            if (ime.top > bounds.top && ime.top < bounds.bottom) bounds.bottom = ime.top
        }
        if (bounds.isEmpty) return TapResult(false, "coordinate", "empty target bounds")
        val x = (bounds.left + bounds.width() * xPct / 100f).coerceIn(bounds.left.toFloat(), (bounds.right - 1).toFloat())
        val y = (bounds.top + bounds.height() * yPct / 100f).coerceIn(bounds.top.toFloat(), (bounds.bottom - 1).toFloat())
        return TapResult(dispatchTap(service, x, y), "coordinate", "$xPct%, $yPct% of target above keyboard")
    }

    private fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        repeat(8) {
            val current = parent ?: return@repeat
            if (current.isClickable && current.isEnabled && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = current.parent
        }
        val r = Rect().also { node.getBoundsInScreen(it) }
        return !r.isEmpty && dispatchTap(service, r.exactCenterX(), r.exactCenterY())
    }

    private fun dispatchTap(service: AccessibilityService, x: Float, y: Float): Boolean {
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, 0, 60)).build()
        return service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                TapService.log("Gesture cancelled; voice state still requires confirmation")
            }
        }, Handler(Looper.getMainLooper()))
    }
}
