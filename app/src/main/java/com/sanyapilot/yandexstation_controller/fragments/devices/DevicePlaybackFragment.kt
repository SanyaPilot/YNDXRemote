package com.sanyapilot.yandexstation_controller.fragments.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.*
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.sanyapilot.yandexstation_controller.DeviceActivity
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.YandexStation
import com.sanyapilot.yandexstation_controller.fragments.DeviceViewModel

class DevicePlaybackFragment : Fragment() {
    private lateinit var viewModel: DeviceViewModel
    private lateinit var station: YandexStation
    private lateinit var playButton: MaterialButton
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

        playButton = view.findViewById(R.id.playButton)
        val prevButton = view.findViewById<MaterialButton>(R.id.prevButton)
        val nextButton = view.findViewById<MaterialButton>(R.id.nextButton)
        val volUpButton = view.findViewById<MaterialButton>(R.id.volUpButton)
        val volDownButton = view.findViewById<MaterialButton>(R.id.volDownButton)
        val coverImage = view.findViewById<ImageView>(R.id.cover)
        val trackName = view.findViewById<TextView>(R.id.trackName)
        val trackArtist = view.findViewById<TextView>(R.id.trackArtist)
        val progressBar = view.findViewById<Slider>(R.id.progressBar)
        val curProgress = view.findViewById<TextView>(R.id.curProgress)
        val maxProgress = view.findViewById<TextView>(R.id.maxProgress)

        station = (activity as DeviceActivity).station

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

        // ViewModel observers here
        viewModel.isLocal.observe(requireActivity()) {
            if (it) {
                trackName.visibility = TextView.VISIBLE
                trackArtist.visibility = TextView.VISIBLE
                progressBar.visibility = TextView.VISIBLE
                curProgress.visibility = TextView.VISIBLE
                maxProgress.visibility = TextView.VISIBLE
            } else {
                trackName.visibility = TextView.INVISIBLE
                trackArtist.visibility = TextView.INVISIBLE
                progressBar.visibility = TextView.INVISIBLE
                curProgress.visibility = TextView.INVISIBLE
                maxProgress.visibility = TextView.INVISIBLE
                coverImage.setImageResource(R.drawable.ic_baseline_cloud_24)
            }
        }

        viewModel.isPlaying.observe(requireActivity()) {
            if (it)
                playButton.setIconResource(R.drawable.ic_round_pause_24)
            else
                playButton.setIconResource(R.drawable.ic_round_play_arrow_24)
        }

        viewModel.playerActive.observe(requireActivity()) {
            if (!it) {
                coverImage.setImageResource(R.drawable.ic_round_pause_on_surface_24)
            }
        }

        viewModel.hasPrev.observe(requireActivity()) {
            prevButton.isEnabled = it
        }

        viewModel.hasNext.observe(requireActivity()) {
            nextButton.isEnabled = it
        }

        viewModel.trackName.observe(requireActivity()) {
            if (it != viewModel.prevTrackName.value) {
                viewModel.prevTrackName.value = it
                animateText(trackName, it)
            }
        }

        viewModel.trackArtist.observe(requireActivity()) {
            if (it != viewModel.prevTrackArtist.value) {
                viewModel.prevTrackArtist.value = it
                animateText(trackArtist, it)
            }
        }

        viewModel.coverURL.observe(requireActivity()) {
            if (it != null) {
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
                            val request = ImageRequest.Builder(requireActivity())
                                .data(curImageURL)
                                .target(coverImage)
                                .listener { _, _ ->
                                    coverImage.startAnimation(fadeIn)
                                }
                                .build()

                            context!!.imageLoader.enqueue(request)
                        }

                        override fun onAnimationRepeat(p0: Animation?) {
                        }
                    })
                    coverImage.startAnimation(fadeOut)
                }
            }
        }

        viewModel.progressMax.observe(requireActivity()) {
            if (it > 0) {
                maxProgress.text = getMinutesSeconds(it)
                progressBar.valueTo = it.toFloat()
            }
        }

        viewModel.progress.observe(requireActivity()) {
            curProgress.text = getMinutesSeconds(it)
            if (allowSliderChange)
                progressBar.value = it.toFloat()
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
        if (viewModel.isLocal.value == true) {
            viewModel.prevCoverURL.value = null
            viewModel.prevTrackName.value = null
            viewModel.prevTrackArtist.value = null
        }

        viewModel.removeObservers(requireActivity())
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
}