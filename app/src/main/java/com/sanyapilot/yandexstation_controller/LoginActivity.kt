package com.sanyapilot.yandexstation_controller

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.Session
import kotlin.concurrent.thread

const val TAG = "YaStationController"

class LoginActivity : AppCompatActivity() {
    private lateinit var loginField: EditText
    private lateinit var passwdField: EditText
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

        loginField = findViewById<TextInputLayout>(R.id.loginField).editText!!
        passwdField =findViewById<TextInputLayout>(R.id.passwdField).editText!!
    }
    fun auth(view: View) {
        thread(start = true) {
            val res = Session.login(loginField.text.toString(), passwdField.text.toString())
            if (res.errorId != null) {
                var stringID = 0
                if (res.errorId == Errors.INVALID_ACCOUNT) stringID = R.string.loginInvalidUser
                if (res.errorId == Errors.INVALID_PASSSWD) stringID = R.string.loginInvalidPasswd
                if (res.errorId == Errors.NEEDS_PHONE_CHALLENGE) stringID = R.string.loginNotSupported

                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.loginLayout), getString(stringID),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                return@thread
            }
            Session.loginCookies()

            Log.e(TAG, "TOKEN AT LOGIN: ${sharedPrefs.getString("x-token", null)}")
            with(sharedPrefs.edit()) {
                putString("x-token", Session.xToken)
                apply()
            }
            Log.e(TAG, "TOKEN AFTER LOGIN: ${sharedPrefs.getString("x-token", null)}")
            runOnUiThread {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}