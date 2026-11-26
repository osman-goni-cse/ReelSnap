package com.reelsnap.service

import android.util.Log
import com.reelsnap.TargetApp
import com.reelsnap.data.Settings
import com.reelsnap.storage.ImageSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val TAG = "ReelSnap"

/** Android allows roughly one accessibility screenshot per second; stay safely above that. */
private const val MIN_SHOT_INTERVAL_MS = 1100L

enum class StopReason { REEL_LIMIT, TIME_LIMIT }

/**
 * The capture loop: wait → screenshot × N (gap between) → swipe up → repeat.
 * Runs until its coroutine is cancelled (target app left / disabled) or a limit is hit.
 */
class CaptureLoop(
    private val service: ReelSnapService,
    private val saver: ImageSaver,
    private val settings: () -> Settings,
) {
    private var lastShotAt = 0L

    private fun jittered(ms: Long, jitter: Boolean): Long =
        if (!jitter || ms == 0L) ms else (ms * Random.nextDouble(0.8, 1.2)).toLong()

    /** Suspends while the foreground app is not an enabled target (e.g. home pressed, shade pulled down). */
    private suspend fun awaitTargetForeground(): TargetApp {
        while (true) {
            service.foregroundTarget()?.let { return it }
            delay(200)
        }
    }

    /** Returns the reason it stopped by itself; throws CancellationException when cancelled. */
    suspend fun run(): StopReason {
        val session = saver.newSessionName()
        val startedAt = System.currentTimeMillis()
        var reels = 0
        var totalShots = 0
        Log.i(TAG, "Session $session started")
        ServiceStatus.update { it.copy(running = true, session = session, reels = 0, shots = 0, message = null) }

        try {
            while (true) {
                val s = settings()
                delay(jittered(s.waitMs, s.jitter))

                for (i in 1..s.shots) {
                    val app = awaitTargetForeground()
                    val sinceLast = System.currentTimeMillis() - lastShotAt
                    if (sinceLast < MIN_SHOT_INTERVAL_MS) delay(MIN_SHOT_INTERVAL_MS - sinceLast)
                    try {
                        val bitmap = service.capture()
                        lastShotAt = System.currentTimeMillis()
                        val stem = "%s_r%04d_s%02d".format(app.shortName, reels + 1, i)
                        val uri = saver.save(bitmap, session, stem, s.format)
                        bitmap.recycle()
                        totalShots++
                        service.onImageSaved(uri)
                        ServiceStatus.update { it.copy(appLabel = app.label, shots = totalShots) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Screenshot failed", e)
                        ServiceStatus.update { it.copy(message = "Screenshot failed: ${e.message}") }
                    }
                    if (i < s.shots) delay(jittered(s.gapMs, s.jitter))
                }

                awaitTargetForeground()
                val (w, h) = service.screenSize()
                if (!service.swipeUp(w, h, s.jitter)) Log.w(TAG, "Swipe was cancelled")
                reels++
                ServiceStatus.update { it.copy(reels = reels) }

                val now = settings()
                if (now.maxReels in 1..reels) return StopReason.REEL_LIMIT
                if (now.maxMinutes > 0 &&
                    System.currentTimeMillis() - startedAt >= now.maxMinutes * 60_000L
                ) return StopReason.TIME_LIMIT
            }
        } finally {
            Log.i(TAG, "Session $session ended: $reels reels, $totalShots shots")
            ServiceStatus.update { it.copy(running = false) }
        }
    }
}
