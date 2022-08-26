package com.sanyapilot.yandexstation_controller.api
// Wrapper class
class YandexStation(private val speaker: Speaker) {
    var isPlaying = false
    fun sendTTS(text: String) = QuasarClient.send(speaker, text, true)
    fun sendCommand(text: String) = QuasarClient.send(speaker, text, false)
    fun play() {
        isPlaying = true
        QuasarClient.send(speaker, "продолжи", false)
    }
    fun pause() {
        isPlaying = false
        QuasarClient.send(speaker, "пауза", false)
    }
    fun nextTrack() {
        isPlaying = true
        QuasarClient.send(speaker, "следующий трек", false)
    }
    fun prevTrack() {
        isPlaying = true
        QuasarClient.send(speaker, "предыдущий трек", false)
    }
    fun increaseVolume(value: Int) = QuasarClient.send(speaker, "повысь громкость на $value процентов", false)
    fun decreaseVolume(value: Int) = QuasarClient.send(speaker, "понизь громкость на $value процентов", false)
}