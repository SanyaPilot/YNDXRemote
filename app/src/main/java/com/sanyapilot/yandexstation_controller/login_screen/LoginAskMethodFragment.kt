package com.sanyapilot.yandexstation_controller.login_screen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.Session
import kotlin.concurrent.thread

class LoginAskMethodFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login_ask_method, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val passwordCard = view.findViewById<MaterialCardView>(R.id.loginMethodPasswordCard)
        val qrCard = view.findViewById<MaterialCardView>(R.id.loginMethodQRCard)

        val navController = findNavController()
        // Via password
        passwordCard.setOnClickListener {
            navController.navigate(LoginAskMethodFragmentDirections.loginViaPassword())
        }

        // Via QR
        qrCard.setOnClickListener {
            // Try to fetch QR URL
            thread {
                val res = Session.requestQRAuth()
                if (!res.ok) {
                    val stringID = when (res.errorId) {
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
                requireActivity().runOnUiThread {
                    navController.navigate(LoginAskMethodFragmentDirections.loginViaQR(res.url!!))
                }
            }

        }
    }
}