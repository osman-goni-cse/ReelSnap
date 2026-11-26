package com.reelsnap.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenshotException(val code: Int) : Exception("takeScreenshot failed with code $code")

/** One screenshot of the default display as a software ARGB_8888 bitmap. */
private suspend fun AccessibilityService.captureOnce(): Bitmap =
    suspendCancellableCoroutine { cont ->
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        buffer.close()
                    }
                    if (bitmap != null) cont.resume(bitmap)
                    else cont.resumeWithException(ScreenshotException(-1))
                }

                override fun onFailure(errorCode: Int) {
                    cont.resumeWithException(ScreenshotException(errorCode))
                }
            },
        )
    }

/**
 * Screenshot with a single retry when Android rejects the call for being too soon
 * after the previous one (it allows roughly one accessibility screenshot per second).
 */
suspend fun AccessibilityService.capture(): Bitmap = try {
    captureOnce()
} catch (e: ScreenshotException) {
    if (e.code != AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) throw e
    delay(1000)
    captureOnce()
}
