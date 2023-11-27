package com.sanyapilot.yandexstation_controller.main_screen.user_settings

import androidx.lifecycle.ViewModel
import com.sanyapilot.yandexstation_controller.api.FuckedQuasarClient
import com.sanyapilot.yandexstation_controller.device.settings.NetStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

data class Timezone(
    val name: String,
    val value: String
)

val TIMEZONES = listOf(
    Timezone("UTC+2 (MSK-1)", "+02"),
    Timezone("UTC+3 (MSK)", "+03"),
    Timezone("UTC+4 (MSK+1)", "+04"),
    Timezone("UTC+5 (MSK+2)", "+05"),
    Timezone("UTC+6 (MSK+3)", "+06"),
    Timezone("UTC+7 (MSK+4)", "+07"),
    Timezone("UTC+8 (MSK+5)", "+08"),
    Timezone("UTC+9 (MSK+6)", "+09"),
    Timezone("UTC+10 (MSK+7)", "+10"),
    Timezone("UTC+11 (MSK+8)", "+11"),
    Timezone("UTC+12 (MSK+9)", "+12")
)

class UserSettingsViewModel : ViewModel() {
    private val _netStatus = MutableStateFlow(NetStatus(true))
    private val _curTimezoneName = MutableStateFlow("UTC+3 (MSK)")

    val netStatus: StateFlow<NetStatus>
        get() = _netStatus
    val curTimezoneName: StateFlow<String>
        get() = _curTimezoneName

    init {
        thread {
            val res = FuckedQuasarClient.getUserInfo()
            if (!res.ok) {
                _netStatus.value = NetStatus(false, res.error)
                return@thread
            }
            updateTZName(res.data!!.timezone!!)
        }
    }

    private fun updateTZName(value: String) {
        for (tz in TIMEZONES) {
            if (tz.value == value) {
                _curTimezoneName.value = tz.name
                break
            }
        }
    }
    fun updateTimezone(value: String) {
        thread {
            val res = FuckedQuasarClient.updateTimezone(value)
            if (!res.ok) {
                _netStatus.value = NetStatus(false, res.error)
            } else {
                updateTZName(value)
            }
        }
    }
}