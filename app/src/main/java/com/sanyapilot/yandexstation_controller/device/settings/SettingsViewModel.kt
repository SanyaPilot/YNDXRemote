package com.sanyapilot.yandexstation_controller.device.settings

import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sanyapilot.yandexstation_controller.api.ConfigUpdateResponse
import com.sanyapilot.yandexstation_controller.api.DNDModeConfig
import com.sanyapilot.yandexstation_controller.api.DeviceConfig
import com.sanyapilot.yandexstation_controller.api.EqualizerBandConfig
import com.sanyapilot.yandexstation_controller.api.EqualizerConfig
import com.sanyapilot.yandexstation_controller.api.LEDBrightnessConfig
import com.sanyapilot.yandexstation_controller.api.LEDConfig
import com.sanyapilot.yandexstation_controller.api.LEDEQVisConfig
import com.sanyapilot.yandexstation_controller.api.LEDTimeVisConfig
import com.sanyapilot.yandexstation_controller.api.QuasarClient
import com.sanyapilot.yandexstation_controller.api.ReqResult
import com.sanyapilot.yandexstation_controller.api.ScreenSaverConfig
import com.sanyapilot.yandexstation_controller.api.SettingsErrors
import com.sanyapilot.yandexstation_controller.misc.StationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

data class NetStatus(
    val ok: Boolean,
    val error: SettingsErrors? = null
)

data class EQBand(
    val id: Int,
    val state: MutableFloatState,
    val name: String
)

data class EQPreset(
    val id: String,
    val name: String,
    val data: List<Float>
)

val EQ_NAMES = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
val EQ_PRESETS = listOf(
    EQPreset("flat", "Плоский", listOf(0f, 0f, 0f, 0f, 0f)),
    EQPreset("low_bass", "Меньше басов", listOf(-6f, 0f, 0f, 0f, 0f)),
    EQPreset("cinema", "Кино", listOf(-3f, -1f, 0f, 0f, 3f)),
    EQPreset("voice", "Голос", listOf(0f, 0f, 2f, 2f, 3f)),
    EQPreset("pop", "Поп", listOf(0f, 1f, 2f, 1f, 0f)),
    EQPreset("hip_hop", "Хип-хоп", listOf(3f, 2f, 0f, 3f, 3f)),
    EQPreset("dance", "Танцы", listOf(5f, 3f, 0f, 3f, 0f)),
    EQPreset("rock", "Рок", listOf(3f, 0f, -1f, 2f, 4f)),
    EQPreset("electro", "Электроника", listOf(3f, 1f, -1f, 1f, 2f)),
    EQPreset("metal", "Метал", listOf(4f, -2f, -2f, -2f, 4f)),
    EQPreset("rnb", "R'n'B", listOf(5f, 2f, -1f, 2f, 4f)),
    EQPreset("classic", "Классика", listOf(0f, 0f, 0f, 0f, -3f)),
    EQPreset("acoustics", "Акустика", listOf(3f, 0f, 1f, 1f, 3f)),
    EQPreset("jazz", "Джаз", listOf(2f, 0f, 1f, 0f, 2f)),
    EQPreset("concert", "Концерт", listOf(1f, 0f, 0f, 0f, 1f)),
    EQPreset("party", "Вечеринка", listOf(4f, 1f, -2f, 1f, 4f)),
    EQPreset("more_bass", "Больше басов", listOf(5f, 0f, 0f, 0f, 0f)),
    EQPreset("more_high", "Больше высоких", listOf(0f, 0f, 0f, 0f, 5f)),
    EQPreset("more_bass_high", "Больше басов и высоких", listOf(5f, 0f, 0f, 0f, 5f)),
    EQPreset("low_high", "Меньше высоких", listOf(0f, 0f, 0f, 0f, -5f))
)

val EQ_FREQS = listOf(60, 230, 910, 3600, 14000)
val EQ_WIDTHS = listOf(90, 340, 1340, 5200, 13000)

const val CUSTOM_PRESET_NAME = "Свой пресет"

data class DNDTime(
    val hour: Int,
    val minute: Int
)

