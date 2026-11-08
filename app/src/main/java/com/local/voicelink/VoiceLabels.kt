package com.local.voicelink

internal object VoiceLabels {
    private fun normalize(value: CharSequence?) = value?.toString()?.trim()?.lowercase(java.util.Locale.ROOT)
        ?.replace(Regex("[\\s_-]+"), " ") ?: ""
    fun isVoice(value: CharSequence?): Boolean = normalize(value) in setOf(
        "start a voice conversation", "start a voice chat", "voice", "voice mode", "voice chat", "start voice", "start voice mode", "start voice chat",
        "open voice mode", "enter voice mode", "switch to voice mode", "advanced voice", "start advanced voice mode")
    fun isGate(value: CharSequence?): Boolean = normalize(value) in setOf(
        "start", "start voice", "start voice chat", "continue", "headphones", "let’s go", "let's go")
    fun isActive(value: CharSequence?): Boolean = normalize(value) in setOf(
        "turn microphone off", "turn microphone on", "end voice chat", "end voice mode", "end conversation", "exit voice mode", "close voice mode",
        "mute microphone", "unmute microphone", "mute mic", "unmute mic", "unmute", "mute")
}
