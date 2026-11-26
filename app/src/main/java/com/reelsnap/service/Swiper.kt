package com.reelsnap.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * Swipe up from ~75% to ~25% of the screen height to go to the next reel.
 * With [jitter], the start/end points, the curve and the duration vary slightly.
 * Returns true if the system completed the gesture.
 */
suspend fun AccessibilityService.swipeUp(width: Int, height: Int, jitter: Boolean): Boolean {
    fun j(amount: Float) = if (jitter) Random.nextFloat() * 2 * amount - amount else 0f

    val startX = width * (0.5f + j(0.08f))
    val endX = startX + width * j(0.05f)
    val startY = height * (0.75f + j(0.04f))
    val endY = height * (0.25f + j(0.04f))
    val ctrlX = (startX + endX) / 2 + width * j(0.06f)
    val ctrlY = (startY + endY) / 2

    val path = Path().apply {
        moveTo(startX, startY)
        quadTo(ctrlX, ctrlY, endX, endY)
    }
    val duration = (250 + j(60f)).toLong().coerceAtLeast(100)
    val gesture = GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        .build()

    return suspendCancellableCoroutine { cont ->
        val dispatched = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(g: GestureDescription) {
                    if (cont.isActive) cont.resume(false)
                }
            },
            null,
        )
        if (!dispatched && cont.isActive) cont.resume(false)
    }
}
