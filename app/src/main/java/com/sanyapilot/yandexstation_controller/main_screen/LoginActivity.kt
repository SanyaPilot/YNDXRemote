package com.sanyapilot.yandexstation_controller.main_screen

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.Session
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {
    private lateinit var sharedPrefs: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sharedPrefs = getSharedPreferences("auth", Context.MODE_PRIVATE)

        val tokenInvalid = intent.getBooleanExtra(TOKEN_INVALID, false)
        if (tokenInvalid) {
            Snackbar.make(
                findViewById(R.id.loginLayout), getString(R.string.loginXTokenFailed),
                Snackbar.LENGTH_LONG
            ).show()
        }

        val loginField = findViewById<TextInputLayout>(R.id.loginField).editText!!
        val passwdField = findViewById<TextInputLayout>(R.id.passwdField).editText!!
        val loginButton = findViewById<Button>(R.id.loginButton)
        val howToCodeButton = findViewById<Button>(R.id.howToCodeButton)
        val telegramButton = findViewById<Button>(R.id.telegramButton)

        loginButton.setOnClickListener {
            thread(start = true) {
                if (loginField.text.isEmpty()) {
                    runOnUiThread {
                        Snackbar.make(
                            findViewById(R.id.loginLayout), getString(R.string.loginInvalidCode),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                } else {
                    val res = Session.login(
                        username = loginField.text.toString(),
                        password = passwdField.text.toString()
                    )
                    if (!res.ok) {
                        val stringID = when (res.errorId) {
                            Errors.INVALID_ACCOUNT -> R.string.loginInvalidCode
                            Errors.INVALID_PASSSWD -> R.string.loginInvalidPasswd
                            Errors.NEEDS_PHONE_CHALLENGE -> R.string.loginNotSupported
                            Errors.INTERNAL_SERVER_ERROR -> R.string.serverDead
                            Errors.CONNECTION_ERROR -> R.string.errorNoInternet
                            Errors.TIMEOUT -> R.string.unknownError
                            Errors.UNKNOWN -> R.string.unknownError
                            else -> R.string.wtf
                        }

                        runOnUiThread {
                            Snackbar.make(
                                findViewById(R.id.loginLayout), getString(stringID),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                        return@thread
                    }
                    with(sharedPrefs.edit()) {
                        putString("xToken", Session.xToken)
                        apply()
                    }
                    runOnUiThread {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                }
            }
        }

        howToCodeButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yndxfuck.ru/remote")))
        }
        telegramButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/yndxfuck")))
        }
    }
}