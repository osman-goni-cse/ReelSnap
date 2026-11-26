package com.reelsnap.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.reelsnap.TargetApp
import com.reelsnap.data.Settings
import com.reelsnap.data.SettingsRepository
import com.reelsnap.storage.ImageSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ReelSnap"
private const val LEAVE_DEBOUNCE_MS = 1000L

/**
 * Watches which app is in the foreground. While an enabled target app (Instagram / Facebook / Lite)
 * is open, runs [CaptureLoop]; when the user leaves it, the loop is cancelled.
 */
class ReelSnapService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: SettingsRepository
    private lateinit var settings: StateFlow<Settings>
    private var loopJob: Job? = null
    private var stopJob: Job? = null

    /** Set when a reel/time limit stops the loop; cleared once the user leaves the target app. */
    private var limitReached = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        repo = SettingsRepository(applicationContext)
        settings = repo.settings.stateIn(scope, SharingStarted.Eagerly, Settings())
        ServiceStatus.update { it.copy(connected = true) }
        scope.launch { settings.collect { evaluate() } }
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::settings.isInitialized) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> evaluate()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        ServiceStatus.update { Status() }
        scope.cancel()
        super.onDestroy()
    }

    /**
     * The enabled target app that currently owns the active (focused) application window, or null.
     * Keyboards and other non-app windows are ignored; system UI (notification shade) counts as "left".
     */
    fun foregroundTarget(): TargetApp? {
        val s = settings.value
        val pkg = try {
            windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
                ?.root?.packageName?.toString()
                ?: rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }
        // Notification shade / quick settings / recents → not in the target app.
        val activeIsSystem = try {
            windows.any { it.isActive && it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
        } catch (e: Exception) {
            false
        }
        if (activeIsSystem) return null
        return TargetApp.fromPackage(pkg)?.takeIf { it.packageName in s.enabledApps }
    }

    private fun evaluate() {
        val target = foregroundTarget()
        if (target == null) limitReached = false
        val shouldRun = settings.value.enabled && target != null && !limitReached

        if (shouldRun) {
            stopJob?.cancel()
            stopJob = null
            if (loopJob?.isActive != true) startLoop()
        } else if (loopJob?.isActive == true && stopJob?.isActive != true) {
            // Debounce: brief transitions (dialogs, keyboard) shouldn't end the session.
            stopJob = scope.launch {
                delay(LEAVE_DEBOUNCE_MS)
                val stillTarget = foregroundTarget() != null
                if (!settings.value.enabled || !stillTarget) {
                    loopJob?.cancel()
                    feedback("ReelSnap paused")
                }
            }
        }
    }

    private fun startLoop() {
        val loop = CaptureLoop(this, ImageSaver(applicationContext)) { settings.value }
        feedback("ReelSnap started")
        loopJob = scope.launch(Dispatchers.Default) {
            val reason = loop.run()
            limitReached = true
            val msg = when (reason) {
                StopReason.REEL_LIMIT -> "ReelSnap: reel limit reached"
                StopReason.TIME_LIMIT -> "ReelSnap: time limit reached"
            }
            ServiceStatus.update { it.copy(message = msg) }
            launch(Dispatchers.Main) { toast(msg) }
        }
    }

    fun onImageSaved(uri: Uri) {
        scope.launch(Dispatchers.IO) { repo.setLastImageUri(uri.toString()) }
    }

    fun screenSize(): Pair<Int, Int> {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val p = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(p)
        return p.x to p.y
    }

    private fun feedback(text: String) {
        if (!settings.value.feedback) return
        toast(text)
        try {
            getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    private fun toast(text: String) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }
}
