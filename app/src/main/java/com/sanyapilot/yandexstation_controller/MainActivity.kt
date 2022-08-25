package com.sanyapilot.yandexstation_controller

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import com.sanyapilot.yandexstation_controller.api.Errors
import com.sanyapilot.yandexstation_controller.api.QuasarClient
import com.sanyapilot.yandexstation_controller.api.Session
import com.sanyapilot.yandexstation_controller.api.Speaker
import com.sanyapilot.yandexstation_controller.fragments.devices.DevicesFragment
import com.sanyapilot.yandexstation_controller.fragments.UserFragment
import kotlin.concurrent.thread

const val TOKEN_INVALID = "com.sanyapilot.yandexstation_controller.tokenInvalid"

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var loggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.e(TAG, "STARTED MAIN")

        val bottomNavigation = findViewById<NavigationBarView>(R.id.bottomNavigation)

        // Bottom navbar listener
        bottomNavigation.selectedItemId = R.id.accountPage
        bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.devicesPage -> {
                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<DevicesFragment>(R.id.mainFragmentContainer)
                    }
                    true
                }
                R.id.accountPage -> {

                    supportFragmentManager.commit {
                        setReorderingAllowed(true)
                        replace<UserFragment>(R.id.mainFragmentContainer)
                    }
                    true
                }
                else -> false
            }
        }
        bottomNavigation.setOnItemReselectedListener {}

        // Check if we need auth
        val sharedPrefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        if (!sharedPrefs.contains("x-token")) {
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        // Authorize with saved x-token
        if (savedInstanceState == null) doNetwork()
        else viewModel.setLoggedIn(true)
    }

    override fun onRestart() {
        // Handle cookie update after returning from LoginActivity
        super.onRestart()
        Log.e(TAG, "onRestart()")
        Log.e(TAG, viewModel.isLoggedIn()!!.toString())
        if (!viewModel.isLoggedIn()!!) {
            doNetwork()
        }
    }
    private fun doNetwork() {
        // Refresh cookies and push default fragment to layout
        // Prepare speakers
        thread(start = true) {
            val result = Session.refreshCookies()
            Log.e(TAG, "refreshed cookies")
            if (result.errorId == Errors.TIMEOUT) {
                Log.e(TAG, "timeout")
                runOnUiThread {
                    Snackbar.make(
                        findViewById(R.id.mainLayout), getString(R.string.errorNoInternet),
                        Snackbar.LENGTH_INDEFINITE
                    ).show()
                }
            } else if (result.errorId == Errors.TOKEN_AUTH_FAILED) {
                Log.e(TAG, "token auth fail")
                runOnUiThread {
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                            .putExtra(TOKEN_INVALID, true)
                    )
                }
            }
            Log.e(TAG, "done all checks")
            QuasarClient.prepareSpeakers()
            //progressBar.visibility = ProgressBar.INVISIBLE
            runOnUiThread {
                viewModel.setLoggedIn(true)
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    add<UserFragment>(R.id.mainFragmentContainer)
                }
            }
        }
    }
    fun logOut(view: View) {
        // Logout action, starting LoginActivity
        val sharedPrefs = getSharedPreferences("auth", Context.MODE_PRIVATE)
        Log.e(TAG, "TOKEN B4 CLEARING: ${sharedPrefs.getString("x-token", null)}")
        with (sharedPrefs.edit()) {
            remove("x-token")
            commit()
        }
        Session.clearAllCookies()
        Log.e(TAG, "TOKEN AFTER CLEARING: ${sharedPrefs.getString("x-token", null)}")
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
    fun testGovorilka(view: View) {
        thread(start = true) {
            val speakers = QuasarClient.getSpeakers()
            for (sp in speakers) {
                if (sp.id == "0bc4049e-8283-4ebd-9612-b77946175fa0") {
                    QuasarClient.send(sp, "Привет, это Алиса, рада знакомству!", true)
                }
            }
        }
    }
}