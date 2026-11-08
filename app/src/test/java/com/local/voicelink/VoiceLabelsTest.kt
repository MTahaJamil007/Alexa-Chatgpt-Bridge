package com.local.voicelink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLabelsTest {
    @Test fun recognizesVoiceEntryLabelsUsedByComposeControls() {
        listOf("Start a voice conversation", "Start a voice chat", "Voice", "Start voice mode", "Start voice chat", "Open voice mode", "Switch to voice mode",
            "  START   VOICE MODE  ").forEach { assertTrue(it, VoiceLabels.isVoice(it)) }
    }
    @Test fun doesNotConfuseDictationOrConversationTextWithVoiceControls() {
        listOf("Dictate", "Microphone", "mic_button", "Send message", "Tell me about voice mode",
            "Stop voice chat", "End voice chat").forEach { assertFalse(it, VoiceLabels.isVoice(it)) }
    }
    @Test fun gateDoesNotMatchOriginalVoiceButton() {
        assertFalse(VoiceLabels.isGate("Voice mode"))
        assertFalse(VoiceLabels.isGate("Voice"))
        assertTrue(VoiceLabels.isGate("Continue"))
        assertTrue(VoiceLabels.isGate("Start voice"))
    }
    @Test fun requiresVoiceSessionControlsForConfirmation() {
        listOf("Turn microphone off", "Turn microphone on", "End voice chat", "Mute microphone", "Unmute microphone", "End conversation")
            .forEach { assertTrue(it, VoiceLabels.isActive(it)) }
        listOf("Start voice mode", "Voice mode", "Continue", "Connecting…", "Microphone", "")
            .forEach { assertFalse(it, VoiceLabels.isActive(it)) }
    }
    @Test fun nullLabelsAreSafe() {
        assertFalse(VoiceLabels.isActive(null))
        assertFalse(VoiceLabels.isVoice(null))
        assertFalse(VoiceLabels.isGate(null))
    }
}
