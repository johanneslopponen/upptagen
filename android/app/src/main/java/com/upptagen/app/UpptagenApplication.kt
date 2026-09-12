package com.upptagen.app

import android.app.Application
import com.upptagen.app.data.AppDatabase
import com.upptagen.app.data.repository.TimeTrackingRepository

class UpptagenApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val repository: TimeTrackingRepository by lazy {
        TimeTrackingRepository(database)
    }
}
