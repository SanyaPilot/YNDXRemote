package com.sanyapilot.yandexstation_controller.fragments.devices

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.sanyapilot.yandexstation_controller.DeviceActivity
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.YandexStation

class DevicePlaybackFragment : Fragment() {
    private lateinit var speaker: YandexStation
    private lateinit var playButton: MaterialButton
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device_playback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        playButton = view.findViewById(R.id.playButton)
        val prevButton = view.findViewById<MaterialButton>(R.id.prevButton)
        val nextButton = view.findViewById<MaterialButton>(R.id.nextButton)
        val volUpButton = view.findViewById<MaterialButton>(R.id.volUpButton)
        val volDownButton = view.findViewById<MaterialButton>(R.id.volDownButton)

        speaker = (activity as DeviceActivity).speaker
        updatePlayBtn()

        playButton.setOnClickListener {
            if (speaker.isPlaying)
                speaker.pause()
            else
                speaker.play()

            updatePlayBtn()
        }

        prevButton.setOnClickListener {
            speaker.prevTrack()
            updatePlayBtn()
        }

        nextButton.setOnClickListener {
            speaker.nextTrack()
            updatePlayBtn()
        }

        volDownButton.setOnClickListener {
            speaker.decreaseVolume(5)
        }

        volUpButton.setOnClickListener {
            speaker.increaseVolume(5)
        }
    }
    private fun updatePlayBtn() {
        if (speaker.isPlaying)
            playButton.setIconResource(R.drawable.ic_round_pause_24)
        else
            playButton.setIconResource(R.drawable.ic_round_play_arrow_24)
    }
}