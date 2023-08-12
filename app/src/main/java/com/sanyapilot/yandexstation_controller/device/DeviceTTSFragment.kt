package com.sanyapilot.yandexstation_controller.device

import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.R

class DeviceTTSFragment : Fragment() {
    private lateinit var textField: EditText
    private lateinit var switch: MaterialSwitch
    private lateinit var mediaController: MediaControllerCompat

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device_tts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textField = view.findViewById<TextInputLayout>(R.id.ttsField).editText!!
        switch = view.findViewById(R.id.ttsSwitch)

        mediaController = MediaControllerCompat.getMediaController(requireActivity())

        val button = view.findViewById<Button>(R.id.sendButton)
        button.setOnClickListener { onClick() }
    }
    private fun onClick() {
        val text = textField.text.toString()
        if (text == "") {
            Snackbar.make(
                requireActivity().findViewById(R.id.deviceLayout), getString(R.string.enterTextSnackbar),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        if (text.length > 100) {
            Snackbar.make(
                requireActivity().findViewById(R.id.deviceLayout), getString(R.string.containsProhibitedSyms),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val reSyms = Regex("(<.+?>|[^А-Яа-яЁёA-Za-z0-9-,!.:=? ]+)")
        val reSpaces = Regex("  +")

        if (reSyms.containsMatchIn(text) || reSpaces.containsMatchIn(text)) {
            Snackbar.make(
                requireActivity().findViewById(R.id.deviceLayout), getString(R.string.containsProhibitedSyms),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val commandParams = Bundle()
        commandParams.putString("text", text)
        if (switch.isChecked)
            mediaController.sendCommand("sendTTS", commandParams, null)
        else
            mediaController.sendCommand("sendCommand", commandParams, null)
    }
}