package com.sanyapilot.yandexstation_controller.device

import android.content.Context
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.sanyapilot.yandexstation_controller.R

object TTSHelper {
    fun sendCommand(view: View, context: Context, mc: MediaControllerCompat?, text: String, tts: Boolean) {
        if (text == "") {
            Snackbar.make(
                view.findViewById(R.id.rcMainLayout), context.getString(R.string.enterTextSnackbar),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        if (text.length > 100) {
            Snackbar.make(
                view.findViewById(R.id.rcMainLayout), context.getString(R.string.tooLong),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val reSyms = Regex("(<.+?>|[^А-Яа-яЁёA-Za-z0-9-,!.:=? ]+)")
        val reSpaces = Regex("  +")

        if (reSyms.containsMatchIn(text) || reSpaces.containsMatchIn(text)) {
            Snackbar.make(
                view.findViewById(R.id.rcMainLayout), context.getString(R.string.containsProhibitedSyms),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val commandParams = Bundle()
        commandParams.putString("text", text)
        if (tts)
            mc?.sendCommand("sendTTS", commandParams, null)
        else
            mc?.sendCommand("sendCommand", commandParams, null)
    }
}