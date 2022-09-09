package com.sanyapilot.yandexstation_controller.fragments.devices

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
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

        // ViewModel observers here
        viewModel.isLocal.observe(viewLifecycleOwner) {
            if (it) {
                progressBar.visibility = TextView.VISIBLE
                curProgress.visibility = TextView.VISIBLE
                maxProgress.visibility = TextView.VISIBLE
            } else {
                progressBar.visibility = TextView.GONE
                curProgress.visibility = TextView.GONE
                maxProgress.visibility = TextView.GONE
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