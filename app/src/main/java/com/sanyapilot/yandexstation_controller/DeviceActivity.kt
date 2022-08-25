package com.sanyapilot.yandexstation_controller

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.sanyapilot.yandexstation_controller.api.QuasarClient
import com.sanyapilot.yandexstation_controller.api.Speaker
import kotlin.concurrent.thread

class DeviceActivity : AppCompatActivity() {
    private lateinit var speaker: Speaker
    private lateinit var textField: EditText
    private lateinit var switch: MaterialSwitch
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        val deviceId = intent.getStringExtra("deviceId")
        speaker = QuasarClient.getSpeakerById(deviceId!!)!!
        textField = findViewById<TextInputLayout>(R.id.ttsField).editText!!
        switch = findViewById(R.id.ttsSwitch)

        val appBar = findViewById<MaterialToolbar>(R.id.deviceAppBar)
        appBar.subtitle = intent.getStringExtra("deviceName")
    }
    fun send(view: View) {
        val text = textField.text.toString()
        if (text == "") {
            Snackbar.make(
                findViewById(R.id.deviceLayout), getString(R.string.enterTextSnackbar),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        if (text.length > 100) {
            Snackbar.make(
                findViewById(R.id.deviceLayout), getString(R.string.containsProhibitedSyms),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val reSyms = Regex("(<.+?>|[^А-Яа-яЁёA-Za-z0-9-,!.:=? ]+)")
        val reSpaces = Regex("  +")

        if (reSyms.containsMatchIn(text) || reSpaces.containsMatchIn(text)) {
            Snackbar.make(
                findViewById(R.id.deviceLayout), getString(R.string.containsProhibitedSyms),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        thread(start = true) {
            QuasarClient.send(
                speaker,
                textField.text.toString(),
                switch.isChecked
            )
            runOnUiThread {
                Snackbar.make(
                    findViewById(R.id.deviceLayout), getString(R.string.sent),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }
}