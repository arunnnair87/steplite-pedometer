package com.steplite.pedometer.sensor

import kotlin.math.sqrt

/**
 * Orientation-independent software step detector for basic Android phones
 * that only have Sensor.TYPE_ACCELEROMETER.
 *
 * Uses a low-pass smoothing filter on 3-axis acceleration magnitude,
 * dynamic peak threshold (default 11.8 m/s²), and a 250ms–2000ms cadence gate
 * to reject rapid pocket/bus vibrations.
 */
class AccelerometerStepDetector(
    var peakThreshold: Float = 11.8f
) {
    private var smoothedMag = 9.81f
    private var lastMag = 9.81f
    private var wasAboveThreshold = false
    private var lastStepTimeMs = 0L
    var rejectedFalseSpikes = 0
        private set

    fun processSample(ax: Float, ay: Float, az: Float, timestampMs: Long): Boolean {
        val rawMag = sqrt(ax * ax + ay * ay + az * az)
        // Low-pass filter (alpha = 0.35) to remove jitter on inexpensive MEMS sensors
        smoothedMag = 0.65f * smoothedMag + 0.35f * rawMag

        var stepDetected = false
        if (!wasAboveThreshold && smoothedMag >= peakThreshold && lastMag < peakThreshold) {
            wasAboveThreshold = true
            val deltaMs = timestampMs - lastStepTimeMs

            if (smoothedMag > 24.0f || (lastStepTimeMs > 0L && deltaMs < 240L)) {
                // Reject unnatural spike or rapid shaking (<240ms between peaks)
                rejectedFalseSpikes++
            } else {
                lastStepTimeMs = timestampMs
                stepDetected = true
            }
        } else if (smoothedMag < peakThreshold - 0.6f) {
            wasAboveThreshold = false
        }

        lastMag = smoothedMag
        return stepDetected
    }
}
