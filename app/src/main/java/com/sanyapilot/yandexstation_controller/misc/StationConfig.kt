package com.sanyapilot.yandexstation_controller.misc

import com.sanyapilot.yandexstation_controller.R

data class StationConfig (
    val name: Int,
    val icon: Int,
    val supportsScreenSaver: Boolean = false,
    val supportsUI: Boolean = false,
    val ledConfig: StationLEDConfig? = null,
    val supportsProximityGestures: Boolean = false  // Mini gen 1
)

data class Preset(
    val id: String,
    val drawableId: Int
)

data class StationLEDConfig (
    val visPresets: List<Preset>? = null,
    val clockTypes: List<Preset>? = null,
    val supportsBrightnessControl: Boolean = true,
    val supportsIdleAnimation: Boolean = false  // Station 2
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
        ledConfig = StationLEDConfig(
            visPresets = listOf(
                Preset("pads", R.drawable.vis_pads),
                Preset("barsBottom", R.drawable.vis_bars_bottom),
                Preset("barsCenter", R.drawable.vis_bars_center),
                Preset("bricksSmall", R.drawable.vis_bricks),
                Preset("flame", R.drawable.vis_wave_bottom),
                Preset("waveCenter", R.drawable.vis_wave_center)
            ),
            clockTypes = listOf(
                Preset("small", R.drawable.clock_small),
                Preset("middle", R.drawable.clock_middle),
                Preset("large", R.drawable.clock_large)
            )
        )
    ),
    "yandexmini" to StationConfig(
        name = R.string.station_mini,
        icon = R.drawable.station_mini,
        supportsProximityGestures = true
    ),
    "yandexmini_2" to StationConfig(
        name = R.string.station_mini2,
        icon = R.drawable.station_mini2,
        ledConfig = StationLEDConfig()  // Just brightness control
    ),
    "yandexmicro" to StationConfig(
        name = R.string.station_lite,
        icon = R.drawable.station_lite
    ),
    "yandexmidi" to StationConfig(
        name = R.string.station_2,
        icon = R.drawable.station_2,
        ledConfig = StationLEDConfig(
            // TODO: Draw fancy icons
            visPresets = listOf(
                Preset("blink", R.drawable.vis_pads),
                Preset("lava_beat", R.drawable.vis_bars_bottom),
                Preset("polar_shining", R.drawable.vis_bars_center),
                Preset("none", R.drawable.light_off_24),
            ),
            supportsIdleAnimation = true
        )
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
