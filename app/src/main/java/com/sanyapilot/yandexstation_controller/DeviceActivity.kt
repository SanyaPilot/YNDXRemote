package com.sanyapilot.yandexstation_controller

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationBarView
import com.sanyapilot.yandexstation_controller.api.GlagolClient
import com.sanyapilot.yandexstation_controller.api.QuasarClient
import com.sanyapilot.yandexstation_controller.api.YandexStation
import com.sanyapilot.yandexstation_controller.fragments.DeviceViewModel
import com.sanyapilot.yandexstation_controller.fragments.devices.DevicePlaybackFragment
import com.sanyapilot.yandexstation_controller.fragments.devices.DeviceTTSFragment

class DeviceActivity : AppCompatActivity() {
    lateinit var station: YandexStation
    private val viewModel: DeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        val deviceId = intent.getStringExtra("deviceId")
        val speaker = QuasarClient.getSpeakerById(deviceId!!)!!
        station = YandexStation(
            this,
            speaker = speaker,
            client = GlagolClient(speaker),
            viewModel = viewModel
        )

        val appBar = findViewById<MaterialToolbar>(R.id.deviceAppBar)
        appBar.subtitle = intent.getStringExtra("deviceName")

        val bottomNavigation = findViewById<NavigationBarView>(R.id.deviceBottomNavigation)

        // Bottom navbar listener
        bottomNavigation.selectedItemId = R.id.accountPage
        bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.playbackPage -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<DevicePlaybackFragment>(R.id.deviceFragmentContainer)
                    }
                    true
                }
                R.id.TTSPage -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<DeviceTTSFragment>(R.id.deviceFragmentContainer)
                    }
                    true
                }
                else -> false
            }
        }
        bottomNavigation.setOnItemReselectedListener {}

        if (savedInstanceState == null) {
            Log.d(TAG, "Adding fragment")
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<DevicePlaybackFragment>(R.id.deviceFragmentContainer)
            }
        }
    }

    override fun onDestroy() {
        station.endLocal()
        super.onDestroy()
    }
}