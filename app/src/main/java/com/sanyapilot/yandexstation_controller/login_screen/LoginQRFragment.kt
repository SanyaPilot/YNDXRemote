package com.sanyapilot.yandexstation_controller.login_screen

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
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
    private lateinit var qrCodeImage: ImageView
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                writeQR()
            } else {
                Snackbar.make(
                    requireActivity().findViewById(R.id.loginLayout), getString(R.string.permissionRequired),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login_qr, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        qrCodeImage = view.findViewById(R.id.loginQRCode)
        val confirmButton = view.findViewById<Button>(R.id.loginQRConfirmButton)
        val saveButton = view.findViewById<Button>(R.id.loginQRSaveCodeButton)

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

        saveButton.setOnClickListener {
            if (
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_DENIED
            ) {
                // Request a permission
                requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                writeQR()
            }
        }
    }

    private fun writeQR() {
        val resolver = requireActivity().applicationContext.contentResolver
        val fileURI = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "YNDXRemote QR.png")
            }
        )
        if (fileURI != null) {
            resolver.openOutputStream(fileURI, "w").use {
                if (it != null) {
                    val drawable = qrCodeImage.drawable
                    val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    Canvas(bitmap).drawBitmap(drawable.toBitmap(), 0f, 0f, null)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, it)
                    Snackbar.make(
                        requireActivity().findViewById(R.id.loginLayout), getString(R.string.QRSavedSuccessfully),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
