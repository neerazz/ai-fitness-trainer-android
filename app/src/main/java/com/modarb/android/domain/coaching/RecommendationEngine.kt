package com.modarb.android.domain.coaching

import com.modarb.android.domain.coaching.history.NutritionLog
import com.modarb.android.domain.coaching.history.WorkoutSession
import com.modarb.android.ui.home.ui.home.domain.models.MyWorkout
import com.modarb.android.ui.home.ui.plan.domain.models.Day
import com.modarb.android.ui.home.ui.plan.domain.models.Week
import com.modarb.android.ui.onboarding.models.user
import com.modarb.android.ui.home.ui.nutrition.domain.models.today_intake.Data as TodayInTakeData
import kotlin.math.roundToInt

object RecommendationEngine {

    fun generatePlan(
        profile: user?,
        workoutHistory: List<WorkoutSession>,
        nutritionHistory: List<NutritionLog>,
        activeWorkout: MyWorkout?,
        todayInTake: TodayInTakeData?
    ): CoachRecommendation {
        val workoutPlan = buildWorkoutPlan(profile, workoutHistory, activeWorkout)
        val dietPlan = buildDietPlan(profile, nutritionHistory, todayInTake)
        return CoachRecommendation(workoutPlan, dietPlan)
    }

    private fun buildWorkoutPlan(
        profile: user?,
        workoutHistory: List<WorkoutSession>,
        activeWorkout: MyWorkout?
    ): WorkoutPlanRecommendation {
        val preferredDays =
            profile?.preferences?.preferred_days?.takeIf { it.isNotEmpty() } ?: DEFAULT_DAYS
        val weeklyFrequency = profile?.preferences?.workout_frequency?.takeIf { it > 0 } ?: 4
        val baseDuration = activeWorkout?.workout?.min_per_day ?: 35
        val weeklyMinutes = weeklyFrequency * baseDuration

        val focusAreas = when {
            activeWorkout != null -> extractFocusAreas(activeWorkout.weeks)
            workoutHistory.isNotEmpty() -> workoutHistory.first().focusAreas
            else -> listOf("Mobility", "Strength", "Cardio")
        }

        val actionItems = preferredDays.take(weeklyFrequency).mapIndexed { index, day ->
            val focus = focusAreas.getOrNull(index % focusAreas.size) ?: focusAreas.first()
            val recoveryCue = if (index == preferredDays.lastIndex) " • Active recovery" else ""
            WorkoutActionItem(
                dayLabel = day.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                summary = "$focus focus$recoveryCue - ${baseDuration}min"
            )
        }

        val recentAverage = workoutHistory.take(5).map { it.durationMinutes }.takeIf { it.isNotEmpty() }
            ?.average()?.roundToInt()
        val recommendedDuration = recentAverage?.coerceAtLeast(baseDuration) ?: baseDuration

        return WorkoutPlanRecommendation(
            weeklyMinutes = weeklyMinutes,
            focusAreas = focusAreas,
            actionItems = actionItems,
            recommendedDuration = recommendedDuration
        )
    }

    private fun buildDietPlan(
        profile: user?,
        nutritionHistory: List<NutritionLog>,
        todayInTake: TodayInTakeData?
    ): DietPlanRecommendation {
        val weight = profile?.weight ?: DEFAULT_WEIGHT
        val targetWeight = profile?.preferences?.target_weight ?: weight
        val goal = profile?.preferences?.fitness_goal ?: ""

        val calorieTarget = when {
            targetWeight < weight -> (weight * 32) - 350
            targetWeight > weight -> (weight * 36) + 200
            else -> weight * 34
        }.coerceIn(1400, 3800)

        val macroSplit = when {
            goal.contains("muscle", true) -> MacroSplit(0.35, 0.30, 0.35)
            goal.contains("lose", true) -> MacroSplit(0.35, 0.35, 0.30)
            else -> MacroSplit(0.30, 0.40, 0.30)
        }

        val macroTargets = MacroTargets(
            protein = (calorieTarget * macroSplit.protein / 4).roundToInt(),
            carbs = (calorieTarget * macroSplit.carbs / 4).roundToInt(),
            fats = (calorieTarget * macroSplit.fats / 9).roundToInt()
        )

        val hydrationGoal = (weight * 35).coerceIn(2000, 4200)

        val reminders = mutableListOf<String>()
        todayInTake?.let {
            if (it.caloriesIntake < it.caloriesGoal * 0.8) {
                reminders.add("You are ${(((it.caloriesGoal - it.caloriesIntake) / it.caloriesGoal) * 100).roundToInt()}% behind today's calories, schedule a balanced snack.")
            }
            if (it.proteinConsumed < macroTargets.protein * 0.8) {
                reminders.add("Prioritize lean protein at the next meal to reach ${macroTargets.protein}g target.")
            }
        }

        if (nutritionHistory.isNotEmpty()) {
            val averageProtein =
                nutritionHistory.take(5).map { it.proteinConsumed }.average().roundToInt()
            if (averageProtein < macroTargets.protein) {
                reminders.add("Average protein intake (${averageProtein}g) is under the goal, add an evening shake.")
            }
        }

        val meals = listOf(
            MealSuggestion("Power Breakfast", "Greek yogurt, berries, and oats for steady energy"),
            MealSuggestion("Training Lunch", "Lean protein + complex carbs + greens"),
            MealSuggestion("Recovery Dinner", "Focus on colorful veggies and slow carbs"),
            MealSuggestion("Hydration Snack", "Electrolyte-rich smoothie between workouts")
        )

        return DietPlanRecommendation(
            calorieTarget = calorieTarget,
            macroTargets = macroTargets,
            reminders = reminders.take(3),
            hydrationGoalMl = hydrationGoal,
            meals = meals
        )
    }

    private fun extractFocusAreas(weeks: List<Week>): List<String> {
        val muscles = weeks
            .flatMap(Week::days)
            .flatMap(Day::exercises)
            .mapNotNull { exercise -> exercise.targetMuscles.primary.name }
        return muscles
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .ifEmpty { listOf("Mobility", "Strength", "Cardio") }
    }

    private data class MacroSplit(val protein: Double, val carbs: Double, val fats: Double)

    private val DEFAULT_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private const val DEFAULT_WEIGHT = 72
}

data class CoachRecommendation(
    val workoutPlan: WorkoutPlanRecommendation,
    val dietPlan: DietPlanRecommendation
)

data class WorkoutPlanRecommendation(
    val weeklyMinutes: Int,
    val focusAreas: List<String>,
    val actionItems: List<WorkoutActionItem>,
    val recommendedDuration: Int
)

data class WorkoutActionItem(
    val dayLabel: String,
    val summary: String
)

data class DietPlanRecommendation(
    val calorieTarget: Int,
    val macroTargets: MacroTargets,
    val reminders: List<String>,
    val hydrationGoalMl: Int,
    val meals: List<MealSuggestion>
)

data class MacroTargets(
    val protein: Int,
    val carbs: Int,
    val fats: Int
)

data class MealSuggestion(
    val title: String,
    val description: String
)
