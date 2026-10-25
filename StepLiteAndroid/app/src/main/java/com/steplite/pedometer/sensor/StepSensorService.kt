package com.steplite.pedometer.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.steplite.pedometer.data.DailyStepEntity
import com.steplite.pedometer.data.StepDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Lightweight Foreground Service that automatically picks:
 * 1. Sensor.TYPE_STEP_COUNTER (if hardware exists on phone)
 * 2. Sensor.TYPE_ACCELEROMETER (universal fallback for basic phones)
 *
 * Persists daily totals into Room SQLite so years of history accumulate seamlessly.
 */
class StepSensorService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sensorManager: SensorManager
    private lateinit var database: StepDatabase
    private val accelDetector = AccelerometerStepDetector()

    private var usingHardwareCounter = false
    private var lastHardwareCounterValue: Int? = null

    override fun onCreate() {
        super.onCreate()
        database = StepDatabase.getInstance(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        startForegroundWithNotification(0)
        registerBestAvailableSensor()
    }

    private fun registerBestAvailableSensor() {
        val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepCounterSensor != null) {
            usingHardwareCounter = true
            sensorManager.registerListener(
                this,
                stepCounterSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        } else {
            usingHardwareCounter = false
            val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelSensor != null) {
                sensorManager.registerListener(
                    this,
                    accelSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSinceBoot = event.values[0].toInt()
                val previous = lastHardwareCounterValue
                lastHardwareCounterValue = totalSinceBoot
                if (previous != null && totalSinceBoot > previous) {
                    val delta = totalSinceBoot - previous
                    recordStepsToDatabase(delta)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val nowMs = SystemClock.elapsedRealtime()
                val isStep = accelDetector.processSample(
                    event.values[0],
                    event.values[1],
                    event.values[2],
                    nowMs
                )
                if (isStep) {
                    recordStepsToDatabase(1)
                }
            }
        }
    }

    private fun recordStepsToDatabase(deltaSteps: Int) {
        serviceScope.launch {
            val today = LocalDate.now()
            val dateIso = today.toString() // YYYY-MM-DD
            val yearMonth = dateIso.substring(0, 7) // YYYY-MM
            val dao = database.stepDao()

            val current = dao.getDayOnce(dateIso)
            val updatedSteps = (current?.steps ?: 0) + deltaSteps
            val strideMeters = 0.74f
            val weightKg = 68f
            val distanceMeters = updatedSteps * strideMeters
            val caloriesKcal = (updatedSteps * 0.04f * (weightKg / 68f)).toInt()

            val entity = DailyStepEntity(
                dateIso = dateIso,
                epochDay = today.toEpochDay(),
                year = today.year,
                yearMonth = yearMonth,
                steps = updatedSteps,
                goal = current?.goal ?: 6000,
                activeSeconds = (current?.activeSeconds ?: 0) + (deltaSteps * 0.55f).toInt(),
                distanceMeters = distanceMeters,
                caloriesKcal = caloriesKcal
            )
            dao.upsertDay(entity)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startForegroundWithNotification(stepsToday: Int) {
        val channelId = "steplite_tracker_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "StepLite Active Counter",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle("StepLite Active")
            .setContentText("$stepsToday steps tracked today")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
