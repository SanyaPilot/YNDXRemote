package com.sanyapilot.yandexstation_controller

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.*
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.QuasarClient
import com.sanyapilot.yandexstation_controller.api.Session
import com.sanyapilot.yandexstation_controller.api.mDNSWorker
import com.sanyapilot.yandexstation_controller.fragments.DevicesFragment
import com.sanyapilot.yandexstation_controller.fragments.UserFragment
import kotlin.concurrent.thread

const val TOKEN_INVALID = "com.sanyapilot.yandexstation_controller.tokenInvalid"
const val SWITCH_ANIM_DURATION: Long = 150

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var bottomNavigation: NavigationBarView
    private lateinit var loadingImage: ImageView
    private lateinit var container: FragmentContainerView
    private lateinit var title: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.e(TAG, "STARTED MAIN")

        // Initialize views
        progressBar = findViewById(R.id.mainLoadingBar)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        loadingImage = findViewById(R.id.loadingImage)
        container = findViewById(R.id.mainFragmentContainer)
        title = findViewById(R.id.mainAppBarTitle)

        if (viewModel.isLoggedIn() == true) {
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

        // Check if we need auth
        val sharedPrefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        if (!sharedPrefs.contains("x-token")) {
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        // Authorize with saved x-token
        if (savedInstanceState == null) doNetwork()
        else viewModel.setLoggedIn(true)
    }

    override fun onRestart() {
        // Handle cookie update after returning from LoginActivity
        super.onRestart()
        Log.e(TAG, "onRestart()")
        Log.e(TAG, viewModel.isLoggedIn()!!.toString())
        if (!viewModel.isLoggedIn()!!) {
            doNetwork()
        }
    }
    private fun doNetwork() {
        // Refresh cookies and push default fragment to layout
        // Prepare speakers
        thread(start = true) {
            val result = Session.refreshCookies()
            Log.e(TAG, "refreshed cookies")
            if (result.errorId == Errors.TIMEOUT) {
                Log.e(TAG, "timeout")
                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.mainLayout), getString(R.string.errorNoInternet),
                        Snackbar.LENGTH_INDEFINITE
                    ).show()
                }
            } else if (result.errorId == Errors.TOKEN_AUTH_FAILED) {
                Log.e(TAG, "token auth fail")
                runOnUiThread {
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                            .putExtra(TOKEN_INVALID, true)
                    )
                }
            }
            Log.e(TAG, "done all checks")
            QuasarClient.prepareSpeakers()

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
                            add<DevicesFragment>(R.id.mainFragmentContainer)
                        }

                        // Start fade in animations for elements
                        val fadeIn = AlphaAnimation(0f, 1f)
                        fadeIn.interpolator = AccelerateInterpolator()
                        fadeIn.duration = 200

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
        val sharedPrefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        Log.e(TAG, "TOKEN B4 CLEARING: ${sharedPrefs.getString("x-token", null)}")
        with (sharedPrefs.edit()) {
            remove("x-token")
            commit()
        }
        Session.clearAllCookies()
        Log.e(TAG, "TOKEN AFTER CLEARING: ${sharedPrefs.getString("x-token", null)}")
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}