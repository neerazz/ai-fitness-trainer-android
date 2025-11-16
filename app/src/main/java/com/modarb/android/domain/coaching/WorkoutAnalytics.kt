package com.modarb.android.domain.coaching

import com.modarb.android.ui.home.ui.plan.domain.models.Exercise
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object WorkoutAnalytics {

    fun estimateDurationMinutes(exercises: List<Exercise>): Int {
        if (exercises.isEmpty()) return 0
        val totalSeconds =
            exercises.sumOf { exercise -> exercise.duration + exercise.expectedDurationRange.min }
        return max(1, totalSeconds / 60)
    }

    fun estimateCalories(weightKg: Int?, durationMinutes: Int, intensityLabel: String): Int {
        val safeWeight = (weightKg ?: DEFAULT_WEIGHT_KG).coerceAtLeast(40)
        val met = when (intensityLabel.lowercase()) {
            "intense", "hiit", "power" -> 8.5
            "moderate" -> 6.0
            "recovery", "light" -> 4.0
            else -> 5.5
        }
        val calories = met * 3.5 * safeWeight / 200.0 * durationMinutes
        return calories.roundToInt()
    }

    fun resolveIntensity(dayType: String): String {
        val normalized = dayType.lowercase(Locale.getDefault())
        return when {
            normalized.contains("rest") -> "Recovery"
            normalized.contains("hiit") || normalized.contains("intense") || normalized.contains("power") -> "Intense"
            normalized.contains("cardio") || normalized.contains("strength") -> "Moderate"
            normalized.contains("yoga") || normalized.contains("mobility") -> "Light"
            else -> "Moderate"
        }
    }

    private const val DEFAULT_WEIGHT_KG = 72
}
