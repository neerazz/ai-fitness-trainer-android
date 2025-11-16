package com.modarb.android.ui.home.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import com.modarb.android.R
import com.modarb.android.databinding.FragmentHomeBinding
import com.modarb.android.domain.coaching.DietPlanRecommendation
import com.modarb.android.domain.coaching.RecommendationEngine
import com.modarb.android.domain.coaching.WorkoutPlanRecommendation
import com.modarb.android.domain.coaching.history.NutritionHistoryRepository
import com.modarb.android.domain.coaching.history.NutritionLog
import com.modarb.android.domain.coaching.history.WorkoutHistoryRepository
import com.modarb.android.network.ApiResult
import com.modarb.android.network.NetworkHelper
import com.modarb.android.posedetection.RequestPermissionsActivity
import com.modarb.android.ui.ChatBotWebView
import com.modarb.android.ui.helpers.WorkoutData
import com.modarb.android.ui.home.HomeActivity
import com.modarb.android.ui.home.ui.home.domain.models.HomePageResponse
import com.modarb.android.ui.home.ui.home.presentation.HomeViewModel
import com.modarb.android.ui.home.ui.home.presentation.WearableMetrics
import com.modarb.android.ui.home.ui.home.presentation.WearableViewModel
import com.modarb.android.ui.home.ui.nutrition.domain.models.today_intake.TodayInTakeResponse
import com.modarb.android.ui.home.ui.plan.domain.models.PlanPageResponse
import com.modarb.android.ui.home.ui.plan.persentation.PlanViewModel
import com.modarb.android.ui.onboarding.activities.SplashActivity
import com.modarb.android.ui.onboarding.utils.UserPref.UserPrefUtil
import com.modarb.android.ui.workout.activities.TodayWorkoutActivity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class HomeFragment : Fragment() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val planViewModel: PlanViewModel by viewModels()


    private lateinit var binding: FragmentHomeBinding
    private lateinit var wearableViewModel: WearableViewModel
    private lateinit var workoutHistoryRepository: WorkoutHistoryRepository
    private lateinit var nutritionHistoryRepository: NutritionHistoryRepository
    private var latestHomeResponse: HomePageResponse? = null
    private var latestTodayInTake: TodayInTakeResponse? = null

    private val wearablePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.all { it.value }
            if (granted) {
                wearableViewModel.startTracking()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.wearable_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wearableViewModel = ViewModelProvider(
            this,
            WearableViewModel.Factory(requireActivity().application)
        )[WearableViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        workoutHistoryRepository = WorkoutHistoryRepository(requireContext())
        nutritionHistoryRepository = NutritionHistoryRepository(requireContext())
        getHomeData()
        getTodayInTake()
        initLogout()
        initActions()
        handleClick()
        observeWearableMetrics()

        Log.d("User ID", UserPrefUtil.getUserData(requireContext())!!.user.id)
        return root
    }

    private fun handleClick() {
        binding.continueBtn.setOnClickListener {
            if (WorkoutData.getTodayWorkout() == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.please_enroll_on_a_new_program),
                    Toast.LENGTH_SHORT
                ).show()
                (activity as? HomeActivity)?.navigateToFragment(R.id.navigation_workouts)
                return@setOnClickListener
            }
            startActivity(Intent(requireContext(), TodayWorkoutActivity::class.java))
        }
    }

    private fun getTodayInTake() {
        homeViewModel.getTodayInTake("Bearer " + UserPrefUtil.getUserData(requireContext())!!.token)


        lifecycleScope.launch {
            homeViewModel.todayInTake.collect {
                when (it) {
                    is ApiResult.Success<*> -> handleTodayInTakeResponse(it.data as TodayInTakeResponse)
                    is ApiResult.Failure -> handleHomeFail(it.exception)
                    else -> {}
                }
            }
        }
    }

    private fun handleTodayInTakeResponse(todayInTakeResponse: TodayInTakeResponse) {
        latestTodayInTake = todayInTakeResponse
        logTodayInTake(todayInTakeResponse)
        updateProgressBars(todayInTakeResponse)
        updateRecommendations()
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgressBars(model: TodayInTakeResponse) {
        val data = model.data
        updateProgressBar(
            binding.trackerView.circularProgressBar,
            data.caloriesGoal.roundToInt(),
            data.caloriesIntake.roundToInt()
        )
        binding.trackerView.calValue.text =
            (data.caloriesIntake.roundToInt()).toString() + " / " + data.caloriesGoal.roundToInt() + "\n\nKcal"
        binding.trackerView.burnedVal.text = data.caloriesBurned.toString() + "\nBurned"
        binding.trackerView.leftVal.text = data.caloriesLeft.toString() + "\nLeft"

        updateMacroProgressBar(
            binding.trackerView.carbsProgressBar,
            binding.trackerView.carbsValue,
            data.carbsGoal.roundToInt(),
            data.carbsConsumed.roundToInt(),
            "g"
        )
        updateMacroProgressBar(
            binding.trackerView.proteinProgressBar,
            binding.trackerView.proteinValue,
            data.proteinGoal.roundToInt(),
            data.proteinConsumed.roundToInt(),
            "g"
        )
        updateMacroProgressBar(
            binding.trackerView.fatsProgressBar,
            binding.trackerView.fatsValue,
            data.fatGoal.roundToInt(),
            data.fatConsumed.roundToInt(),
            "g"
        )

    }

    private fun updateProgressBar(progressBar: ProgressBar, max: Int, progress: Int) {
        progressBar.max = max
        progressBar.progress = progress
    }

    private fun updateProgressBar(progressBar: CircularProgressBar, max: Int, progress: Int) {
        progressBar.progressMax = max.toFloat()
        progressBar.progress = progress.toFloat()
    }

    private fun updateMacroProgressBar(
        progressBar: ProgressBar, valueTextView: TextView, goal: Int, consumed: Int, unit: String
    ) {
        valueTextView.text = "$consumed/$goal$unit"
        progressBar.max = goal
        progressBar.progress = consumed
    }

    private fun logTodayInTake(response: TodayInTakeResponse) {
        val data = response.data
        val logEntry = NutritionLog(
            timestamp = System.currentTimeMillis(),
            caloriesGoal = data.caloriesGoal.roundToInt(),
            caloriesConsumed = data.caloriesIntake.roundToInt(),
            proteinGoal = data.proteinGoal.roundToInt(),
            proteinConsumed = data.proteinConsumed.roundToInt(),
            carbsGoal = data.carbsGoal.roundToInt(),
            carbsConsumed = data.carbsConsumed.roundToInt(),
            fatGoal = data.fatGoal.roundToInt(),
            fatConsumed = data.fatConsumed.roundToInt()
        )
        nutritionHistoryRepository.logDailyIntake(logEntry)
    }

    private fun initLogout() {
        binding.profileBtn.setOnClickListener {
            UserPrefUtil.saveUserData(requireContext(), null)
            UserPrefUtil.setUserLoggedIn(requireContext(), false)
            startActivity(Intent(requireContext(), SplashActivity::class.java))
            requireActivity().finish()
        }

        binding.viewMealBtn.setOnClickListener {
            (activity as? HomeActivity)?.navigateToFragment(R.id.navigation_nutrition)
        }
    }


    private fun getHomeData() {
        binding.progressView.progressOverlay.visibility = View.VISIBLE
        homeViewModel.getUserHomePage("Bearer " + UserPrefUtil.getUserData(requireContext())!!.token)


        lifecycleScope.launch {
            homeViewModel.homeResponse.collect {
                when (it) {
                    is ApiResult.Success<*> -> handleHomeSuccess(it.data as HomePageResponse)
                    is ApiResult.Error -> handleHomeError(it.data as HomePageResponse)
                    is ApiResult.Failure -> handleHomeFail(it.exception)
                    else -> {}
                }
            }
        }

    }

    @SuppressLint("SetTextI18n")
    private fun initMealPlan(homePageResponse: HomePageResponse) {
        binding.mealDetails.text =
            homePageResponse.data.myMealPlan.today.numberOfMeals.toString() + " Meals and ${homePageResponse.data.myMealPlan.today.numberOfSnacks} snacks"
        binding.calories.text =
            homePageResponse.data.myMealPlan.today.totalCalories.toString() + " Cal"
    }

    private fun handleHomeFail(exception: Throwable) {
        Toast.makeText(requireContext(), exception.message, Toast.LENGTH_SHORT).show()
        binding.progressView.progressOverlay.visibility = View.GONE
    }

    private fun handleHomeSuccess(res: HomePageResponse) {
        latestHomeResponse = res
        WorkoutData.workoutId = res.data.myWorkout.id
        setData(res)
        initMealPlan(res)
        getPlanData()
        updateRecommendations()
    }

    private fun handleHomeError(errorResponse: HomePageResponse) {
        NetworkHelper.showErrorMessage(requireContext(), errorResponse)
        binding.progressView.progressOverlay.visibility = View.GONE
    }

    private fun getPlanData() {

        lifecycleScope.launch {
            planViewModel.planResponse.collect {
                when (it) {
                    is ApiResult.Success<*> -> handlePlanSuccess(it.data as PlanPageResponse)
                    is ApiResult.Error -> handlePlanError(it.data)
                    is ApiResult.Failure -> handlePlanFail(it.exception)
                    else -> {}
                }
            }
        }
        planViewModel.getPlanPage(
            WorkoutData.workoutId, "Bearer " + UserPrefUtil.getUserData(requireContext())!!.token
        )

    }

    private fun handlePlanFail(exception: Throwable) {
        Toast.makeText(requireContext(), exception.message, Toast.LENGTH_SHORT).show()
    }

    private fun handlePlanError(errorResponse: PlanPageResponse) {
        NetworkHelper.showErrorMessage(requireContext(), errorResponse)
    }

    private fun handlePlanSuccess(res: PlanPageResponse) {
        WorkoutData.weekList = res.data.weeks
        binding.progressView.progressOverlay.visibility = View.GONE
    }

    @SuppressLint("SetTextI18n")
    private fun initActions() {
        binding.workoutPlanView.setOnClickListener {
            (activity as? HomeActivity)?.navigateToFragment(R.id.navigation_my_plan)
        }

        binding.nutritionView.setOnClickListener {
            (activity as? HomeActivity)?.navigateToFragment(R.id.navigation_nutrition)
        }

        binding.chatBtn.setOnClickListener {
            startActivity(Intent(requireContext(), ChatBotWebView::class.java))
        }

        binding.cameraBtn.setOnClickListener {
            startActivity(Intent(requireContext(), RequestPermissionsActivity::class.java))
        }
        binding.userName.text = "Hey, \n" + UserPrefUtil.getUserData(requireContext())!!.user.name
    }

    @SuppressLint("SetTextI18n")
    private fun setData(response: HomePageResponse) {
        binding.todayWorkoutName.text = response.data.myWorkout.workout.name
        binding.workouttime.text =
            formatWorkoutTime(response.data.myWorkout.workout.min_per_day, requireContext())

        val exerciseCount =
            WorkoutData.getTodayWorkout(response.data.myWorkout.weeks)?.total_number_exercises?.toString()
        binding.exerciseCountTxt.text =
            if (exerciseCount.isNullOrBlank()) "No Exercises" else "$exerciseCount Exercises"
    }

    private fun updateRecommendations() {
        val homeResponse = latestHomeResponse ?: return
        val profile = UserPrefUtil.getUserData(requireContext())?.user ?: return
        val recommendation = RecommendationEngine.generatePlan(
            profile = profile,
            workoutHistory = workoutHistoryRepository.getHistory(),
            nutritionHistory = nutritionHistoryRepository.getHistory(),
            activeWorkout = homeResponse.data.myWorkout,
            todayInTake = latestTodayInTake?.data
        )
        bindWorkoutRecommendation(recommendation.workoutPlan)
        bindDietRecommendation(recommendation.dietPlan)
    }

    private fun bindWorkoutRecommendation(plan: WorkoutPlanRecommendation) {
        binding.workoutPlanSummary.text =
            getString(R.string.recommended_weekly_minutes, plan.weeklyMinutes)
        binding.workoutSessionDetail.text =
            getString(R.string.recommended_session_length, plan.recommendedDuration)
        binding.workoutFocusGroup.removeAllViews()
        if (plan.focusAreas.isEmpty()) {
            binding.workoutFocusGroup.visibility = View.GONE
        } else {
            binding.workoutFocusGroup.visibility = View.VISIBLE
            plan.focusAreas.forEach { focus ->
                val chip = Chip(requireContext()).apply {
                    text = focus
                    isCheckable = false
                    isClickable = false
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    setChipBackgroundColorResource(R.color.grey_700)
                }
                binding.workoutFocusGroup.addView(chip)
            }
        }

        binding.workoutActions.text =
            if (plan.actionItems.isEmpty()) getString(R.string.coach_no_workout_data)
            else plan.actionItems.joinToString("\n") { "${it.dayLabel}: ${it.summary}" }
    }

    private fun bindDietRecommendation(plan: DietPlanRecommendation) {
        binding.dietPlanSummary.text = getString(R.string.calories_value, plan.calorieTarget)
        binding.dietMacroSummary.text = getString(
            R.string.coach_macro_breakdown,
            plan.macroTargets.protein,
            plan.macroTargets.carbs,
            plan.macroTargets.fats
        )
        val reminders = plan.reminders.ifEmpty { listOf(getString(R.string.coach_default_diet_reminder)) }
        val hydration = getString(R.string.hydration_goal, plan.hydrationGoalMl)
        binding.dietReminders.text =
            (reminders + hydration).joinToString(separator = "\n") { "• $it" }
        binding.dietMeals.text = plan.meals.joinToString(separator = " • ") { it.title }
    }

    private fun observeWearableMetrics() {
        binding.connectWearableButton.setOnClickListener {
            handleWearableButtonClick()
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                wearableViewModel.metrics.collect { bindWearableMetrics(it) }
            }
        }
    }

    private fun bindWearableMetrics(metrics: WearableMetrics) {
        val statusText = when (metrics.connectionState) {
            WearableMetrics.ConnectionState.CONNECTED -> getString(R.string.wearable_connected)
            WearableMetrics.ConnectionState.SIMULATION -> getString(R.string.wearable_simulated)
            else -> getString(R.string.wearable_connect_prompt)
        }
        binding.wearableStatus.text = statusText
        binding.heartRateValue.text = getString(R.string.heart_rate_bpm, metrics.heartRate)
        binding.stepsValue.text = getString(R.string.steps_value, metrics.steps)
        binding.caloriesValue.text = getString(R.string.calories_value, metrics.caloriesBurned)

        binding.connectWearableButton.text =
            if (wearableViewModel.isTracking()) getString(R.string.stop_wearable_cta)
            else getString(R.string.connect_wearable_cta)
        binding.connectWearableButton.isEnabled =
            metrics.connectionState != WearableMetrics.ConnectionState.CONNECTING
    }

    private fun handleWearableButtonClick() {
        if (wearableViewModel.isTracking()) {
            wearableViewModel.stopTracking()
            return
        }
        requestWearablePermissions()
    }

    private fun requestWearablePermissions() {
        val required = mutableListOf(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            required.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            wearableViewModel.startTracking()
        } else {
            wearablePermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun formatWorkoutTime(minutesPerDay: Int, context: Context): String {
        return "$minutesPerDay ${context.getString(R.string.min)}"
    }

}
