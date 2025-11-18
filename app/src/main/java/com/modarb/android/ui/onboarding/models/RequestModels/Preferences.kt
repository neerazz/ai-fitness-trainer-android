package com.modarb.android.ui.onboarding.models.RequestModels

data class Preferences(
    var fitness_goal: String,
    var preferred_equipment: List<String>,
    var target_weight: Int,
    var workout_place: String,
    var preferred_days: List<String> = emptyList(),
    var workout_frequency: Int = 0
)