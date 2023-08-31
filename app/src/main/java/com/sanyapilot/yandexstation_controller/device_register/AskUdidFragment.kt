package com.sanyapilot.yandexstation_controller.device_register

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.FuckedQuasarClient
import com.sanyapilot.yandexstation_controller.api.LinkDeviceErrors
import kotlin.concurrent.thread

class AskUdidFragment : Fragment() {
    private lateinit var navController: NavController
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        navController = findNavController()
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_ask_udid, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val button = view.findViewById<Button>(R.id.udidButton)
        val textField = view.findViewById<TextInputLayout>(R.id.udidField).editText!!
        val layout = view.findViewById<ConstraintLayout>(R.id.askUdidLayout)

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
                val result = FuckedQuasarClient.linkDeviceStage1(trimmedText)
                requireActivity().runOnUiThread {
                    if (result.ok) {
                        // Switch to the next screen
                        val action = AskUdidFragmentDirections.toAskCode(trimmedText)
                        navController.navigate(action)
                    } else {
                        Snackbar.make(
                            layout,
                            when (result.error!!) {
                                LinkDeviceErrors.REGISTERED_ALREADY -> getString(R.string.deviceAlreadyLinked)
                                LinkDeviceErrors.DEVICE_OFFLINE -> getString(R.string.deviceOffline)
                                LinkDeviceErrors.ALREADY_RUNNING -> getString(R.string.alreadyRunningError)
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