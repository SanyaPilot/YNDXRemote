package com.sanyapilot.yandexstation_controller.device

import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.device.TTSHelper.sendCommand

class DeviceTTSFragment : Fragment() {
    private lateinit var viewModel: DeviceViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device_tts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TTS and commands
        val textField = view.findViewById<TextInputLayout>(R.id.ttsField).editText!!
        val sendCmdButton = view.findViewById<Button>(R.id.sendCmdButton)
        val sendTTSButton = view.findViewById<Button>(R.id.sendTTSButton)

        viewModel = ViewModelProvider(requireActivity())[DeviceViewModel::class.java]

        viewModel.isReady.observe(viewLifecycleOwner) {
            if (it) {
                val mediaController = MediaControllerCompat.getMediaController(requireActivity())

                sendCmdButton.setOnClickListener {
                    sendCommand(
                        view,
                        requireContext(),
                        mediaController,
                        textField.text.toString(),
                        false
                    )
                }
                sendTTSButton.setOnClickListener {
                    sendCommand(
                        view,
                        requireContext(),
                        mediaController,
                        textField.text.toString(),
                        true
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.removeObservers(viewLifecycleOwner)
    }
}
