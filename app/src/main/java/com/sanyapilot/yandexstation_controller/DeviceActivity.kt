package com.sanyapilot.yandexstation_controller

import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowInsets
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.cardview.widget.CardView
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
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
        }

        val appBar = findViewById<MaterialToolbar>(R.id.deviceAppBar)
        appBar?.let { appBar.subtitle = intent.getStringExtra("deviceName") }

        // Picture card scaling
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            @Suppress("DEPRECATION") val screenHeight = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                windowMetrics.bounds.height() - insets.top - insets.bottom
            } else {
                val screenMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(screenMetrics)
                screenMetrics.heightPixels
            }

            val imageCard = findViewById<CardView>(R.id.imageCard)
            val sideLength = screenHeight / 2.3
            imageCard.layoutParams.height = sideLength.toInt()
            imageCard.layoutParams.width = sideLength.toInt()
        }

        val coverImage = findViewById<ImageView>(R.id.cover)
        val trackName = findViewById<TextView>(R.id.trackName)
        val trackArtist = findViewById<TextView>(R.id.trackArtist)

        // ViewModel observers here
        viewModel.isLocal.observe(this) {
            if (it) {
                //trackName.visibility = TextView.VISIBLE
                //trackArtist.visibility = TextView.VISIBLE
            } else {
                trackName.visibility = TextView.INVISIBLE
                trackArtist.visibility = TextView.INVISIBLE
                coverImage.setImageResource(R.drawable.ic_baseline_cloud_24)
            }
        }

        viewModel.playerActive.observe(this) {
            if (!it) {
                coverImage.setImageResource(R.drawable.ic_round_pause_on_surface_24)
            }
        }

        viewModel.trackName.observe(this) {
            if (it != viewModel.prevTrackName.value) {
                if (viewModel.prevTrackName.value == null) {
                    Log.d(com.sanyapilot.yandexstation_controller.api.TAG, "Setting visibility")
                    Log.d(com.sanyapilot.yandexstation_controller.api.TAG, "${viewModel.prevTrackName.value}")
                    trackName.visibility = TextView.VISIBLE
                }
                viewModel.prevTrackName.value = it
                animateText(trackName, it)
            }
        }

        viewModel.trackArtist.observe(this) {
            if (it != viewModel.prevTrackArtist.value) {
                if (viewModel.prevTrackArtist.value == null) {
                    trackArtist.visibility = TextView.VISIBLE
                }
                viewModel.prevTrackArtist.value = it
                animateText(trackArtist, it)
            }
        }

        viewModel.coverURL.observe(this) {
            if (it != null) {
                Log.d(TAG, "Img URL: $it")
                val curImageURL = "https://" + it.removeSuffix("%%") + "400x400"
                if (curImageURL != viewModel.prevCoverURL.value) {
                    viewModel.prevCoverURL.value = curImageURL

                    val fadeIn = AlphaAnimation(0f, 1f)
                    fadeIn.interpolator = DecelerateInterpolator()
                    fadeIn.duration = 200
                    val fadeOut = AlphaAnimation(1f, 0f)
                    fadeOut.interpolator = AccelerateInterpolator()
                    fadeOut.duration = 200

                    fadeOut.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationStart(p0: Animation?) {
                        }

                        override fun onAnimationEnd(p0: Animation?) {
                            coverImage.load(curImageURL)
                            val request = ImageRequest.Builder(applicationContext)
                                .data(curImageURL)
                                .target(coverImage)
                                .listener { _, _ ->
                                    coverImage.startAnimation(fadeIn)
                                }
                                .build()

                            imageLoader.enqueue(request)
                        }

                        override fun onAnimationRepeat(p0: Animation?) {
                        }
                    })
                    coverImage.startAnimation(fadeOut)
                }
            }
        }

        val controlSelector = findViewById<MaterialButtonToggleGroup>(R.id.controlsSelector)

        // Bottom selector listener
        controlSelector.addOnButtonCheckedListener { _, checkedId, isChecked ->
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
        }
    }

    private fun animateText(textView: TextView, value: String) {
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.interpolator = DecelerateInterpolator()
        fadeIn.duration = 200
        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.interpolator = AccelerateInterpolator()
        fadeOut.duration = 200

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
            }

            override fun onAnimationEnd(p0: Animation?) {
                textView.text = value
                textView.startAnimation(fadeIn)
            }

            override fun onAnimationRepeat(p0: Animation?) {
            }
        })
        textView.startAnimation(fadeOut)
    }

    override fun onDestroy() {
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
    }
}