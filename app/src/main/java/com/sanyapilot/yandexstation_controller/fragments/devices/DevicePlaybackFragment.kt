package com.sanyapilot.yandexstation_controller.fragments.devices

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.lifecycle.ViewModelProvider
import coil.imageLoader
import coil.request.ImageRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.sanyapilot.yandexstation_controller.DeviceActivity
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.TAG
import com.sanyapilot.yandexstation_controller.api.YandexStation
import com.sanyapilot.yandexstation_controller.fragments.DeviceViewModel


class DevicePlaybackFragment : Fragment() {
    private lateinit var viewModel: DeviceViewModel
    private lateinit var station: YandexStation
    private var orientation: Int = 0
    private var allowSliderChange = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device_playback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[DeviceViewModel::class.java]
        station = (activity as DeviceActivity).station
        orientation = resources.configuration.orientation

        val progressBar = requireView().findViewById<Slider>(R.id.progressBar)

        progressBar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                allowSliderChange = false
            }
            override fun onStopTrackingTouch(slider: Slider) {
                viewModel.progress.value = slider.value.toInt()
                allowSliderChange = true
                station.seek(slider.value.toInt())
            }
        })

        progressBar.setLabelFormatter { value: Float ->
            getMinutesSeconds(value.toInt())
        }

        // Picture card scaling
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            @Suppress("DEPRECATION") val screenHeight = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                val windowMetrics = requireActivity().windowManager.currentWindowMetrics
                val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                windowMetrics.bounds.height() - insets.top - insets.bottom
            } else {
                val screenMetrics = DisplayMetrics()
                requireActivity().windowManager.defaultDisplay.getMetrics(screenMetrics)
                screenMetrics.heightPixels
            }

            val imageCard = requireView().findViewById<CardView>(R.id.imageCard)
            val sideLength = screenHeight / 2.3
            imageCard.layoutParams.height = sideLength.toInt()
            imageCard.layoutParams.width = sideLength.toInt()
        }
    }

    override fun onResume() {
        super.onResume()

        val playButton = requireView().findViewById<MaterialButton>(R.id.playButton)
        val prevButton = requireView().findViewById<MaterialButton>(R.id.prevButton)
        val nextButton = requireView().findViewById<MaterialButton>(R.id.nextButton)
        val volUpButton = requireView().findViewById<MaterialButton>(R.id.volUpButton)
        val volDownButton = requireView().findViewById<MaterialButton>(R.id.volDownButton)
        val progressBar = requireView().findViewById<Slider>(R.id.progressBar)
        val curProgress = requireView().findViewById<TextView>(R.id.curProgress)
        val maxProgress = requireView().findViewById<TextView>(R.id.maxProgress)
        val coverImage = requireView().findViewById<ImageView>(R.id.cover)
        val trackName = requireView().findViewById<TextView>(R.id.trackName)
        val trackArtist = requireView().findViewById<TextView>(R.id.trackArtist)

        // ViewModel observers here
        viewModel.isLocal.observe(viewLifecycleOwner) {
            if (it && viewModel.playerActive.value == true) {
                progressBar.visibility = TextView.VISIBLE
                curProgress.visibility = TextView.VISIBLE
                maxProgress.visibility = TextView.VISIBLE
            } else {
                progressBar.visibility = TextView.GONE
                curProgress.visibility = TextView.GONE
                maxProgress.visibility = TextView.GONE
            }

            if (!it && orientation == Configuration.ORIENTATION_PORTRAIT) {
                trackName.visibility = TextView.INVISIBLE
                trackArtist.visibility = TextView.INVISIBLE
                coverImage.setImageResource(R.drawable.ic_baseline_cloud_24)
            }
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) {
            if (it)
                playButton.setIconResource(R.drawable.ic_round_pause_24)
            else
                playButton.setIconResource(R.drawable.ic_round_play_arrow_24)
        }

        viewModel.hasPrev.observe(viewLifecycleOwner) {
            prevButton.isEnabled = it
        }

        viewModel.hasNext.observe(viewLifecycleOwner) {
            nextButton.isEnabled = it
        }

        viewModel.playerActive.observe(viewLifecycleOwner) {
            if (it) {
                progressBar.visibility = TextView.VISIBLE
                curProgress.visibility = TextView.VISIBLE
                maxProgress.visibility = TextView.VISIBLE
            } else {
                progressBar.visibility = TextView.GONE
                curProgress.visibility = TextView.GONE
                maxProgress.visibility = TextView.GONE

                if (orientation == Configuration.ORIENTATION_PORTRAIT)
                    coverImage.setImageResource(R.drawable.ic_round_pause_on_surface_24)
            }
        }

        viewModel.progressMax.observe(viewLifecycleOwner) {
            if (it > 0) {
                maxProgress.text = getMinutesSeconds(it)
                progressBar.valueTo = it.toFloat()
            }
        }

        viewModel.progress.observe(viewLifecycleOwner) {
            curProgress.text = getMinutesSeconds(it)
            if (allowSliderChange)
                progressBar.value = it.toFloat()
        }

        // Observers for cover in portrait
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            val observers = PlaybackInfoObservers(viewModel, requireContext())
            viewModel.isLocal.observe(this) {
                observers.isLocalObserver(trackName, trackArtist, coverImage, it)
            }
            viewModel.playerActive.observe(this) { observers.playerActiveObserver(coverImage, it) }
            viewModel.trackName.observe(this) { observers.trackNameObserver(trackName, it) }
            viewModel.trackArtist.observe(this) { observers.trackArtistObserver(trackArtist, it) }
            viewModel.coverURL.observe(this) { observers.coverObserver(coverImage, it) }
        }

        playButton.setOnClickListener {
            if (viewModel.isPlaying.value == true)
                station.pause()
            else
                station.play()
        }

        prevButton.setOnClickListener {
            station.prevTrack()
        }

        nextButton.setOnClickListener {
            station.nextTrack()
        }

        volDownButton.setOnClickListener {
            station.decreaseVolume(10f)
        }

        volUpButton.setOnClickListener {
            station.increaseVolume(10f)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop()")
        if (viewModel.isLocal.value == true && orientation == Configuration.ORIENTATION_PORTRAIT) {
            viewModel.prevCoverURL.value = null
            viewModel.prevTrackName.value = null
            viewModel.prevTrackArtist.value = null
        }
        viewModel.removeObservers(viewLifecycleOwner)
    }

    private fun getMinutesSeconds(value: Int): String {
        var newValue = value
        var minutes = 0
        while (newValue >= 60) {
            minutes += 1
            newValue -= 60
        }

        return "$minutes:${if (newValue < 10) "0" else ""}$newValue"
    }
}