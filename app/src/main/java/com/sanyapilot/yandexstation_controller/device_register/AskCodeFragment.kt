package com.sanyapilot.yandexstation_controller.device_register

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.FuckedQuasarClient
import com.sanyapilot.yandexstation_controller.api.LinkDeviceErrors
import kotlin.concurrent.thread

class AskCodeFragment : Fragment() {
    private val args: AskCodeFragmentArgs by navArgs()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_ask_code, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val button = view.findViewById<Button>(R.id.codeButton)
        val textField = view.findViewById<TextInputLayout>(R.id.codeField).editText!!
        val layout = view.findViewById<ConstraintLayout>(R.id.askCodeLayout)

        button.setOnClickListener {
            val trimmedText = textField.text.toString().trim()
            if (trimmedText.isEmpty()) {
                Snackbar.make(
                    layout,
                    getString(R.string.invalidUDID),
                    Snackbar.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            thread {
                val result = FuckedQuasarClient.linkDeviceStage2(args.deviceId, trimmedText.toInt())
                requireActivity().runOnUiThread {
                    if (result.ok) {
                        // Finish activity
                        requireActivity().finish()
                    } else {
                        Snackbar.make(
                            layout,
                            when (result.error!!) {
                                LinkDeviceErrors.INVALID_CODE -> getString(R.string.loginInvalidCode)
                                LinkDeviceErrors.DEVICE_OFFLINE -> getString(R.string.deviceOffline)
                                LinkDeviceErrors.UNAUTHORIZED -> getString(R.string.unauthorizedError)
                                LinkDeviceErrors.UNKNOWN -> getString(R.string.unknownError)
                                LinkDeviceErrors.TIMEOUT -> getString(R.string.serverDead)
                                else -> getString(R.string.wtf)
                            },
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
}