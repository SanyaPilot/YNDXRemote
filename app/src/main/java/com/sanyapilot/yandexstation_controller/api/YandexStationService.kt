package com.sanyapilot.yandexstation_controller.api

import kotlin.concurrent.thread

// Wrapper class
class YandexStationService(val speaker: Speaker, val client: GlagolClient, val listener: (data: StationState) -> Unit) {
    var localMode = false
    var localTTS = true

    init {
        localMode = mDNSWorker.deviceExists(speaker.id)
        if (localMode) {
            startLocal(false)
        } else {
            mDNSWorker.addListener(speaker.id) { startLocal(true) }
        }
    }

    fun endLocal() {
        if (localMode) {
            localMode = false
            thread(start = true) { client.stop() }
        }
    }

    fun startLocal(showSnack: Boolean) {
        thread(start = true) {
            client.start { listener(it) }
            client.setOnSocketClosedListener {
                localMode = false
            }
            client.setOnFailureListener {
                localMode = false
                mDNSWorker.removeDevice(speaker.id)
                mDNSWorker.addListener(speaker.id) { startLocal(true) }
            }
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
    /*fun increaseVolume(value: Float) {
        if (localMode) {
            client.send(GlagolPayload(
                command = "setVolume",
                volume = viewModel.volume.value!! + (value / 100)
            ))
        } else {
            QuasarClient.send(speaker, "повысь громкость на ${value.toInt()} процентов", false)
        }
    }
    fun decreaseVolume(value: Float) {
        if (localMode) {
            client.send(GlagolPayload(
                command = "setVolume",
                volume = viewModel.volume.value!! - (value / 100)
            ))
        } else {
            QuasarClient.send(speaker, "понизь громкость на ${value.toInt()} процентов", false)
        }
    }*/
    fun setVolume(value: Float) {
        if (localMode) {
            client.send(GlagolPayload(
                command = "setVolume",
                volume = value / 10
            ))
        } else {
            //QuasarClient.send(speaker, "громкость ${value * 10} процентов", false)
        }
    }
    fun seek(value: Int) {
        //viewModel.seekTime.value = value
        client.send(GlagolPayload(
            command = "rewind",
            position = value
        ))
    }
}