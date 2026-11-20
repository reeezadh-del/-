package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.ProxyProfileRepository
import com.example.data.SettingsRepository

class SeparApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val profileRepository: ProxyProfileRepository by lazy {
        ProxyProfileRepository(database.proxyProfileDao())
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }
}
