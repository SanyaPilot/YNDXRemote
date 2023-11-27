package com.sanyapilot.yandexstation_controller.main_screen.user_settings

import androidx.lifecycle.ViewModel
import com.sanyapilot.yandexstation_controller.R
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

data class Spotter(
    val name: Int,
    val description: Int,
    val value: String
)

val SPOTTERS = mapOf(
    "music" to listOf(
        Spotter(
            name = R.string.spotterPlayAndPauseTitle,
            description = R.string.spotterPlayAndPauseDesc,
            value = "playAndPause"
        ),
        Spotter(
            name = R.string.spotterMusicNavigationTitle,
            description = R.string.spotterMusicNavigationDesc,
            value = "navigation"
        ),
        Spotter(
            name = R.string.spotterVolumeTitle,
            description = R.string.spotterVolumeDesc,
            value = "volume"
        )
    ),
    "tv" to listOf(
        Spotter(
            name = R.string.spotterTVNavigationTitle,
            description = R.string.spotterTVNavigationDesc,
            value = "navigation"
        ),
        Spotter(
            name = R.string.spotterBackToHomeTitle,
            description = R.string.spotterBackToHomeDesc,
            value = "backToHome"
        )
    ),
    "smartHome" to listOf(
        Spotter(
            name = R.string.spotterLightControlTitle,
            description = R.string.spotterLightControlDesc,
            value = "light"
        ),
        Spotter(
            name = R.string.spotterTVControlTitle,
            description = R.string.spotterTVControlDesc,
            value = "tv"
        )
    )
)

val SPOTTER_TYPE_NAMES = mapOf(
    "music" to R.string.spottersMusicTitle,
    "tv" to R.string.spottersTVTitle,
    "smartHome" to R.string.spottersSmartHomeTitle
)

class UserSettingsViewModel : ViewModel() {
    private val _netStatus = MutableStateFlow(NetStatus(true))
    private val _curTimezoneName = MutableStateFlow("UTC+3 (MSK)")
    private val _enabledSpotters = MutableStateFlow(mapOf<String, List<String>>())

    val netStatus: StateFlow<NetStatus>
        get() = _netStatus
    val curTimezoneName: StateFlow<String>
        get() = _curTimezoneName
    val enabledSpotters: StateFlow<Map<String, List<String>>>
        get() = _enabledSpotters

    init {
        thread {
            // Timezone
            val res = FuckedQuasarClient.getUserInfo()
            if (!res.ok) {
                _netStatus.value = NetStatus(false, res.error)
                return@thread
            }
            updateTZName(res.data!!.timezone!!)

            // Spotters
            _enabledSpotters.value = res.data.spotters!!
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

    fun toggleSpotter(type: String, name: String) {
        thread {
            val temp = _enabledSpotters.value.toMutableMap()
            val tempList = if (temp[type] == null) {
                mutableListOf()
            } else {
                temp[type]!!.toMutableList()
            }
            if (tempList.contains(name)) {
                tempList.remove(name)
            } else {
                tempList.add(name)
            }
            temp[type] = tempList.toList()
            val res = FuckedQuasarClient.updateSpotters(temp)
            if (!res.ok) {
                _netStatus.value = NetStatus(false, res.error)
            } else {
                _enabledSpotters.value = temp
            }
        }
    }
}