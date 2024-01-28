package com.sanyapilot.yandexstation_controller.misc

import com.sanyapilot.yandexstation_controller.R

data class StationConfig (
    val name: Int,
    val icon: Int,
    val supportsScreenSaver: Boolean = false,
    val supportsUI: Boolean = false,
    val supportsLED: Boolean = false,
    val supportProximityGestures: Boolean = false
)

val stationConfigs = mapOf(
    "yandexstation" to StationConfig(
        name = R.string.station,
        icon = R.drawable.station_icon,
        supportsScreenSaver = true,
        supportsUI = true
    ),
    "yandexstation_2" to StationConfig(
        name = R.string.station_max,
        icon = R.drawable.station_max,
        supportsScreenSaver = true,
        supportsUI = true,
        supportsLED = true
    ),
    "yandexmini" to StationConfig(
        name = R.string.station_mini,
        icon = R.drawable.station_mini,
        supportProximityGestures = true
    ),
    "yandexmini_2" to StationConfig(
        name = R.string.station_mini2,
        icon = R.drawable.station_mini2,
        supportsLED = true
    ),
    "yandexmicro" to StationConfig(
        name = R.string.station_lite,
        icon = R.drawable.station_lite
    ),
    "yandexmidi" to StationConfig(
        name = R.string.station_2,
        icon = R.drawable.station_2
    ),
    "cucumber" to StationConfig(
        name = R.string.station_midi,
        icon = R.drawable.station_midi
    ),
    "chiron" to StationConfig(
        name = R.string.station_duo_max,
        icon = R.drawable.station_duo_max
    )
)

val fallbackConfig = StationConfig(
    name = R.string.station_unknown,
    icon = R.drawable.station_unknown
)
