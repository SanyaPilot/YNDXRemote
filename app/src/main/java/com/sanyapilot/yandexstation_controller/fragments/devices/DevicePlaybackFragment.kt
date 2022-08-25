package com.sanyapilot.yandexstation_controller.fragments.devices

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.sanyapilot.yandexstation_controller.DeviceActivity
import com.sanyapilot.yandexstation_controller.R

class DevicePlaybackFragment : Fragment() {
    private var playState: Boolean = false
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

        playButton.setOnClickListener {
            playState = !playState
            updatePlayBtn()
            (activity as DeviceActivity).send(if (playState) "продолжи" else "пауза")
        }

        prevButton.setOnClickListener {
            playState = true
            updatePlayBtn()
            (activity as DeviceActivity).send("предыдущий трек")
        }

        nextButton.setOnClickListener {
            playState = true
            updatePlayBtn()
            (activity as DeviceActivity).send("следующий трек")
        }

        volDownButton.setOnClickListener {
            (activity as DeviceActivity).send("понизь громкость на 5 процентов")
        }

        volUpButton.setOnClickListener {
            (activity as DeviceActivity).send("повысь громкость на 5 процентов")
        }
    }
    private fun updatePlayBtn() {
        if (playState)
            playButton.setIconResource(R.drawable.ic_round_pause_24)
        else
            playButton.setIconResource(R.drawable.ic_round_play_arrow_24)
    }
}