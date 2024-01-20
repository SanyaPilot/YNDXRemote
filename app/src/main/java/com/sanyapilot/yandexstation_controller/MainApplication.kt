package com.sanyapilot.yandexstation_controller

import android.app.Application
import android.content.Context
import com.google.android.material.color.DynamicColors
import com.sanyapilot.yandexstation_controller.api.Session

class
MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply dynamic color
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Load token from storage (if exists)
        val sharedPrefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        if (sharedPrefs.contains("xToken")) Session.xToken = sharedPrefs.getString("xToken", null)!!
    }
}