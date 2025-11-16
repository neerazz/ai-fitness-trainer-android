package com.modarb.android.domain.coaching.history

data class WorkoutSession(
    val workoutId: String,
    val workoutName: String,
    val durationMinutes: Int,
    val completedAt: Long,
    val caloriesBurned: Int,
    val weekNumber: Int,
    val dayNumber: Int,
    val focusAreas: List<String>,
    val intensityLabel: String
)
