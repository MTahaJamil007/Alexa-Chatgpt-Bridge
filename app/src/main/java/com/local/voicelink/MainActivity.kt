package com.local.voicelink

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.local.voicelink.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isUpdatingUI = false

    private val logRefreshRunnable = object : Runnable {
        override fun run() {
            refreshPermissions()
            refreshLog()
            mainHandler.postDelayed(this, 500)
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = Settings(this)

        setupPermissionButtons()
        setupActionButtons()
        setupSettingsBindings()
        setupLogSection()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        loadSettingsIntoUI()
        mainHandler.post(logRefreshRunnable)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(logRefreshRunnable)
        super.onPause()
    }

    // ── Permission Management ──────────────────────────────────────────

    private fun setupPermissionButtons() {
        binding.btnFixOverlay.setOnClickListener {
            startActivity(
                Intent(
                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.btnFixAccessibility.setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnFixNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun refreshPermissions() {
        val overlayOk = AndroidSettings.canDrawOverlays(this)
        val accessOk = isAccessibilityEnabled()
        val notifOk = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        binding.statusOverlay.text = if (overlayOk) getString(R.string.status_granted) else getString(R.string.status_denied)
        binding.statusAccessibility.text = if (accessOk) getString(R.string.status_granted) else getString(R.string.status_denied)
        binding.statusNotifications.text = if (notifOk) getString(R.string.status_granted) else getString(R.string.status_denied)

        val ready = overlayOk && accessOk
        binding.btnRun.isEnabled = ready
        binding.btnClose.isEnabled = ready
        binding.txtHint.visibility = if (ready) View.GONE else View.VISIBLE
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return TapService.instance != null && enabled.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    // ── Action Buttons ─────────────────────────────────────────────────

    private fun setupActionButtons() {
        binding.btnRun.setOnClickListener {
            saveSettingsFromUI()
            LaunchBridge.startTarget(applicationContext, settings)
        }
        binding.btnClose.setOnClickListener {
            saveSettingsFromUI()
            LaunchBridge.closeTarget(applicationContext, settings)
        }
        binding.btnCopyIntent.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("intent_action", TriggerReceiver.ACTION_START))
            Toast.makeText(this, getString(R.string.toast_intent_copied), Toast.LENGTH_LONG).show()
        }
    }

    // ── Settings Two-Way Bindings ──────────────────────────────────────

    private fun loadSettingsIntoUI() {
        isUpdatingUI = true
        binding.editTargetPackage.setTextIfDifferent(settings.targetPackage)
        binding.editPostLaunchDelay.setTextIfDifferent(settings.postLaunchDelayMs.toString())
        binding.editPollInterval.setTextIfDifferent(settings.pollIntervalMs.toString())
        binding.editMaxAttempts.setTextIfDifferent(settings.maxAttempts.toString())
        binding.editGateGrace.setTextIfDifferent(settings.gateGraceMs.toString())
        binding.editCoordX.setTextIfDifferent(settings.coordXPercent.toString())
        binding.editCoordY.setTextIfDifferent(settings.coordYPercent.toString())
        binding.editCloseGestureCount.setTextIfDifferent(settings.closeGestureCount.toString())

        binding.switchCoordFallback.isChecked = settings.useCoordinateFallback
        binding.switchCloseBackGestures.isChecked = settings.closeWithBackGestures
        binding.switchForceStop.isChecked = settings.forceStopAfterClose
        binding.switchVerbose.isChecked = settings.verboseLogging
        isUpdatingUI = false
    }

    private fun setupSettingsBindings() {
        binding.editTargetPackage.afterTextChanged { if (!isUpdatingUI) settings.targetPackage = it }
        binding.editPostLaunchDelay.afterTextChangedInt { if (!isUpdatingUI) settings.postLaunchDelayMs = it }
        binding.editPollInterval.afterTextChangedInt { if (!isUpdatingUI) settings.pollIntervalMs = it }
        binding.editMaxAttempts.afterTextChangedInt { if (!isUpdatingUI) settings.maxAttempts = it }
        binding.editGateGrace.afterTextChangedInt { if (!isUpdatingUI) settings.gateGraceMs = it }
        binding.editCoordX.afterTextChangedInt { if (!isUpdatingUI) settings.coordXPercent = it }
        binding.editCoordY.afterTextChangedInt { if (!isUpdatingUI) settings.coordYPercent = it }
        binding.editCloseGestureCount.afterTextChangedInt { if (!isUpdatingUI) settings.closeGestureCount = it }

        binding.switchCoordFallback.onChecked { if (!isUpdatingUI) settings.useCoordinateFallback = it }
        binding.switchCloseBackGestures.onChecked { if (!isUpdatingUI) settings.closeWithBackGestures = it }
        binding.switchForceStop.onChecked { if (!isUpdatingUI) settings.forceStopAfterClose = it }
        binding.switchVerbose.onChecked { if (!isUpdatingUI) settings.verboseLogging = it }
    }

    private fun saveSettingsFromUI() {
        settings.targetPackage = binding.editTargetPackage.text.toString()
        binding.editPostLaunchDelay.text.toString().toIntOrNull()?.let { settings.postLaunchDelayMs = it }
        binding.editPollInterval.text.toString().toIntOrNull()?.let { settings.pollIntervalMs = it }
        binding.editMaxAttempts.text.toString().toIntOrNull()?.let { settings.maxAttempts = it }
        binding.editGateGrace.text.toString().toIntOrNull()?.let { settings.gateGraceMs = it }
        binding.editCoordX.text.toString().toIntOrNull()?.let { settings.coordXPercent = it }
        binding.editCoordY.text.toString().toIntOrNull()?.let { settings.coordYPercent = it }
        binding.editCloseGestureCount.text.toString().toIntOrNull()?.let { settings.closeGestureCount = it }
        settings.useCoordinateFallback = binding.switchCoordFallback.isChecked
        settings.closeWithBackGestures = binding.switchCloseBackGestures.isChecked
        settings.forceStopAfterClose = binding.switchForceStop.isChecked
        settings.verboseLogging = binding.switchVerbose.isChecked
    }

    // ── Log Section ────────────────────────────────────────────────────

    private fun setupLogSection() {
        binding.btnClearLog.setOnClickListener {
            TapService.clearLog()
            binding.txtLog.text = ""
        }
    }

    private fun refreshLog() {
        val lines = TapService.getLogLines()
        val formatted = lines.joinToString("\n")
        if (binding.txtLog.text != formatted) {
            binding.txtLog.text = formatted
            binding.logScrollView.post { binding.logScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun EditText.setTextIfDifferent(value: String) {
        if (text.toString() != value) setText(value)
    }

    private fun EditText.afterTextChanged(action: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { action(s?.toString()?.trim() ?: "") }
        })
    }

    private fun EditText.afterTextChangedInt(action: (Int) -> Unit) {
        afterTextChanged { s -> s.toIntOrNull()?.let(action) }
    }

    private fun CompoundButton.onChecked(action: (Boolean) -> Unit) {
        setOnCheckedChangeListener { _, checked -> action(checked) }
    }
}

