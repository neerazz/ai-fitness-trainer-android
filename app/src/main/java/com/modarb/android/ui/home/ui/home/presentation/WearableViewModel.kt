package com.modarb.android.ui.home.ui.home.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.StateFlow

class WearableViewModel(application: Application) : AndroidViewModel(application) {

    private val tracker = WearableTracker(application.applicationContext)

    val metrics: StateFlow<WearableMetrics> = tracker.metrics

    fun startTracking() {
        tracker.start()
    }

    fun stopTracking() {
        tracker.stop()
    }

    fun toggleTracking() {
        if (tracker.isTracking()) {
            stopTracking()
        } else {
            startTracking()
        }
    }

    fun isTracking(): Boolean = tracker.isTracking()

    override fun onCleared() {
        tracker.stop()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WearableViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return WearableViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class $modelClass")
        }
    }
}
