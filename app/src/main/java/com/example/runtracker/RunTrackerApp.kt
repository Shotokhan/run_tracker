package com.example.runtracker

import android.app.Application
import com.example.runtracker.data.AppDatabase

class RunTrackerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
