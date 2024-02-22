package com.sanyapilot.yandexstation_controller.login_screen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.Session
import com.sanyapilot.yandexstation_controller.main_screen.MainActivity
import kotlin.concurrent.thread

class LoginQRFragment : Fragment() {
    private val args: LoginQRFragmentArgs by navArgs()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login_qr, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val qrCodeImage = view.findViewById<ImageView>(R.id.loginQRCode)
        val confirmButton = view.findViewById<Button>(R.id.loginQRConfirmButton)

        // Load image
        val imageLoader = ImageLoader.Builder(requireContext())
            .components {
                add(SvgDecoder.Factory())
            }
            .build()

        qrCodeImage.load(args.qrCodeUrl, imageLoader)

        confirmButton.setOnClickListener {
            thread {
                val res = Session.loginQR()
                if (!res.ok) {
                    val stringID = when (res.errorId) {
                        Errors.QR_NOT_LOGGED_IN -> R.string.loginQRNotConfirmedError
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