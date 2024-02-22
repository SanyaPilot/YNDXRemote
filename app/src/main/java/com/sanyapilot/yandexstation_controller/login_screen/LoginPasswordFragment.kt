package com.sanyapilot.yandexstation_controller.login_screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.Session
import com.sanyapilot.yandexstation_controller.main_screen.MainActivity
import kotlin.concurrent.thread

class LoginPasswordFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val loginField = view.findViewById<TextInputLayout>(R.id.loginField).editText!!
        val passwdField = view.findViewById<TextInputLayout>(R.id.passwordField).editText!!
        val loginButton = view.findViewById<Button>(R.id.loginPasswordButton)

        loginButton.setOnClickListener {
            thread {
                if (loginField.text.isEmpty() || passwdField.text.isEmpty()) {
                    requireActivity().runOnUiThread {
                        Snackbar.make(
                            requireActivity().findViewById(R.id.loginLayout), getString(R.string.loginInvalidAccountOrPass),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    return@thread
                }
                val res = Session.loginPassword(
                    username = loginField.text.toString(),
                    password = passwdField.text.toString()
                )
                if (!res.ok) {
                    val stringID = when (res.errorId) {
                        Errors.INVALID_ACCOUNT -> R.string.loginInvalidAccountOrPass
                        Errors.INVALID_PASSSWD -> R.string.loginInvalidAccountOrPass
                        Errors.NEEDS_PHONE_CHALLENGE -> R.string.loginNotSupported
                        Errors.INTERNAL_SERVER_ERROR -> R.string.serverDead
                        Errors.CONNECTION_ERROR -> R.string.errorNoInternet
                        Errors.TIMEOUT -> R.string.unknownError
                        Errors.UNKNOWN -> R.string.unknownError
                        else -> R.string.wtf
                    }

                    requireActivity().runOnUiThread {
                        Snackbar.make(
                            requireActivity().findViewById(R.id.loginLayout), getString(stringID),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    return@thread
                }
                val sharedPrefs = requireActivity().getSharedPreferences("auth", Context.MODE_PRIVATE)
                with(sharedPrefs.edit()) {
                    putString("xToken", Session.xToken)
                    apply()
                }
                requireActivity().runOnUiThread {
                    startActivity(Intent(requireContext(), MainActivity::class.java))
                    requireActivity().finish()
                }
            }
        }
    }
}