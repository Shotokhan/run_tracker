package com.example.runtracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.runtracker.MainActivity
import com.example.runtracker.RunTrackerApp
import com.example.runtracker.core.Split
import com.example.runtracker.core.TrackCalculator
import com.example.runtracker.core.TrackSnapshot
import com.example.runtracker.data.SessionEntity
import com.example.runtracker.data.SplitEntity
import com.example.runtracker.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.os.Build

class TrackingService : LifecycleService() {

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    private val calculator = TrackCalculator()
    private lateinit var locationManager: LocationManager

    private val _state = MutableStateFlow(TrackSnapshot())
    val state: StateFlow<TrackSnapshot> = _state.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var sessionStartEpoch: Long = 0L
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    private val locationListener = LocationListener { location -> onNewLocation(location) }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannels()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val goal = if (intent.hasExtra(EXTRA_GOAL_METERS)) intent.getDoubleExtra(EXTRA_GOAL_METERS, 0.0) else null
                beginTracking(goal)
            }
            ACTION_STOP -> stopTracking()
            ACTION_SNOOZE -> {
                val extra = intent.getDoubleExtra(EXTRA_SNOOZE_METERS, 500.0)
                snoozeGoal(extra)
            }
        }
        return START_STICKY
    }

    // ---- Public control surface (used by bound ViewModel) ----

    fun beginTracking(goalMeters: Double?) {
        if (_isTracking.value) return
        sessionStartEpoch = System.currentTimeMillis()
        calculator.start(System.currentTimeMillis(), goalMeters)
        _isTracking.value = true
        _state.value = calculator.heartbeat(System.currentTimeMillis())

        startForeground(FOREGROUND_NOTIF_ID, buildForegroundNotification(_state.value))
        startLocationUpdates()
        startHeartbeat()
    }

    fun stopTracking() {
        if (!_isTracking.value) return
        val now = System.currentTimeMillis()
        stopLocationUpdates()
        heartbeatJob?.cancel()

        val finalPartial = calculator.finalPartialSplit(now)
        val allSplits = calculator.allSplits() + listOfNotNull(finalPartial)
        val finalSnapshot = calculator.heartbeat(now)
        val endEpoch = now

        persistSession(
            startEpoch = sessionStartEpoch,
            endEpoch = endEpoch,
            distanceMeters = finalSnapshot.totalDistanceMeters,
            durationMillis = finalSnapshot.elapsedMillis,
            avgPaceSecPerKm = finalSnapshot.avgPaceSecPerKm,
            goalMeters = finalSnapshot.goalMeters,
            goalReached = finalSnapshot.goalReached,
            splits = allSplits
        )

        _isTracking.value = false
        _state.value = TrackSnapshot()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun snoozeGoal(extraMeters: Double) {
        if (!_isTracking.value) return
        calculator.snoozeGoal(extraMeters)
        _state.value = calculator.heartbeat(System.currentTimeMillis())
        cancelNotification(GOAL_NOTIF_ID)
    }

    // ---- Internals ----

    private fun onNewLocation(location: Location) {
        val snapshot = calculator.onLocation(location, System.currentTimeMillis())
        _state.value = snapshot
        if (calculator.consumeGoalReachedEvent()) {
            notifyGoalReached(snapshot)
        }
    }

    private fun startLocationUpdates() {
        val hasFine = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine) return
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_UPDATE_INTERVAL_MS,
                0f,
                locationListener
            )
        } catch (e: SecurityException) {
            // Permission revoked between check and call; nothing more we can do here.
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: SecurityException) {
            // no-op
        }
    }

    private fun startHeartbeat() {
        heartbeatJob = lifecycleScope.launch {
            while (_isTracking.value) {
                delay(1000)
                if (!_isTracking.value) break
                val snapshot = calculator.heartbeat(System.currentTimeMillis())
                _state.value = snapshot
                updateForegroundNotification(snapshot)
            }
        }
    }

    private fun persistSession(
        startEpoch: Long,
        endEpoch: Long,
        distanceMeters: Double,
        durationMillis: Long,
        avgPaceSecPerKm: Double,
        goalMeters: Double?,
        goalReached: Boolean,
        splits: List<Split>
    ) {
        val db = (application as RunTrackerApp).database
        lifecycleScope.launch(Dispatchers.IO) {
            val entity = SessionEntity(
                startEpochMillis = startEpoch,
                endEpochMillis = endEpoch,
                distanceMeters = distanceMeters,
                durationMillis = durationMillis,
                avgPaceSecPerKm = avgPaceSecPerKm,
                goalMeters = goalMeters,
                goalWasReached = goalReached
            )
            val splitEntities = splits.map {
                SplitEntity(
                    sessionId = 0, // overwritten in insertSessionWithSplits
                    splitIndex = it.index,
                    distanceMeters = it.distanceMeters,
                    durationMillis = it.durationMillis,
                    paceSecPerKm = it.paceSecPerKm
                )
            }
            db.sessionDao().insertSessionWithSplits(entity, splitEntities)
        }
    }

    // ---- Notifications ----

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TRACKING, "Active run", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing run tracking status"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Goal reached", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when your distance goal is reached"
            }
        )
    }

    private fun buildForegroundNotification(snapshot: TrackSnapshot): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Run in progress \u00b7 ${Formatters.distance(snapshot.totalDistanceMeters)}")
            .setContentText("${Formatters.duration(snapshot.elapsedMillis)}  \u00b7  ${Formatters.pace(snapshot.avgPaceSecPerKm)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun updateForegroundNotification(snapshot: TrackSnapshot) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(FOREGROUND_NOTIF_ID, buildForegroundNotification(snapshot))
    }

    private fun notifyGoalReached(snapshot: TrackSnapshot) {
        vibrate()
        val snoozeIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TrackingService::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_SNOOZE_METERS, 500.0),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAppIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Goal reached!")
            .setContentText("You've covered ${Formatters.distance(snapshot.totalDistanceMeters)}. Snooze for 500 m more, or stop.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze +500m", snoozeIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
        getSystemService(NotificationManager::class.java).notify(GOAL_NOTIF_ID, notification)
    }

    private fun cancelNotification(id: Int) {
        getSystemService(NotificationManager::class.java).cancel(id)
    }

    private fun vibrate() {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
    }

    companion object {
        const val ACTION_START = "com.example.runtracker.action.START"
        const val ACTION_STOP = "com.example.runtracker.action.STOP"
        const val ACTION_SNOOZE = "com.example.runtracker.action.SNOOZE"
        const val EXTRA_GOAL_METERS = "extra_goal_meters"
        const val EXTRA_SNOOZE_METERS = "extra_snooze_meters"

        private const val CHANNEL_TRACKING = "tracking_channel"
        private const val CHANNEL_ALERTS = "alerts_channel"
        private const val FOREGROUND_NOTIF_ID = 1
        private const val GOAL_NOTIF_ID = 2
        private const val MIN_UPDATE_INTERVAL_MS = 1000L
    }
}
