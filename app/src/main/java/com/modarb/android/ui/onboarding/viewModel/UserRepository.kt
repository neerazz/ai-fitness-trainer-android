package com.modarb.android.ui.onboarding.viewModel

import android.content.Context
import com.modarb.android.network.ApiService
import com.modarb.android.ui.onboarding.models.LoginResponse
import com.modarb.android.ui.onboarding.models.Preferences
import com.modarb.android.ui.onboarding.models.RequestModels.LoginRequest
import com.modarb.android.ui.onboarding.models.RequestModels.RegisterRequest
import com.modarb.android.ui.onboarding.models.data
import com.modarb.android.ui.onboarding.models.user
import com.modarb.android.ui.onboarding.utils.UserPref.LocalUserStorage
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UserRepository(
    private val apiService: ApiService,
    private val context: Context? = null,
    private val useLocalStorage: Boolean = true // POC mode - uses local storage
) {

    suspend fun loginUser(email: String, password: String): Response<LoginResponse> {
        // Use local storage for POC
        if (useLocalStorage && context != null) {
            return loginUserLocally(email, password)
        }

        // Fallback to API (original implementation)
        return apiService.loginUser(LoginRequest(email, password))
    }

    suspend fun registerUser(registerRequest: RegisterRequest): Response<LoginResponse> {
        // Use local storage for POC
        if (useLocalStorage && context != null) {
            return registerUserLocally(registerRequest)
        }

        // Fallback to API (original implementation)
        return apiService.registerUser(registerRequest)
    }

    /**
     * Local login implementation (POC)
     */
    private fun loginUserLocally(email: String, password: String): Response<LoginResponse> {
        val localUser = LocalUserStorage.validateLogin(context!!, email, password)

        if (localUser != null) {
            // Create successful response
            val loginResponse = LoginResponse().apply {
                status = 200
                message = "Login successful"
                data = data(
                    token = "local_token_${localUser.id}",
                    user = user(
                        id = localUser.id,
                        name = localUser.name,
                        email = localUser.email,
                        age = calculateAge(localUser.dob),
                        gender = localUser.gender,
                        height = localUser.height,
                        weight = localUser.weight,
                        fitness_level = localUser.fitness_level,
                        injuries = localUser.injuries,
                        role = "user",
                        preferences = Preferences(
                            fitness_goal = localUser.preferences.fitness_goal,
                            preferred_equipment = localUser.preferences.preferred_equipment,
                            target_weight = localUser.preferences.target_weight,
                            workout_place = localUser.preferences.workout_place,
                            preferred_days = localUser.preferences.preferred_days,
                            workout_frequency = localUser.preferences.workout_frequency
                        )
                    )
                )
            }
            return Response.success(loginResponse)
        } else {
            // Create error response
            val errorResponse = """{"status":401,"message":"Invalid email or password","errors":null}"""
            return Response.error(
                401,
                errorResponse.toResponseBody("application/json".toMediaTypeOrNull())
            )
        }
    }

    /**
     * Local registration implementation (POC)
     */
    private fun registerUserLocally(registerRequest: RegisterRequest): Response<LoginResponse> {
        val success = LocalUserStorage.registerUser(context!!, registerRequest)

        if (success) {
            // Create successful response
            val loginResponse = LoginResponse().apply {
                status = 201
                message = "Registration successful"
                data = data(
                    token = "local_token_temp",
                    user = user(
                        id = "temp_id",
                        name = registerRequest.name,
                        email = registerRequest.email,
                        age = calculateAge(registerRequest.dob),
                        gender = registerRequest.gender,
                        height = registerRequest.height,
                        weight = registerRequest.weight,
                        fitness_level = registerRequest.fitness_level,
                        injuries = registerRequest.injuries,
                        role = "user",
                        preferences = Preferences(
                            fitness_goal = registerRequest.preferences.fitness_goal,
                            preferred_equipment = registerRequest.preferences.preferred_equipment,
                            target_weight = registerRequest.preferences.target_weight,
                            workout_place = registerRequest.preferences.workout_place,
                            preferred_days = registerRequest.preferences.preferred_days,
                            workout_frequency = registerRequest.preferences.workout_frequency
                        )
                    )
                )
            }
            return Response.success(loginResponse)
        } else {
            // Create error response - user already exists
            val errorResponse = """{"status":400,"message":"User with this email already exists","errors":null}"""
            return Response.error(
                400,
                errorResponse.toResponseBody("application/json".toMediaTypeOrNull())
            )
        }
    }

    /**
     * Calculate age from date of birth
     */
    private fun calculateAge(dob: String): Int {
        return try {
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            val birthDate = formatter.parse(dob) ?: return 0

            val birthCalendar = Calendar.getInstance().apply { time = birthDate }
            val currentCalendar = Calendar.getInstance()

            var age = currentCalendar.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)

            if (currentCalendar.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                age--
            }

            age
        } catch (e: Exception) {
            0 // Default age if parsing fails
        }
    }
}