@OptIn(ExperimentalMaterial3Api::class)
class SettingsViewModel(
    private val deviceId: String,
    private val deviceConfig: StationConfig,
    private val mediaController: MediaControllerCompat?
) : ViewModel() {
    private val _ssImages = MutableStateFlow(false)
    private val _netStatus = MutableStateFlow(NetStatus(true))
    private val _renameError = MutableStateFlow(false)
    private val _deviceName = MutableStateFlow("")
    private var _rawEQValues = mutableListOf(0f, 0f, 0f, 0f, 0f)
    private val _eqValues = MutableStateFlow(listOf<EQBand>())
    private val _presetName = MutableStateFlow(CUSTOM_PRESET_NAME)
    private val _dndEnabled = MutableStateFlow(false)
    private val _dndStartValue = MutableStateFlow(DNDTime(23, 0))
    private val _dndStopValue = MutableStateFlow(DNDTime(9, 0))
    private val _visPresetName = MutableStateFlow("pads")
    private val _visRandomEnabled = MutableStateFlow(true)
    private val _clockType = MutableStateFlow("middle")
    private val _screenAutoBrightness = MutableStateFlow(true)
    private val _screenBrightness = MutableStateFlow(0.5f)
    private val _proximityGestures = MutableStateFlow(true)

    private lateinit var deviceSmartHomeId: String
    private lateinit var outPayload: DeviceConfig
    private lateinit var configVersion: String

    companion object {
        const val TAG = "SettingsViewModel"
    }

    val ssImages: StateFlow<Boolean>
        get() = _ssImages
    val netStatus: StateFlow<NetStatus>
        get() = _netStatus
    val renameError: StateFlow<Boolean>
        get() = _renameError
    val deviceName: StateFlow<String>
        get() = _deviceName
    val eqValues: StateFlow<List<EQBand>>
        get() = _eqValues
    val presetName: StateFlow<String>
        get() = _presetName
    val dndEnabled: StateFlow<Boolean>
        get() = _dndEnabled
    val dndStartValue: StateFlow<DNDTime>
        get() = _dndStartValue
    val dndStopValue: StateFlow<DNDTime>
        get() = _dndStopValue
    val visPresetName: StateFlow<String>
        get() = _visPresetName
    val visRandomEnabled: StateFlow<Boolean>
        get() = _visRandomEnabled
    val clockType: StateFlow<String>
        get() = _clockType
    val screenAutoBrightness: StateFlow<Boolean>
        get() = _screenAutoBrightness
    val screenBrightness: StateFlow<Float>
        get() = _screenBrightness
    val proximityGestures: StateFlow<Boolean>
        get() = _proximityGestures

    init {
        // For preview
        updateEQValues(_rawEQValues)

        thread {
            val device = QuasarClient.getDeviceById(deviceId)!!
            _deviceName.value = device.name
            deviceSmartHomeId = device.smartHomeId
            refreshConfig()
        }
    }

    private fun refreshConfig() {
        // Fetch all current data
        val res = QuasarClient.getDeviceConfig(deviceSmartHomeId)
        if (!res.ok || res.data!!.quasar_config == null) {
            _netStatus.value = NetStatus(false, res.error)
            return
        }

        val quasarConfig = res.data.quasar_config!!
        // Initially fill the payload
        outPayload = quasarConfig
        configVersion = res.data.quasar_config_version!!

        // Screensavers (only for supported devices)
        if (deviceConfig.supportsScreenSaver) {
            val ssConfig = quasarConfig.screenSaverConfig
            if (ssConfig != null) {
                _ssImages.value = ssConfig.type == "IMAGE"
            }
        }

        // EQ
        val eqConfig = quasarConfig.equalizer
        if (eqConfig != null) {
            for (i in 0..4) {
                _rawEQValues[i] = eqConfig.bands[i].gain
            }
            updateEQValues(_rawEQValues)
        }

        // DND
        val dndConfig = quasarConfig.dndMode
        if (dndConfig != null) {
            _dndEnabled.value = dndConfig.enabled
            val start = dndConfig.starts.slice(0..4).split(":")
            val stop = dndConfig.ends.slice(0..4).split(":")
            _dndStartValue.value = DNDTime(start[0].toInt(), start[1].toInt())
            _dndStopValue.value = DNDTime(stop[0].toInt(), stop[1].toInt())
        }

        // Specific for devices with a LED screen
        if (deviceConfig.ledConfig != null) {
            val ledConfig = quasarConfig.led
            if (ledConfig != null) {
                if (deviceConfig.ledConfig.visPresets != null) {
                    _visPresetName.value = ledConfig.music_equalizer_visualization.style
                    _visRandomEnabled.value = ledConfig.music_equalizer_visualization.auto
                }
                if (deviceConfig.ledConfig.clockTypes != null) {
                    _clockType.value = ledConfig.time_visualization.size
                }
                if (deviceConfig.ledConfig.supportsBrightnessControl) {
                    _screenAutoBrightness.value = ledConfig.brightness.auto
                    _screenBrightness.value = ledConfig.brightness.value
                }
            }
        }

        // Yandex Station Mini gen 1 proximity gestures
        if (deviceConfig.supportsProximityGestures && quasarConfig.tof != null) {
            _proximityGestures.value = quasarConfig.tof!!
        }
    }

    private fun sendPayload(retry: Int = 2): ReqResult<ConfigUpdateResponse> {
        val res = QuasarClient.updateDeviceConfig(deviceSmartHomeId, outPayload, configVersion)
        if (res.error == SettingsErrors.INVALID_CONFIG_VERSION && retry > 0) {
            // Config was updated from elsewhere, sync and try again
            Log.d(TAG, "Invalid config version! Refreshing...")
            refreshConfig()
            return sendPayload(retry - 1)
        } else if (res.data?.version != null) {
            configVersion = res.data.version
        }
        return res
    }

    private fun updateEQValues(data: List<Float>) {
        val tempEQ = mutableListOf<EQBand>()
        for (i in 0..4) {
            tempEQ.add(EQBand(i, mutableFloatStateOf(data[i]), EQ_NAMES[i]))
        }
        _eqValues.value = tempEQ.toList()

        // Find preset
        var found = false
        for (preset in EQ_PRESETS) {
            if (preset.data == data) {
                _presetName.value = preset.name
                found = true
                break
            }
        }
        if (!found) {
            _presetName.value = CUSTOM_PRESET_NAME
        }
    }

    fun toggleSSType() {
        thread {
            outPayload.screenSaverConfig = ScreenSaverConfig(if (_ssImages.value) "VIDEO" else "IMAGE")
            val res = sendPayload()
            if (!res.ok) {
                _netStatus.value = NetStatus(false, res.error)
            } else {
                _ssImages.value = !ssImages.value
            }
        }
    }

    fun unlinkDevice() {
        thread {
            val res = QuasarClient.unlinkDevice(deviceSmartHomeId)
            if (res.ok) {
                // Stop service and finish activity
                QuasarClient.fetchDevices()
                mediaController?.transportControls?.stop()
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }

    fun updateDeviceName(name: String) {
        thread {
            val res = QuasarClient.renameDevice(deviceSmartHomeId, name, _deviceName.value)
            if (res.ok) {
                _deviceName.value = name
                _renameError.value = false
                QuasarClient.fetchDevices()
            } else if (res.error == SettingsErrors.INVALID_VALUE) {
                _renameError.value = true
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }

    private fun genEQBandsPayload(data: List<Float> = listOf(0f, 0f, 0f, 0f, 0f)): List<EqualizerBandConfig> {
        val res = mutableListOf<EqualizerBandConfig>()
        for (i in 0..4) {
            res.add(EqualizerBandConfig(
                gain = data[i],
                freq = EQ_FREQS[i],
                width = EQ_WIDTHS[i]
            ))
        }
        return res
    }
    fun updateEQBand(id: Int, value: Float) {
        thread {
            _rawEQValues[id] = value
            if (outPayload.equalizer == null) {
                outPayload.equalizer = EqualizerConfig(
                    enabled = true,
                    smartEnabled = false,
                    active_preset_id = "custom",
                    bands = genEQBandsPayload(),
                    custom_preset_bands = mutableListOf(0f, 0f, 0f, 0f, 0f)
                )
            }
            outPayload.equalizer!!.bands[id].gain = value
            outPayload.equalizer!!.custom_preset_bands[id] = value

            val res = sendPayload()
            if (res.ok) {
                updateEQValues(_rawEQValues)
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
    fun updateAllEQ(data: List<Float>) {
        thread {
            outPayload.equalizer = EqualizerConfig(
                enabled = true,
                smartEnabled = false,
                active_preset_id = "custom",
                bands = genEQBandsPayload(data),
                custom_preset_bands = data.toMutableList()
            )
            val res = sendPayload()
            if (res.ok) {
                _rawEQValues = data.toMutableList()
                updateEQValues(data)
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }

    private fun genDNDPayload(
        start: DNDTime = _dndStartValue.value,
        stop: DNDTime = _dndStopValue.value
    ): DNDModeConfig {
        val tz = TimeZone.getDefault()
        val suffix = (if (tz.rawOffset > 0) "+" else "-") +
                (tz.rawOffset / (1000 * 60 * 60)).absoluteValue
                    .toString().padStart(2, '0') + "00"
        return DNDModeConfig(
            enabled = _dndEnabled.value,
            starts = start.hour.toString().padStart(2, '0') + ":" +
                     start.minute.toString().padStart(2, '0') + ":00" + suffix,
            ends = stop.hour.toString().padStart(2, '0') + ":" +
                   stop.minute.toString().padStart(2, '0') + ":00" + suffix,
        )
    }
    fun toggleDND() {
        thread {
            if (outPayload.dndMode == null) {
                outPayload.dndMode = genDNDPayload()
            }
            outPayload.dndMode!!.enabled = !outPayload.dndMode!!.enabled
            val res = sendPayload()
            if (res.ok) {
                _dndEnabled.value = !_dndEnabled.value
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
    fun setDNDValues(start: TimePickerState, stop: TimePickerState) {
        thread {
            val startDNDTime = DNDTime(start.hour, start.minute)
            val stopDNDTime = DNDTime(stop.hour, stop.minute)
            outPayload.dndMode = genDNDPayload(
                start = startDNDTime,
                stop = stopDNDTime
            )
            val res = sendPayload()
            if (res.ok) {
                _dndStartValue.value = startDNDTime
                _dndStopValue.value = stopDNDTime
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }

    private fun genLEDConfig(): LEDConfig {
        return LEDConfig(
            brightness = if (deviceConfig.ledConfig!!.supportsBrightnessControl)
                LEDBrightnessConfig(
                    auto = _screenAutoBrightness.value,
                    value = _screenBrightness.value
                ) else null,
            music_equalizer_visualization = if (deviceConfig.ledConfig.visPresets != null)
                LEDEQVisConfig(
                    auto = _visRandomEnabled.value,
                    style = _visPresetName.value
                ) else null,
            time_visualization = if (deviceConfig.ledConfig.clockTypes != null)
                LEDTimeVisConfig(
                    size = _clockType.value
                ) else null
        )
    }
    fun setVisPreset(name: String) {
        thread {
            if (outPayload.led == null) {
                outPayload.led = genLEDConfig()
            }
            outPayload.led!!.music_equalizer_visualization.style = name
            val res = sendPayload()
            if (res.ok) {
                _visPresetName.value = name
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
    fun toggleVisRandom() {
        thread {
            if (outPayload.led == null) {
                outPayload.led = genLEDConfig()
            }
            outPayload.led!!.music_equalizer_visualization.auto = !outPayload.led!!.music_equalizer_visualization.auto
            val res = sendPayload()
            if (res.ok) {
                _visRandomEnabled.value = !_visRandomEnabled.value
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
    fun setClockType(type: String) {
        thread {
            if (outPayload.led == null) {
                outPayload.led = genLEDConfig()
            }
            outPayload.led!!.time_visualization.size = type
            val res = sendPayload()
            if (res.ok) {
                _clockType.value = type
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
    fun toggleAutoBrightness() {
        thread {
            if (outPayload.led == null) {
                outPayload.led = genLEDConfig()
            }
            outPayload.led!!.brightness.auto = !outPayload.led!!.brightness.auto
            val res = sendPayload()
            if (res.ok) {
                _screenAutoBrightness.value = !_screenAutoBrightness.value
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
    fun updateScreenBrightness(level: Float) {
        thread {
            if (outPayload.led == null) {
                outPayload.led = genLEDConfig()
            }
            val roundedLevel = (level * 100).roundToInt() / 100f
            outPayload.led!!.brightness.value = roundedLevel
            val res = sendPayload()
            if (res.ok) {
                _screenBrightness.value = roundedLevel
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }

    fun toggleProximityGestures() {
        thread {
            outPayload.tof = !_proximityGestures.value
            val res = sendPayload()
            if (res.ok) {
                _proximityGestures.value = !_proximityGestures.value
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
}

class SettingsViewModelFactory(
    private val deviceId: String,
    private val deviceConfig: StationConfig,
    private val mediaController: MediaControllerCompat?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(
            deviceId,
            deviceConfig,
            mediaController
        ) as T
    }
}
