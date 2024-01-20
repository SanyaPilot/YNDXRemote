package com.sanyapilot.yandexstation_controller.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.elevation.SurfaceColors
import com.sanyapilot.yandexstation_controller.service.DEVICE_ID
import com.sanyapilot.yandexstation_controller.service.DEVICE_NAME
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.mDNSWorker
import com.sanyapilot.yandexstation_controller.device.settings.SettingsFragment
import com.sanyapilot.yandexstation_controller.misc.stationsWithVideo
import com.sanyapilot.yandexstation_controller.service.DEVICE_PLATFORM
import com.sanyapilot.yandexstation_controller.service.StationControlService
import kotlin.concurrent.thread

class DeviceActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "DeviceActivity"
    }

    private val viewModel: DeviceViewModel by viewModels()
    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var deviceId: String
    private lateinit var devicePlatform: String
    private lateinit var deviceName: String

    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager

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
            goOffline(listen = false, showAnim = true)
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

        deviceId = intent.getStringExtra(DEVICE_ID)!!
        deviceName = intent.getStringExtra(DEVICE_NAME)!!
        devicePlatform = intent.getStringExtra(DEVICE_PLATFORM)!!

        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val appBar = findViewById<MaterialToolbar>(R.id.deviceAppBar)
        appBar?.let {
            setSupportActionBar(appBar)
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setDisplayShowHomeEnabled(true)
            appBar.subtitle = deviceName
        }

        val navBar: BottomNavigationView = findViewById(R.id.deviceNavigation)

        // Enable UI remote only for specific models
        if (stationsWithVideo.contains(devicePlatform)) {
            navBar.menu.getItem(1).isVisible = true
        }

        // Color navbar
        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)

        // Supply device ID and device name to the service
        val hints = Bundle()
        hints.apply {
            putString(DEVICE_ID, deviceId)
            putString(DEVICE_NAME, deviceName)
            putString(DEVICE_PLATFORM, devicePlatform)
        }
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, StationControlService::class.java),
            connectionCallbacks,
            hints
        )

        mDNSWorker.removeAllListeners(deviceId)
        if (mDNSWorker.getDevice(deviceId) != null) {
            goOnline(false)
        } else {
            goOffline(listen = true, showAnim = false)
        }
    }

    private fun goOffline(listen: Boolean, showAnim: Boolean) {
        if (listen) {
            mDNSWorker.addListener(deviceId) { runOnUiThread { goOnline(true) } }
            val netCaps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (wifiManager.isWifiEnabled && netCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                mDNSWorker.start()
            }
        }

        viewModel.removeObservers(this)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val coverImage = findViewById<ImageView>(R.id.cover)
            val trackName = findViewById<TextView>(R.id.trackName)
            val trackArtist = findViewById<TextView>(R.id.trackArtist)
            coverImage.visibility = View.GONE
            trackName.visibility = View.GONE
            trackArtist.visibility = View.GONE
        }

        // Portrait
        val navBar: BottomNavigationView? = findViewById(R.id.deviceNavigation)
        if (viewModel.selectedNavItem.value != null) {
            navBar?.selectedItemId = viewModel.selectedNavItem.value!!
        }
        if (navBar != null) {
            updateLandscapeSelectedItem(navBar.selectedItemId)
        }
        navBar?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.playbackPage -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<DeviceOfflineFragment>(R.id.controlsContainer, "DEVICE_OFFLINE")
                    }
                }

                R.id.rcPage -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<DeviceOfflineFragment>(R.id.controlsContainer, "DEVICE_OFFLINE")
                    }
                }

                R.id.settingsPage -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace(R.id.controlsContainer, SettingsFragment.instance(deviceId, devicePlatform), "DEVICE_SETTINGS")
                    }
                }
            }
            updateLandscapeSelectedItem(item.itemId)
            true
        }

        // Landscape
        val controlsSelector: MaterialButtonToggleGroup? = findViewById(R.id.controlsSelector)
        if (viewModel.checkedControlsItem.value != null) {
            controlsSelector?.check(viewModel.checkedControlsItem.value!!)
        }
        if (controlsSelector != null) {
            updatePortraitSelectedItem(controlsSelector.checkedButtonId)
        }
        controlsSelector?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.playbackButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DeviceOfflineFragment>(R.id.controlsContainer, "DEVICE_OFFLINE")
                        }
                    }

                    R.id.remoteButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DeviceOfflineFragment>(R.id.controlsContainer, "DEVICE_OFFLINE")
                        }
                    }

                    R.id.ttsButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DeviceOfflineFragment>(R.id.controlsContainer, "DEVICE_OFFLINE")
                        }
                    }

                    R.id.settingsButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace(R.id.controlsContainer, SettingsFragment.instance(deviceId, devicePlatform), "DEVICE_SETTINGS")
                        }
                    }
                }
                updatePortraitSelectedItem(checkedId)
            }
        }

        // Display offline screen
        if (supportFragmentManager.findFragmentByTag("DEVICE_SETTINGS")?.isVisible != true) {
            if (showAnim) {
                changeFragWithAnim(DeviceOfflineFragment(), "DEVICE_OFFLINE")
            } else {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace<DeviceOfflineFragment>(R.id.controlsContainer, "DEVICE_OFFLINE")
                }
            }
        }
    }

    private fun goOnline(afterOffline: Boolean) {
        // Stop scan
        mDNSWorker.removeAllListeners(deviceId)
        mDNSWorker.stop()

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val coverImage = findViewById<ImageView>(R.id.cover)
            val trackName = findViewById<TextView>(R.id.trackName)
            val trackArtist = findViewById<TextView>(R.id.trackArtist)

            // ViewModel observers here
            val observers = PlaybackInfoObservers(viewModel, applicationContext)
            viewModel.playerActive.observe(this) { observers.playerActiveObserver(coverImage, it) }
            viewModel.trackName.observe(this) { observers.trackNameObserver(trackName, it) }
            viewModel.trackArtist.observe(this) { observers.trackArtistObserver(trackArtist, it) }
            viewModel.coverBitmap.observe(this) {
                observers.coverObserver(
                    coverImage,
                    it,
                    viewModel.coverURL.value
                )
            }
        }

        // Portrait
        val navBar: BottomNavigationView? = findViewById(R.id.deviceNavigation)
        if (viewModel.selectedNavItem.value != null) {
            navBar?.selectedItemId = viewModel.selectedNavItem.value!!
        }
        if (navBar != null) {
            updateLandscapeSelectedItem(navBar.selectedItemId)
        }
        navBar?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.playbackPage -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<DevicePlaybackFragment>(R.id.controlsContainer, "DEVICE_PLAYBACK")
                    }
                }

                R.id.rcPage -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<DeviceRemoteFragment>(R.id.controlsContainer, "DEVICE_REMOTE")
                    }
                }

                R.id.settingsPage -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace(R.id.controlsContainer, SettingsFragment.instance(deviceId, devicePlatform), "DEVICE_SETTINGS")
                    }
                }
            }
            updateLandscapeSelectedItem(item.itemId)
            true
        }

        // Landscape
        val controlsSelector: MaterialButtonToggleGroup? = findViewById(R.id.controlsSelector)
        if (viewModel.checkedControlsItem.value != null) {
            controlsSelector?.check(viewModel.checkedControlsItem.value!!)
        }
        if (controlsSelector != null) {
            updatePortraitSelectedItem(controlsSelector.checkedButtonId)
        }
        controlsSelector?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.playbackButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DevicePlaybackFragment>(R.id.controlsContainer, "DEVICE_PLAYBACK")
                        }
                    }

                    R.id.remoteButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DeviceRemoteFragment>(R.id.controlsContainer, "DEVICE_REMOTE")
                        }
                    }

                    R.id.ttsButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DeviceTTSFragment>(R.id.controlsContainer, "DEVICE_TTS")
                        }
                    }

                    R.id.settingsButton -> {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace(R.id.controlsContainer, SettingsFragment.instance(deviceId, devicePlatform), "DEVICE_SETTINGS")
                        }
                    }
                }
                updatePortraitSelectedItem(checkedId)
            }
        }
        if (afterOffline) {
            startControlService()
            volumeControlStream = AudioManager.STREAM_MUSIC
            changeFragWithAnim(DevicePlaybackFragment(), "DEVICE_PLAYBACK")
            navBar?.selectedItemId = R.id.playbackPage
            controlsSelector?.uncheck(controlsSelector.checkedButtonId)
            controlsSelector?.check(R.id.playbackButton)
            updateLandscapeSelectedItem(R.id.playbackButton)
            updatePortraitSelectedItem(R.id.playbackPage)
        }
    }

    private fun changeFragWithAnim(fragment: Fragment, tag: String) {
        val container = findViewById<FragmentContainerView>(R.id.controlsContainer)
        val fadeOutContainer = AlphaAnimation(1f, 0f)
        fadeOutContainer.interpolator = AccelerateInterpolator()
        fadeOutContainer.duration = 200

        val fadeInContainer = AlphaAnimation(0f, 1f)
        fadeInContainer.interpolator = DecelerateInterpolator()
        fadeInContainer.duration = 200
        fadeInContainer.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                container.visibility = View.VISIBLE
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        fadeOutContainer.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                container.visibility = View.INVISIBLE
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(R.id.controlsContainer, fragment, tag)
                }
                container.startAnimation(fadeInContainer)
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        container.startAnimation(fadeOutContainer)
    }

    private fun startControlService() {
        // Start StationControlService
        startService(Intent(this, StationControlService::class.java))
        mediaBrowser.connect()
    }
    override fun onStart() {
        super.onStart()
        if (mDNSWorker.getDevice(deviceId) != null) {
            startControlService()
        }
    }

    override fun onResume() {
        super.onResume()
        if (mDNSWorker.getDevice(deviceId) != null) {
            volumeControlStream = AudioManager.STREAM_MUSIC
        }
    }

    public override fun onStop() {
        super.onStop()
        if (mDNSWorker.getDevice(deviceId) != null) {
            MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
            mediaBrowser.disconnect()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun updateLandscapeSelectedItem(id: Int) {
        viewModel.checkedControlsItem.value = when (id) {
            R.id.playbackPage -> R.id.playbackButton
            R.id.rcPage -> R.id.remoteButton
            R.id.settingsPage -> R.id.settingsButton
            else -> R.id.playbackButton
        }
    }

    private fun updatePortraitSelectedItem(id: Int) {
        viewModel.selectedNavItem.value = when (id) {
            R.id.playbackButton -> R.id.playbackPage
            R.id.remoteButton -> R.id.rcPage
            R.id.ttsButton -> R.id.rcPage
            R.id.settingsButton -> R.id.settingsPage
            else -> R.id.playbackPage
        }
    }
}