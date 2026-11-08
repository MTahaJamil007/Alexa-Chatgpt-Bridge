package com.local.voicelink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val settings = Settings(context)

        when (action) {
            ACTION_START -> {
                TapService.log("TriggerReceiver: START broadcast received")
                LaunchBridge.startTarget(context.applicationContext, settings)
            }
            ACTION_STOP -> {
                TapService.log("TriggerReceiver: STOP broadcast received")
                LaunchBridge.closeTarget(context.applicationContext, settings)
            }
            else -> TapService.log("TriggerReceiver: unknown action $action")
        }
    }

    companion object {
        const val ACTION_START = "com.local.voicelink.START"
        const val ACTION_STOP = "com.local.voicelink.STOP"
    }
}
