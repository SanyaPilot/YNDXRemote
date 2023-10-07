package com.sanyapilot.yandexstation_controller.device

import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.device.TTSHelper.sendCommand

class DeviceRemoteFragment : Fragment() {
    private var mediaController: MediaControllerCompat? = null
    private lateinit var viewModel: DeviceViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device_remote, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Navigation
        val upButton = view.findViewById<Button>(R.id.navUpButton)
        val downButton = view.findViewById<Button>(R.id.navDownButton)
        val leftButton = view.findViewById<Button>(R.id.navLeftButton)
        val rightButton = view.findViewById<Button>(R.id.navRightButton)
        val clickButton = view.findViewById<Button>(R.id.clickButton)
        val backButton = view.findViewById<Button>(R.id.navBackButton)
        val homeButton = view.findViewById<Button>(R.id.navHomeButton)

        // TTS and commands
        val textField = view.findViewById<TextInputLayout>(R.id.ttsField).editText!!
        val sendCmdButton = view.findViewById<Button>(R.id.sendCmdButton)
        val sendTTSButton = view.findViewById<Button>(R.id.sendTTSButton)

        // Hide TTS in landscape
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val ttsLayout = view.findViewById<LinearLayout>(R.id.ttsLayout)
            ttsLayout.visibility = View.GONE
        }

        viewModel = ViewModelProvider(requireActivity())[DeviceViewModel::class.java]

        viewModel.isReady.observe(viewLifecycleOwner) {
            if (it) {
                mediaController = MediaControllerCompat.getMediaController(requireActivity())

                upButton.setOnClickListener {
                    mediaController?.sendCommand("navUp", null, null)
                }
                downButton.setOnClickListener {
                    mediaController?.sendCommand("navDown", null, null)
                }
                leftButton.setOnClickListener {
                    mediaController?.sendCommand("navLeft", null, null)
                }
                rightButton.setOnClickListener {
                    mediaController?.sendCommand("navRight", null, null)
                }
                clickButton.setOnClickListener {
                    mediaController?.sendCommand("click", null, null)
                }
                backButton.setOnClickListener {
                    mediaController?.sendCommand("navBack", null, null)
                }
                homeButton.setOnClickListener {
                    mediaController?.sendCommand("navHome", null, null)
                }

                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
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
    }

    override fun onStop() {
        super.onStop()
        viewModel.removeObservers(viewLifecycleOwner)
    }
}