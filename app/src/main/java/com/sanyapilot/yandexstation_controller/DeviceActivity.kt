package com.sanyapilot.yandexstation_controller

import android.content.ComponentName
import android.content.res.Configuration
import android.media.AudioManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.sanyapilot.yandexstation_controller.fragments.DeviceViewModel
import com.sanyapilot.yandexstation_controller.fragments.devices.PlaybackInfoObservers

class DeviceActivity : AppCompatActivity() {
    //lateinit var station: YandexStation
    private val viewModel: DeviceViewModel by viewModels()
    private lateinit var mediaBrowser: MediaBrowserCompat

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            // Get the token for the MediaSession
            mediaBrowser.sessionToken.also { token ->
                // Create a MediaControllerCompat
                val mediaController = MediaControllerCompat(
                    this@DeviceActivity, // Context
                    token
                )
                // Save the controller
                MediaControllerCompat.setMediaController(this@DeviceActivity, mediaController)
            }
            // Finish building the UI
            //buildTransportControls()
            val mediaController = MediaControllerCompat.getMediaController(this@DeviceActivity)
            //mediaController.transportControls.play()
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        // Start StationControlService
        val serviceBundle = Bundle()
        serviceBundle.putString("deviceId", intent.getStringExtra("deviceId"))
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, StationControlService::class.java),
            connectionCallbacks,
            serviceBundle
        )

        /*val deviceId = intent.getStringExtra("deviceId")
        val speaker = QuasarClient.getSpeakerById(deviceId!!)!!

        if (viewModel.station.value == null) {
            station = YandexStation(
                this,
                speaker = speaker,
                client = GlagolClient(speaker),
                viewModel = viewModel
            )
            viewModel.station.value = station
        } else {
            station = viewModel.station.value!!
        }*/

        val appBar = findViewById<MaterialToolbar>(R.id.deviceAppBar)
        appBar?.let { appBar.subtitle = intent.getStringExtra("deviceName") }

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val coverImage = findViewById<ImageView>(R.id.cover)
            val trackName = findViewById<TextView>(R.id.trackName)
            val trackArtist = findViewById<TextView>(R.id.trackArtist)

            // ViewModel observers here
            val observers = PlaybackInfoObservers(viewModel, applicationContext)
            viewModel.isLocal.observe(this) { observers.isLocalObserver(trackName, trackArtist, coverImage, it) }
            viewModel.playerActive.observe(this) { observers.playerActiveObserver(coverImage, it) }
            viewModel.trackName.observe(this) { observers.trackNameObserver(trackName, it) }
            viewModel.trackArtist.observe(this) { observers.trackArtistObserver(trackArtist, it) }
            viewModel.coverURL.observe(this) { observers.coverObserver(coverImage, it) }
        }

        val controlSelector = findViewById<MaterialButtonToggleGroup>(R.id.controlsSelector)

        // Bottom selector listener
        /*controlSelector.addOnButtonCheckedListener { _, checkedId, isChecked ->
            Log.d(TAG, "Checked id: $checkedId")
            if (checkedId == R.id.playbackButton && isChecked) {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    Log.e(TAG, "Adding controls fragment!")
                    replace<DevicePlaybackFragment>(R.id.controlsContainer)
                }
            } else if (checkedId == R.id.TTSButton && isChecked) {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace<DeviceTTSFragment>(R.id.controlsContainer)
                }
            }
        }*/
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun onStop() {
        super.onStop()
        //mediaBrowser.disconnect()
    }
    /*override fun onStop() {
        super.onStop()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }*/

    /*override fun onDestroy() {
        if (isFinishing) {
            station.endLocal()
        } else {
            if (viewModel.isLocal.value == true) {
                viewModel.prevCoverURL.value = null
                viewModel.prevTrackName.value = null
                viewModel.prevTrackArtist.value = null
            }
        }

        super.onDestroy()
    }*/
}