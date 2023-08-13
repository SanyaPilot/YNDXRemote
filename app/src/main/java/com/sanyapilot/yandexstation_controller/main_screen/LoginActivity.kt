package com.sanyapilot.yandexstation_controller.main_screen

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.Session
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {
    private lateinit var codeField: EditText
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

        codeField = findViewById<TextInputLayout>(R.id.codeField).editText!!
    }
    fun auth(view: View) {
        thread(start = true) {
            val res = Session.login(codeField.text.toString().toInt())
            if (!res.ok) {
                val stringID = when (res.errorId) {
                    Errors.INVALID_CODE -> R.string.loginInvalidCode
                    Errors.INTERNAL_SERVER_ERROR -> R.string.serverDead
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
                putString("access-token", Session.accessToken)
                apply()
            }
            runOnUiThread {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}