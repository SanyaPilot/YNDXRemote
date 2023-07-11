package com.sanyapilot.yandexstation_controller

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
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.FuckedQuasarClient
import com.sanyapilot.yandexstation_controller.api.mDNSWorker
import com.sanyapilot.yandexstation_controller.fragments.DevicesFragment
import com.sanyapilot.yandexstation_controller.fragments.NoWiFiFragment
import com.sanyapilot.yandexstation_controller.fragments.UserFragment
import kotlin.concurrent.thread

const val TOKEN_INVALID = "com.sanyapilot.yandexstation_controller.tokenInvalid"
const val SWITCH_ANIM_DURATION: Long = 150
const val PLAYER_CHANNEL_ID = "yast_control"

class MainActivity : AppCompatActivity() {
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

        Log.e(TAG, "STARTED MAIN")

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

        val netCaps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (wifiManager.isWifiEnabled && netCaps != null && netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            initUI()
        } else {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace<NoWiFiFragment>(R.id.mainFragmentContainer)
            }
        }
    }

    private fun initUI() {
        Log.d(TAG, "Init UI")
        // Check if we need auth
        if (!sharedPrefs.contains("access-token")) {
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
            val fadeOut = AlphaAnimation(1f, 0f)
            fadeOut.interpolator = AccelerateInterpolator()
            fadeOut.duration = SWITCH_ANIM_DURATION

            val fadeIn = AlphaAnimation(0f, 1f)
            fadeIn.interpolator = AccelerateInterpolator()
            fadeIn.duration = SWITCH_ANIM_DURATION

            fadeOut.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(p0: Animation?) {
                }

                override fun onAnimationEnd(p0: Animation?) {
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

                    container.startAnimation(fadeIn)
                    title.startAnimation(fadeIn)
                }

                override fun onAnimationRepeat(p0: Animation?) {
                }
            })

            val fadeOutText = AlphaAnimation(1f, 0f)
            fadeOutText.interpolator = AccelerateInterpolator()
            fadeOutText.duration = SWITCH_ANIM_DURATION

            // Start switching from fade out animation
            title.startAnimation(fadeOutText)
            container.startAnimation(fadeOut)

            true
        }
        bottomNavigation.setOnItemReselectedListener {}

        // Authorize with saved access token
        if (!viewModel.isLoggedIn()) doNetwork()
    }

    fun retryInitUI(view: View) {
        Log.d(TAG, "Retrying UI init!")
        val netCaps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (wifiManager.isWifiEnabled && netCaps != null && netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            // Fade out fragments container
            val fadeOutContainer = AlphaAnimation(1f, 0f)
            fadeOutContainer.interpolator = AccelerateInterpolator()
            fadeOutContainer.duration = 200
            fadeOutContainer.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(p0: Animation?) {
                }

                override fun onAnimationEnd(p0: Animation?) {
                    container.visibility = View.INVISIBLE
                    initUI()
                }

                override fun onAnimationRepeat(p0: Animation?) {
                }
            })
            container.startAnimation(fadeOutContainer)
        } else {
            Snackbar.make(
                findViewById(R.id.mainLayout), getString(R.string.noWiFi),
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    override fun onRestart() {
        // Handle cookie update after returning from LoginActivity
        super.onRestart()
        Log.e(TAG, "onRestart()")
        Log.e(TAG, viewModel.isLoggedIn().toString())
        if (!viewModel.isLoggedIn()) {
            doNetwork()
        } else {
            viewModel.setLoggedIn(true)
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.isLoggedIn())
            thread(start = true) { mDNSWorker.stop() }
    }

    override fun onResume() {
        super.onResume()
        if (mDNSWorker.isReady())
            thread(start = true) { mDNSWorker.start() }
    }
    fun fetchDevices() {
        val result = FuckedQuasarClient.fetchDevices()
        if (!result.ok) {
            if (result.errorId == Errors.TIMEOUT) {
                Log.e(TAG, "timeout")
                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.mainLayout), getString(R.string.errorNoInternet),
                        Snackbar.LENGTH_INDEFINITE
                    ).show()
                }
            } else if (result.errorId == Errors.INVALID_TOKEN) {
                Log.e(TAG, "token auth fail")
                runOnUiThread {
                    with (sharedPrefs.edit()) {
                        remove("access-token")
                        commit()
                    }
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                            .putExtra(TOKEN_INVALID, true)
                    )
                }
            } else if (result.errorId == Errors.INTERNAL_SERVER_ERROR) {
                Log.e(TAG, "Internal server error!")
                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.mainLayout), getString(R.string.serverDead),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    private fun doNetwork() {
        title.text = getString(R.string.loading)
        progressBar.visibility = View.VISIBLE
        loadingImage.visibility = View.VISIBLE
        // Prepare speakers
        thread(start = true) {
            fetchDevices()
            runOnUiThread {
                viewModel.setLoggedIn(true)
                val fadeOut = AlphaAnimation(1f, 0f)
                fadeOut.interpolator = AccelerateInterpolator()
                fadeOut.duration = 200

                fadeOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(p0: Animation?) {
                    }

                    override fun onAnimationEnd(p0: Animation?) {
                        // Remove loading progress bar from layout
                        progressBar.visibility = View.GONE

                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace<DevicesFragment>(R.id.mainFragmentContainer)
                        }

                        // Start fade in animations for elements
                        val fadeIn = AlphaAnimation(0f, 1f)
                        fadeIn.interpolator = AccelerateInterpolator()
                        fadeIn.duration = 200

                        container.visibility = View.VISIBLE
                        container.startAnimation(fadeIn)
                        bottomNavigation.visibility = View.VISIBLE
                        bottomNavigation.startAnimation(fadeIn)
                    }

                    override fun onAnimationRepeat(p0: Animation?) {
                    }
                })
                progressBar.startAnimation(fadeOut)

                val fadeOutImage = AlphaAnimation(1f, 0f)
                fadeOutImage.interpolator = AccelerateInterpolator()
                fadeOutImage.duration = 200
                fadeOutImage.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(p0: Animation?) {
                    }

                    override fun onAnimationEnd(p0: Animation?) {
                        loadingImage.visibility = View.GONE
                    }

                    override fun onAnimationRepeat(p0: Animation?) {
                    }
                })

                loadingImage.startAnimation(fadeOutImage)
            }
            mDNSWorker.init(this)
        }
    }
    fun logOut(view: View) {
        // Logout action, starting LoginActivity
        with (sharedPrefs.edit()) {
            remove("access-token")
            commit()
        }
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