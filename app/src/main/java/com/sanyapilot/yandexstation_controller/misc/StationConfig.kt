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
    val drawableId: Int,
    val textId: Int? = null
)

data class StationLEDConfig (
    val drawIconsAsImages: Boolean = false,
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
            drawIconsAsImages = true,
            visPresets = listOf(
                Preset("blink", R.drawable.st2_led_blink, R.string.visSt2BlinkLabel),
                Preset("lava_beat", R.drawable.st2_led_lava, R.string.visSt2LavaLabel),
                Preset("polar_shining", R.drawable.st2_led_polar, R.string.visSt2PolarLabel),
                Preset("none", R.drawable.st2_led_off, R.string.visSt2NoneLabel),
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
