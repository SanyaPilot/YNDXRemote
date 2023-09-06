package com.sanyapilot.yandexstation_controller.device

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
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sanyapilot.yandexstation_controller.service.DEVICE_ID
import com.sanyapilot.yandexstation_controller.service.DEVICE_NAME
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.FuckedQuasarClient
import com.sanyapilot.yandexstation_controller.api.UnlinkDeviceErrors
import com.sanyapilot.yandexstation_controller.service.StationControlService
import kotlin.concurrent.thread

class DeviceActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "DeviceActivity"
    }

    private val viewModel: DeviceViewModel by viewModels()
    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var deviceId: String

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
            updateUIonMetadataChange(metadata)

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
            updateUIonMetadataChange(metadata!!)
        }

        override fun onShuffleModeChanged(shuffleMode: Int) {
            viewModel.shuffleSupported.value = shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            viewModel.isPlaying.value = (state!!.state == PlaybackStateCompat.STATE_PLAYING)
            viewModel.hasNext.value = PlaybackStateCompat.ACTION_SKIP_TO_NEXT and state.actions == PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            viewModel.hasPrev.value = PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS and state.actions == PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }

        override fun onSessionDestroyed() {
            mediaBrowser.disconnect()
            finish()
        }
    }

    private fun updateUIonMetadataChange(metadata: MediaMetadataCompat) {
        // Bad way to detect idle state
        val title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
        val subtitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        val idle = ((title == getString(R.string.fetchingData) || title == getString(R.string.idle)) && subtitle == intent.getStringExtra("deviceName"))
        viewModel.playerActive.value = !idle
        if (idle) {
            viewModel.shuffleSupported.value = false
            viewModel.likeSupported.value = false
            viewModel.hasNext.value = false
            viewModel.hasPrev.value = false
        } else {
            val mediaController = MediaControllerCompat.getMediaController(this)
            viewModel.shuffleSupported.value = mediaController.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE
            viewModel.likeSupported.value = true
        }

        viewModel.trackName.value = title
        viewModel.trackArtist.value = subtitle
        viewModel.progressMax.value = (metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) / 1000).toInt()
        viewModel.coverURL.value = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
        viewModel.coverBitmap.value = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        deviceId = intent.getStringExtra("deviceId")!!
        val deviceName = intent.getStringExtra("deviceName")

        val appBar = findViewById<MaterialToolbar>(R.id.deviceAppBar)
        appBar?.let {
            setSupportActionBar(appBar)
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setDisplayShowHomeEnabled(true)
            appBar.subtitle = deviceName
        }

        // Supply device ID and device name to the service
        val hints = Bundle()
        hints.apply {
            putString(DEVICE_ID, deviceId)
            putString(DEVICE_NAME, deviceName)
        }
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, StationControlService::class.java),
            connectionCallbacks,
            hints
        )

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
            if (isChecked) {
                when (checkedId) {
                    R.id.playbackButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DevicePlaybackFragment>(R.id.controlsContainer)
                        }
                    }
                    R.id.TTSButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DeviceTTSFragment>(R.id.controlsContainer)
                        }
                    }
                    R.id.remoteButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DeviceRemoteFragment>(R.id.controlsContainer)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        // Start StationControlService
        startService(Intent(this, StationControlService::class.java))

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.device_actions, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.unlinkDeviceItem -> {  // Show unlink dialog
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unlinkQuestion)
                .setMessage(R.string.unlinkDescription)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    thread {
                        val res = FuckedQuasarClient.unlinkDevice(deviceId)
                        if (res.ok) {
                            // Stop service and finish activity
                            FuckedQuasarClient.fetchDevices()
                            mediaController.transportControls.stop()
                        } else {
                            Snackbar.make(
                                findViewById(R.id.deviceLayout),
                                getString(when (res.error) {
                                    UnlinkDeviceErrors.NOT_LINKED -> R.string.deviceAlreadyUnlinked
                                    UnlinkDeviceErrors.UNAUTHORIZED -> R.string.unauthorizedError
                                    UnlinkDeviceErrors.TIMEOUT -> R.string.unknownError
                                    UnlinkDeviceErrors.UNKNOWN -> R.string.unknownError
                                    else -> R.string.wtf
                                }),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}