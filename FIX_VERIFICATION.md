# VoiceLink repair and verification

The supplied video shows ChatGPT opening with its keyboard visible. The old sequence spent a full retry budget on each obsolete selector tier and eventually used coordinates that could land inside the keyboard. It also treated an accepted tap as completed voice entry.

The repaired sequence checks the supported selectors on every poll, recognizes the current ChatGPT labels, scopes all taps to the target package, uses a composer-relative fallback above the keyboard, and confirms voice-session controls before logging DONE. Polling and cancellation run on a background HandlerThread without sleeps. STOP cancels pending work, taps the session's End control, and returns Home. Repeated START requests are ignored before another launch. An enabled service gets a bounded reconnection wait.

## Files changed or added

```text
app/src/main/java/com/local/voicelink/
  LaunchBridge.kt
  MainActivity.kt
  NodeWalker.kt
  Settings.kt
  TapService.kt
  VoiceLabels.kt                   (new)
app/src/test/java/com/local/voicelink/
  VoiceLabelsTest.kt                (new)
```

No app dependencies, SDK levels, Gradle/AGP versions or manifest permissions were added or changed.

## Verification on November 7, 2026

Emulator: running AVD Pixel_10_Pro, Android image android-37.2; ChatGPT version 1.2026.300. The user completed sign-in. ChatGPT's initial microphone prompt was granted manually during testing. Account/network/voice availability and first-use permission remain ChatGPT prerequisites; the utility never grants another app's permissions itself.

- `gradlew.bat assembleDebug testDebugUnitTest lintDebug --offline --no-configuration-cache`: successful.
- Five regression tests passed, zero failures/errors.
- Lint: zero errors, 65 warnings (including existing hardcoded layout strings, target SDK age, and layout advice); no missing-resource errors.
- Cold ChatGPT process launch: voice UI confirmed in 7,753 ms.
- Repeated START: second request ignored, one run completed in 4,523 ms.
- Keyboard confirmed visible using input-method diagnostics, followed by rotation during launch: voice UI confirmed in 4,316 ms; no service crash.
- Manual Run sequence button: voice UI confirmed in 4,166 ms.
- START followed immediately by STOP during the launch delay: cancelled with no later resolving/tap/DONE from that run.
- Overlay revoked: warning logged and target voice UI confirmed in 5,187 ms; overlay restored afterward.
- Final delivered APK, including background worker and service-reconnection wait: voice UI confirmed in 4,577 ms.
- STOP: End voice control tapped, launcher/Home confirmed foreground.
- Missing/disconnected service: clean FAILED observed without crash. Missing-package handling is implemented but not separately exercised by removing ChatGPT.

Evidence: `app/build/reports/voicelink-emulator.log`, `app/build/reports/voicelink-verified.png`, test XML, and Android lint report.

## Install and run

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am broadcast -a com.local.voicelink.START -n com.local.voicelink/.TriggerReceiver
adb shell am broadcast -a com.local.voicelink.STOP -n com.local.voicelink/.TriggerReceiver
adb logcat -s VoiceLink:D
```

Enable VoiceLink's overlay and accessibility permissions after installation. Enable ChatGPT microphone access on its first voice launch. The final APK is already installed on the tested emulator.

## Platform limitation and future changes

The original requirement to force-stop ChatGPT and remove its task from recents cannot be guaranteed by an ordinary, non-root app with the requested permissions. The old shell command lacked system privileges and its background-process fallback could not provide that behavior. The repair reports this honestly and ends voice mode/returns Home instead. Android 14+ restricts killBackgroundProcesses to the caller's own processes: https://developer.android.com/about/versions/14/behavior-changes-all#kill-background-processes

The specification's per-tier full retry budgets conflicted with its short launch time; this repair prioritizes responsive voice entry by trying semantic tiers together each poll. DONE confirms the visible voice UI, not server connectivity or successful audio exchange.

Future ChatGPT changes to accessibility labels, localization, the voice composer's position, onboarding screens, or session controls can require selector updates. Coordinate fallback is best effort. Log in, allow microphone access, and ensure voice is available on the account before running.
