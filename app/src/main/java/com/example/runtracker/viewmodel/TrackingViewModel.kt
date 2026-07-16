package com.example.runtracker.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.runtracker.RunTrackerApp
import com.example.runtracker.core.TrackSnapshot
import com.example.runtracker.data.SessionEntity
import com.example.runtracker.service.TrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private var service: TrackingService? = null
    private var bound = false

    private val _liveState = MutableStateFlow(TrackSnapshot())
    val liveState: StateFlow<TrackSnapshot> = _liveState.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _goalReachedEvent = MutableStateFlow(false)
    val goalReachedEvent: StateFlow<Boolean> = _goalReachedEvent.asStateFlow()

    private var lastGoalReachedSeen = false

    val history: StateFlow<List<SessionEntity>> =
        (application as RunTrackerApp).database.sessionDao().observeAllSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as TrackingService.LocalBinder
            service = localBinder.getService()
            bound = true
            viewModelScope.launch {
                service?.state?.collect { snapshot ->
                    _liveState.value = snapshot
                    if (snapshot.goalReached && !lastGoalReachedSeen) {
                        _goalReachedEvent.value = true
                    }
                    lastGoalReachedSeen = snapshot.goalReached
                }
            }
            viewModelScope.launch {
                service?.isTracking?.collect { tracking ->
                    _isTracking.value = tracking
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    init {
        val context = getApplication<Application>()
        val intent = Intent(context, TrackingService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun startSession(goalMeters: Double?) {
        val context = getApplication<Application>()
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
            goalMeters?.let { putExtra(TrackingService.EXTRA_GOAL_METERS, it) }
        }
        context.startForegroundService(intent)
    }

    fun stopSession() {
        val context = getApplication<Application>()
        context.startService(Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_STOP))
    }

    fun snoozeGoal(extraMeters: Double = 500.0) {
        val context = getApplication<Application>()
        context.startService(
            Intent(context, TrackingService::class.java)
                .setAction(TrackingService.ACTION_SNOOZE)
                .putExtra(TrackingService.EXTRA_SNOOZE_METERS, extraMeters)
        )
    }

    fun consumeGoalReachedEvent() {
        _goalReachedEvent.value = false
    }

    suspend fun getSplitsForSession(sessionId: Long) =
        (getApplication<Application>() as RunTrackerApp).database.sessionDao().getSplitsForSession(sessionId)

    suspend fun getSession(sessionId: Long) =
        (getApplication<Application>() as RunTrackerApp).database.sessionDao().getSession(sessionId)

    override fun onCleared() {
        super.onCleared()
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
    }
}
