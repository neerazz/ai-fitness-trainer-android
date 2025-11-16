package com.modarb.android.domain.coaching.history

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NutritionHistoryRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val typeToken = object : TypeToken<MutableList<NutritionLog>>() {}.type
    private val dayFormatter = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun logDailyIntake(entry: NutritionLog) {
        val history = getHistory().toMutableList()
        val entryDay = dayFormatter.format(Date(entry.timestamp))
        history.removeAll { dayFormatter.format(Date(it.timestamp)) == entryDay }
        history.add(0, entry)
        prefs.edit().putString(KEY_HISTORY, gson.toJson(history.take(MAX_ENTRIES))).apply()
    }

    fun getHistory(): List<NutritionLog> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<NutritionLog>>(json, typeToken)
        }.getOrDefault(emptyList())
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private companion object {
        private const val PREF_NAME = "nutrition_history_pref"
        private const val KEY_HISTORY = "history"
        private const val MAX_ENTRIES = 14
    }
}
