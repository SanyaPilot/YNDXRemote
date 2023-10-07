package com.sanyapilot.yandexstation_controller.device

import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.R

class DeviceRemoteFragment : Fragment() {
    private lateinit var mediaController: MediaControllerCompat

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device_remote, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mediaController = MediaControllerCompat.getMediaController(requireActivity())

        // Navigation
        val upButton = view.findViewById<Button>(R.id.navUpButton)
        val downButton = view.findViewById<Button>(R.id.navDownButton)
        val leftButton = view.findViewById<Button>(R.id.navLeftButton)
        val rightButton = view.findViewById<Button>(R.id.navRightButton)
        val clickButton = view.findViewById<Button>(R.id.clickButton)
        val backButton = view.findViewById<Button>(R.id.navBackButton)
        val homeButton = view.findViewById<Button>(R.id.navHomeButton)

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

        // TTS and commands
        val textField = view.findViewById<TextInputLayout>(R.id.ttsField).editText!!
        val sendCmdButton = view.findViewById<Button>(R.id.sendCmdButton)
        val sendTTSButton = view.findViewById<Button>(R.id.sendTTSButton)

        sendCmdButton.setOnClickListener { sendCommand(view, textField.text.toString(), false) }
        sendTTSButton.setOnClickListener { sendCommand(view, textField.text.toString(), true) }
    }

    private fun sendCommand(view: View, text: String, tts: Boolean) {
        if (text == "") {
            Snackbar.make(
                view.findViewById(R.id.rcMainLayout), getString(R.string.enterTextSnackbar),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        if (text.length > 100) {
            Snackbar.make(
                view.findViewById(R.id.rcMainLayout), getString(R.string.tooLong),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val reSyms = Regex("(<.+?>|[^А-Яа-яЁёA-Za-z0-9-,!.:=? ]+)")
        val reSpaces = Regex("  +")

        if (reSyms.containsMatchIn(text) || reSpaces.containsMatchIn(text)) {
            Snackbar.make(
                view.findViewById(R.id.rcMainLayout), getString(R.string.containsProhibitedSyms),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val commandParams = Bundle()
        commandParams.putString("text", text)
        if (tts)
            mediaController.sendCommand("sendTTS", commandParams, null)
        else
            mediaController.sendCommand("sendCommand", commandParams, null)
    }
}