package com.sanyapilot.yandexstation_controller.service

import com.sanyapilot.yandexstation_controller.api.GlagolClient
import com.sanyapilot.yandexstation_controller.api.GlagolPayload
import com.sanyapilot.yandexstation_controller.api.Speaker
import com.sanyapilot.yandexstation_controller.api.StationState
import com.sanyapilot.yandexstation_controller.api.mDNSWorker
import kotlin.concurrent.thread

// Wrapper class
class YandexStationService(val speaker: Speaker, val client: GlagolClient, val listener: (data: StationState) -> Unit, val closedListener: () -> Unit) {
    var localMode = false
    var localTTS = true

    init {
        localMode = mDNSWorker.deviceExists(speaker.id)
        if (localMode) {
            startLocal()
        } else {
            mDNSWorker.addListener(speaker.id) { startLocal() }
        }
    }

    fun endLocal() {
        if (localMode) {
            localMode = false
            thread(start = true) { client.stop() }
        }
    }

    fun startLocal() {
        thread(start = true) {
            client.setOnSocketClosedListener {
                closedListener()
            }
            client.setOnFailureListener {
                closedListener()
            }
            client.start { listener(it) }
        }
        localMode = true
    }

    fun sendTTS(text: String) {
        // Currently selecting TTS mode is not implemented
        if (localMode && localTTS) {
            client.send(GlagolPayload(command = "sendText", text = "Повтори за мной $text"))
        } else {
            //QuasarClient.send(speaker, text, true)
        }
    }
    fun sendCommand(text: String) {
        if (localMode) {
            client.send(GlagolPayload(command = "sendText", text = text))
        } else {
            //QuasarClient.send(speaker, text, false)
        }
    }
    fun play() {
        if (localMode) {
            client.send(GlagolPayload(command = "play"))
        } else {
            //QuasarClient.send(speaker, "продолжи", false)
        }
    }
    fun pause() {
        if (localMode) {
            client.send(GlagolPayload(command = "stop"))
        } else {
            //QuasarClient.send(speaker, "пауза", false)
        }
    }
    fun nextTrack() {
        if (localMode) {
            client.send(GlagolPayload(command = "next"))
        } else {
            //QuasarClient.send(speaker, "следующий трек", false)
        }
    }
    fun prevTrack() {
        if (localMode) {
            client.send(GlagolPayload(command = "prev"))
        } else {
            //QuasarClient.send(speaker, "предыдущий трек", false)
        }
    }
    fun setVolume(value: Float) {
        if (localMode) {
            client.send(
                GlagolPayload(
                command = "setVolume",
                volume = value / 10
            )
            )
        } else {
            //QuasarClient.send(speaker, "громкость ${value * 10} процентов", false)
        }
    }
    fun seek(value: Int) {
        //viewModel.seekTime.value = value
        client.send(
            GlagolPayload(
            command = "rewind",
            position = value
        )
        )
    }
    // Yandex unreleased navigation over Glagol
    fun navUp(steps: Int?) {
        val payload = GlagolPayload(
            command = "control",
            action = "go_up"
        )
        if (steps != null) {
            payload.scrollAmount = "exact"
            payload.scrollExactValue = steps
        }
        client.send(payload)
    }
    fun navDown(steps: Int?) {
        val payload = GlagolPayload(
            command = "control",
            action = "go_down"
        )
        if (steps != null) {
            payload.scrollAmount = "exact"
            payload.scrollExactValue = steps
        }
        client.send(payload)
    }
    fun navLeft(steps: Int?) {
        val payload = GlagolPayload(
            command = "control",
            action = "go_left"
        )
        if (steps != null) {
            payload.scrollAmount = "exact"
            payload.scrollExactValue = steps
        }
        client.send(payload)
    }
    fun navRight(steps: Int?) {
        val payload = GlagolPayload(
            command = "control",
            action = "go_right"
        )
        if (steps != null) {
            payload.scrollAmount = "exact"
            payload.scrollExactValue = steps
        }
        client.send(payload)
    }
    fun click() {
        client.send(
            GlagolPayload(
            command = "control",
            action = "click_action"
        )
        )
    }
    fun navBack() {
        // Workaround via sendText command
        sendCommand("назад")
    }
    fun navHome() {
        // Another workaround
        sendCommand("домой")
    }

    // Music
    fun playTrack(id: String, offset: Float?) {
        val payload = GlagolPayload(
            command = "playMusic",
            type = "track",
            id = id
        )
        if (offset != null)
            payload.offset = offset

        client.send(payload)
    }
    fun playPlaylist(id: String) {
        client.send(
            GlagolPayload(
            command = "playMusic",
            type = "playlist",
            id = id
        )
        )
    }
    fun playRadio(id: String) {
        client.send(
            GlagolPayload(
            command = "playMusic",
            type = "radio",
            id = id
        )
        )
    }
    // Shortcut for "my vibe"
    fun playMyVibe() {
        playRadio("user:onyourwave")
    }
    fun playFavs() {
        // Workaround
        sendCommand("включи мои любимые")
    }
    fun shuffle() {
        sendCommand("играй вперемешку")
    }
    fun likeTrack() {
        sendCommand("лайк")
    }
}