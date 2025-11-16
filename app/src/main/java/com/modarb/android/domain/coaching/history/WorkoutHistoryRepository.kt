package com.modarb.android.domain.coaching.history

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WorkoutHistoryRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val typeToken = object : TypeToken<MutableList<WorkoutSession>>() {}.type

    fun logSession(session: WorkoutSession) {
        val history = getHistory().toMutableList()
        history.removeAll {
            it.workoutId == session.workoutId && it.weekNumber == session.weekNumber && it.dayNumber == session.dayNumber
        }
        history.add(0, session)
        prefs.edit().putString(KEY_HISTORY, gson.toJson(history.take(MAX_ENTRIES))).apply()
    }

    fun getHistory(): List<WorkoutSession> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<WorkoutSession>>(json, typeToken)
        }.getOrDefault(emptyList())
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private companion object {
        private const val PREF_NAME = "workout_history_pref"
        private const val KEY_HISTORY = "history"
        private const val MAX_ENTRIES = 40
    }
}
