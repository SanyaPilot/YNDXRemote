package com.sanyapilot.yandexstation_controller

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.sanyapilot.yandexstation_controller.fragments.DeviceViewModel
import com.sanyapilot.yandexstation_controller.fragments.devices.DevicePlaybackFragment
import com.sanyapilot.yandexstation_controller.fragments.devices.DeviceTTSFragment
import com.sanyapilot.yandexstation_controller.fragments.devices.PlaybackInfoObservers
import kotlin.concurrent.thread

class DeviceActivity : AppCompatActivity() {
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
            val mediaController = MediaControllerCompat.getMediaController(this@DeviceActivity)

            // Fill UI initially
            val metadata = mediaController.metadata
            viewModel.trackName.value = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            viewModel.trackArtist.value = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            viewModel.progressMax.value = (metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000).toInt()
            viewModel.coverURL.value = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
            viewModel.coverBitmap.value = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            viewModel.isPlaying.value = (mediaController.playbackState.state == PlaybackStateCompat.STATE_PLAYING)

            // Update progress bar
            thread(start = true) {
                while (true) {
                    runOnUiThread {
                        viewModel.progress.value = (mediaController.playbackState.position / 1000).toInt()
                    }
                    Thread.sleep(250)
                }
            }

            mediaController.registerCallback(controllerCallback)
            viewModel.isReady.value = true
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
            Log.d(TAG, "Service has crashed!")
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
        }
    }

    private var controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            viewModel.trackName.value = metadata!!.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            viewModel.trackArtist.value = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            viewModel.progressMax.value = (metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000).toInt()
            viewModel.coverURL.value = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
            viewModel.coverBitmap.value = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            viewModel.isPlaying.value = (state!!.state == PlaybackStateCompat.STATE_PLAYING)
        }

        override fun onSessionDestroyed() {
            mediaBrowser.disconnect()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        // Start StationControlService
        startService(
            Intent(this, StationControlService::class.java).apply {
                putExtra(DEVICE_ID, intent.getStringExtra("deviceId"))
                putExtra(DEVICE_NAME, intent.getStringExtra("deviceName"))
            }
        )
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, StationControlService::class.java),
            connectionCallbacks,
            null
        )

        val appBar = findViewById<MaterialToolbar>(R.id.deviceAppBar)
        appBar?.let { appBar.subtitle = intent.getStringExtra("deviceName") }

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val coverImage = findViewById<ImageView>(R.id.cover)
            val trackName = findViewById<TextView>(R.id.trackName)
            val trackArtist = findViewById<TextView>(R.id.trackArtist)

            // ViewModel observers here
            val observers = PlaybackInfoObservers(viewModel, applicationContext)
            viewModel.playerActive.observe(this) { observers.playerActiveObserver(coverImage, it) }
            viewModel.trackName.observe(this) { observers.trackNameObserver(trackName, it) }
            viewModel.trackArtist.observe(this) { observers.trackArtistObserver(trackArtist, it) }
            viewModel.coverBitmap.observe(this) { observers.coverObserver(coverImage, it, viewModel.coverURL.value) }
        }

        val controlSelector = findViewById<MaterialButtonToggleGroup>(R.id.controlsSelector)

        // Bottom selector listener
        controlSelector.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (checkedId == R.id.playbackButton && isChecked) {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace<DevicePlaybackFragment>(R.id.controlsContainer)
                }
            } else if (checkedId == R.id.TTSButton && isChecked) {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace<DeviceTTSFragment>(R.id.controlsContainer)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    public override fun onStop() {
        super.onStop()
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }
}