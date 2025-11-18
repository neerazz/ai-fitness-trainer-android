package com.modarb.android.ui.onboarding.utils.UserPref

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.modarb.android.ui.onboarding.models.RequestModels.RegisterRequest
import java.util.UUID

/**
 * Local storage manager for user credentials and data (POC implementation)
 * This stores user data locally instead of connecting to a backend service
 */
object LocalUserStorage {
    private const val PREF_FILE_NAME = "local_users_storage"
    private const val USERS_KEY = "registered_users"
    private val gson = Gson()

    /**
     * Data class to store user credentials locally
     */
    data class LocalUser(
        val id: String,
        val email: String,
        val password: String, // In production, never store plain passwords!
        val name: String,
        val dob: String,
        val gender: String,
        val height: Int,
        val weight: Int,
        val fitness_level: String,
        val injuries: List<String>,
        val preferences: LocalPreferences
    )

    data class LocalPreferences(
        val fitness_goal: String,
        val preferred_equipment: List<String>,
        val target_weight: Int,
        val workout_place: String,
        val preferred_days: List<String>,
        val workout_frequency: Int
    )

    /**
     * Save a new user registration locally
     */
    fun registerUser(context: Context, registerRequest: RegisterRequest): Boolean {
        try {
            val users = getAllUsers(context).toMutableList()

            // Check if user already exists
            if (users.any { it.email.equals(registerRequest.email, ignoreCase = true) }) {
                return false // User already exists
            }

            // Create new local user
            val newUser = LocalUser(
                id = UUID.randomUUID().toString(),
                email = registerRequest.email,
                password = registerRequest.password,
                name = registerRequest.name,
                dob = registerRequest.dob,
                gender = registerRequest.gender,
                height = registerRequest.height,
                weight = registerRequest.weight,
                fitness_level = registerRequest.fitness_level,
                injuries = registerRequest.injuries,
                preferences = LocalPreferences(
                    fitness_goal = registerRequest.preferences.fitness_goal,
                    preferred_equipment = registerRequest.preferences.preferred_equipment,
                    target_weight = registerRequest.preferences.target_weight,
                    workout_place = registerRequest.preferences.workout_place,
                    preferred_days = registerRequest.preferences.preferred_days,
                    workout_frequency = registerRequest.preferences.workout_frequency
                )
            )

            users.add(newUser)
            saveAllUsers(context, users)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Validate login credentials
     */
    fun validateLogin(context: Context, email: String, password: String): LocalUser? {
        val users = getAllUsers(context)
        return users.find {
            it.email.equals(email, ignoreCase = true) && it.password == password
        }
    }

    /**
     * Get user by email
     */
    fun getUserByEmail(context: Context, email: String): LocalUser? {
        val users = getAllUsers(context)
        return users.find { it.email.equals(email, ignoreCase = true) }
    }

    /**
     * Get all registered users
     */
    private fun getAllUsers(context: Context): List<LocalUser> {
        val jsonData = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
            .getString(USERS_KEY, null) ?: return emptyList()

        val type = object : TypeToken<List<LocalUser>>() {}.type
        return gson.fromJson(jsonData, type) ?: emptyList()
    }

    /**
     * Save all users to SharedPreferences
     */
    private fun saveAllUsers(context: Context, users: List<LocalUser>) {
        val jsonData = gson.toJson(users)
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(USERS_KEY, jsonData)
            .apply()
    }

    /**
     * Clear all users (for testing)
     */
    fun clearAllUsers(context: Context) {
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(USERS_KEY)
            .apply()
    }

    /**
     * Get total number of registered users
     */
    fun getUserCount(context: Context): Int {
        return getAllUsers(context).size
    }
}
