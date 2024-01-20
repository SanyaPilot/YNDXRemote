package com.sanyapilot.yandexstation_controller.main_screen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.FuckedQuasarClient
import com.sanyapilot.yandexstation_controller.api.Session
import com.sanyapilot.yandexstation_controller.api.mDNSWorker
import kotlin.concurrent.thread

const val TOKEN_INVALID = "com.sanyapilot.yandexstation_controller.tokenInvalid"
const val PLAYER_CHANNEL_ID = "com.sanyapilot.yandexstation_controller.yast_control"

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var bottomNavigation: NavigationBarView
    private lateinit var loadingImage: ImageView
    private lateinit var container: FragmentContainerView
    private lateinit var title: TextView
    private lateinit var sharedPrefs: SharedPreferences

    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register notification channel
        createNotificationChannel()

        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        sharedPrefs = getSharedPreferences("auth", Context.MODE_PRIVATE)

        // Initialize views
        progressBar = findViewById(R.id.mainLoadingBar)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        loadingImage = findViewById(R.id.loadingImage)
        container = findViewById(R.id.mainFragmentContainer)
        title = findViewById(R.id.mainAppBarTitle)

        // Check if we need auth
        if (!sharedPrefs.contains("xToken")) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        if (viewModel.isLoggedIn()) {
            progressBar.visibility = View.GONE
            loadingImage.visibility = View.GONE
            bottomNavigation.visibility = View.VISIBLE
        }

        // Bottom navbar listener
        bottomNavigation.selectedItemId = R.id.devicesPage
        bottomNavigation.setOnItemSelectedListener { item ->
            // Animations
            title.animate().apply {
                duration = 200
                alpha(0f)
                start()
            }
            container.animate().apply {
                duration = 200
                alpha(0f)
                withEndAction {
                    Log.d(TAG, "Replacing frag!")
                    when (item.itemId) {
                        R.id.devicesPage -> {
                            supportFragmentManager.commit {
                                setReorderingAllowed(true)
                                replace<DevicesFragment>(R.id.mainFragmentContainer)
                            }
                        }
                        R.id.accountPage -> {
                            supportFragmentManager.commit {
                                setReorderingAllowed(true)
                                replace<UserFragment>(R.id.mainFragmentContainer)
                            }
                        }
                    }
                    title.animate().apply {
                        duration = 200
                        alpha(1f)
                        start()
                    }
                    container.animate().apply {
                        duration = 200
                        alpha(1f)
                        start()
                    }
                }
                start()
            }
            true
        }
        bottomNavigation.setOnItemReselectedListener {}

        // Authorize with saved access token
        if (!viewModel.isLoggedIn()) doNetwork()
    }

    override fun onRestart() {
        // Handle cookie update after returning from LoginActivity
        super.onRestart()
        if (!viewModel.isLoggedIn()) {
            doNetwork()
        } else {
            viewModel.setLoggedIn(true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (mDNSWorker.isReady())
            thread(start = true) { startNSD() }
    }
    fun fetchDevices() : Boolean {
        val result = FuckedQuasarClient.fetchDevices()
        if (!result.ok) {
            if (result.errorId == Errors.TIMEOUT) {
                Log.d(TAG, "timeout")
                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.mainLayout), getString(R.string.errorNoInternet),
                        Snackbar.LENGTH_INDEFINITE
                    ).show()
                }
            } else if (result.errorId == Errors.INVALID_TOKEN) {
                Log.d(TAG, "token auth fail")
                runOnUiThread {
                    with (sharedPrefs.edit()) {
                        remove("xToken")
                        commit()
                    }
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                            .putExtra(TOKEN_INVALID, true)
                    )
                }
            } else if (result.errorId == Errors.INTERNAL_SERVER_ERROR) {
                Log.d(TAG, "Internal server error!")
                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.mainLayout), getString(R.string.serverDead),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
        return result.ok
    }
    private fun doNetwork() {
        title.text = getString(R.string.loading)
        progressBar.visibility = View.VISIBLE
        loadingImage.visibility = View.VISIBLE
        // Prepare speakers
        thread(start = true) {
            if (!fetchDevices()) {
                return@thread
            }
            runOnUiThread {
                viewModel.setLoggedIn(true)
                progressBar.animate().apply {
                    duration = 200
                    alpha(0f)
                    withEndAction {
                        // Remove loading progress bar from layout
                        progressBar.visibility = View.GONE

                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DevicesFragment>(R.id.mainFragmentContainer)
                        }

                        // Start fade in animations for elements
                        val fadeIn = AlphaAnimation(0f, 1f)
                        fadeIn.duration = 200

                        container.visibility = View.VISIBLE
                        container.startAnimation(fadeIn)
                        bottomNavigation.visibility = View.VISIBLE
                        bottomNavigation.startAnimation(fadeIn)
                        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this@MainActivity)
                    }
                    start()
                }

                loadingImage.animate().apply {
                    duration = 200
                    alpha(0f)
                    withEndAction {
                        loadingImage.visibility = View.GONE
                    }
                    start()
                }
            }
            mDNSWorker.init(this)
            startNSD()
        }
    }
    private fun startNSD() {
        // Start NSD only if WiFi enabled
        val netCaps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (wifiManager.isWifiEnabled && netCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            mDNSWorker.start()
        }
    }
    fun logOut(view: View) {
        // Logout action, starting LoginActivity
        with (sharedPrefs.edit()) {
            remove("xToken")
            commit()
        }
        Session.clearAllCookies()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
    private fun createNotificationChannel() {
        val name = "Station player"
        val descriptionText = "Control media on station"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(PLAYER_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        if (!notificationManager.areNotificationsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Request a permission
            val requestPermissionLauncher =
                registerForActivityResult(RequestPermission()
                ) { isGranted: Boolean ->
                    if (isGranted) {
                        // Permission is granted. Continue the action or workflow in your
                        // app.
                    } else {
                        // Explain to the user that the feature is unavailable because the
                        // feature requires a permission that the user has denied. At the
                        // same time, respect the user's decision. Don't link to system
                        // settings in an effort to convince the user to change their
                        // decision.
                    }
                }

            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}