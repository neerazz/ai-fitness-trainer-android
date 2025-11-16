package com.modarb.android.domain.coaching.history

data class NutritionLog(
    val timestamp: Long,
    val caloriesGoal: Int,
    val caloriesConsumed: Int,
    val proteinGoal: Int,
    val proteinConsumed: Int,
    val carbsGoal: Int,
    val carbsConsumed: Int,
    val fatGoal: Int,
    val fatConsumed: Int
)
