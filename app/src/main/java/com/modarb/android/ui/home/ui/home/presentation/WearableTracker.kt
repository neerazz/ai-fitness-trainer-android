package com.modarb.android.ui.home.ui.home.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

class WearableTracker(context: Context) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _metrics = MutableStateFlow(WearableMetrics())
    val metrics: StateFlow<WearableMetrics> = _metrics

    private var simulationJob: Job? = null
    private val simulationScope = CoroutineScope(Dispatchers.Default)
    private var stepBaseline: Float? = null
    private var isTrackingSensors = false

    fun start() {
        if (heartRateSensor == null && stepSensor == null) {
            startSimulation()
            return
        }
        _metrics.update { it.copy(connectionState = WearableMetrics.ConnectionState.CONNECTING) }
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            isTrackingSensors = true
        }
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            isTrackingSensors = true
        }
        _metrics.update { it.copy(connectionState = WearableMetrics.ConnectionState.CONNECTED) }
    }

    fun stop() {
        if (isTrackingSensors) {
            sensorManager.unregisterListener(this)
        }
        stopSimulation()
        isTrackingSensors = false
        _metrics.update { it.copy(connectionState = WearableMetrics.ConnectionState.DISCONNECTED) }
    }

    fun isTracking(): Boolean = isTrackingSensors || simulationJob?.isActive == true

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val heartRate = event.values.firstOrNull()?.roundToInt() ?: return
                _metrics.update { it.copy(heartRate = heartRate, connectionState = WearableMetrics.ConnectionState.CONNECTED) }
            }

            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values.firstOrNull() ?: return
                if (stepBaseline == null) {
                    stepBaseline = totalSteps
                }
                val steps = (totalSteps - (stepBaseline ?: totalSteps)).coerceAtLeast(0f).roundToInt()
                val calories = estimateCaloriesFromSteps(steps, _metrics.value.heartRate)
                _metrics.update {
                    it.copy(
                        steps = steps,
                        caloriesBurned = calories,
                        connectionState = WearableMetrics.ConnectionState.CONNECTED
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startSimulation() {
        if (simulationJob?.isActive == true) return
        _metrics.value = _metrics.value.copy(connectionState = WearableMetrics.ConnectionState.SIMULATION)
        simulationJob = simulationScope.launch {
            var simulatedSteps = 0
            var simulatedCalories = 12
            var heartRate = 82
            while (isActive) {
                heartRate = (heartRate + Random.nextInt(-4, 6)).coerceIn(65, 165)
                simulatedSteps += Random.nextInt(8, 28)
                simulatedCalories += Random.nextInt(2, 6)
                _metrics.value = WearableMetrics(
                    heartRate = heartRate,
                    steps = simulatedSteps,
                    caloriesBurned = simulatedCalories,
                    connectionState = WearableMetrics.ConnectionState.SIMULATION
                )
                delay(3000)
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
    }

    private fun estimateCaloriesFromSteps(steps: Int, heartRate: Int): Int {
        if (steps <= 0) return 0
        val effort = when {
            heartRate > 150 -> 0.06
            heartRate > 120 -> 0.05
            else -> 0.04
        }
        return (steps * effort).roundToInt()
    }
}
