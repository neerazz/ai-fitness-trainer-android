package com.modarb.android.ui.home.ui.home.presentation

data class WearableMetrics(
    val heartRate: Int = 0,
    val steps: Int = 0,
    val caloriesBurned: Int = 0,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED
) {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        SIMULATION,
        NOT_SUPPORTED
    }
}
