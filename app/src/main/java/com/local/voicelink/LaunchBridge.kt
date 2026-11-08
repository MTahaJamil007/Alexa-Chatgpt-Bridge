package com.local.voicelink

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.widget.Toast

object LaunchBridge {
    private val main = Handler(Looper.getMainLooper())
    fun startTarget(context: Context, settings: Settings) = startTarget(context, settings.snapshot())
    fun startTarget(context: Context, snapshot: SettingsSnapshot) = startTarget(context, snapshot, 10)
    private fun startTarget(context: Context, snapshot: SettingsSnapshot, retries: Int) {
        val app = context.applicationContext
        main.post {
            val svc = TapService.instance
            if (svc == null) {
                val enabled = AndroidSettings.Secure.getString(app.contentResolver,
                    AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                if (retries > 0 && enabled.split(':').any { it.startsWith("${app.packageName}/") }) {
                    main.postDelayed({ startTarget(app, snapshot, retries - 1) }, 200)
                    return@post
                }
                TapService.log("FAILED: accessibility service is not connected; enable VoiceLink in Accessibility settings")
                Toast.makeText(app, "Enable the VoiceLink accessibility service first", Toast.LENGTH_LONG).show()
                return@post
            }
            svc.armStart(snapshot) {
                // Overlay permission permits background activity launch; Android still controls the transition.
                if (!AndroidSettings.canDrawOverlays(app)) TapService.log("Overlay permission missing; Android may suppress background launch")
                val intent = app.packageManager.getLaunchIntentForPackage(snapshot.targetPackage)
                if (intent == null) {
                    TapService.log("FAILED: target not installed: ${snapshot.targetPackage}")
                    main.post { Toast.makeText(app, R.string.toast_target_not_installed, Toast.LENGTH_LONG).show() }
                    false
                } else try {
                    app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    TapService.log("Target launch requested; waiting for its foreground window")
                    true
                } catch (e: Exception) {
                    TapService.log("FAILED: launch rejected: ${e.message}")
                    false
                }
            }
        }
    }
    fun closeTarget(context: Context, settings: Settings) = closeTarget(context, settings.snapshot())
    fun closeTarget(context: Context, snapshot: SettingsSnapshot) {
        main.post {
            val svc = TapService.instance
            if (svc != null) svc.armStop(snapshot)
            else TapService.log("FAILED: accessibility service is not connected; cannot close target (${context.packageName})")
        }
    }
}
