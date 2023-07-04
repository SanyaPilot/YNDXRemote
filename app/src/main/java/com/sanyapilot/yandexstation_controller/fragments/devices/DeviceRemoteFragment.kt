package com.sanyapilot.yandexstation_controller.fragments.devices

import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.sanyapilot.yandexstation_controller.R

class DeviceRemoteFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device_remote, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val upButton = view.findViewById<Button>(R.id.navUpButton)
        val downButton = view.findViewById<Button>(R.id.navDownButton)
        val leftButton = view.findViewById<Button>(R.id.navLeftButton)
        val rightButton = view.findViewById<Button>(R.id.navRightButton)
        val clickButton = view.findViewById<Button>(R.id.clickButton)
        val backButton = view.findViewById<Button>(R.id.navBackButton)
        val homeButton = view.findViewById<Button>(R.id.navHomeButton)

        val mediaController = MediaControllerCompat.getMediaController(requireActivity())

        upButton.setOnClickListener {
            mediaController.sendCommand("navUp", null, null)
        }
        downButton.setOnClickListener {
            mediaController.sendCommand("navDown", null, null)
        }
        leftButton.setOnClickListener {
            mediaController.sendCommand("navLeft", null, null)
        }
        rightButton.setOnClickListener {
            mediaController.sendCommand("navRight", null, null)
        }
        clickButton.setOnClickListener {
            mediaController.sendCommand("click", null, null)
        }
        backButton.setOnClickListener {
            mediaController.sendCommand("navBack", null, null)
        }
        homeButton.setOnClickListener {
            mediaController.sendCommand("navHome", null, null)
        }
    }
}