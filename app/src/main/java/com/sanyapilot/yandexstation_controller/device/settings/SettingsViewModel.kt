package com.sanyapilot.yandexstation_controller.device.settings

/* TODO: Implement device settings
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

const val CUSTOM_PRESET_NAME = "Свой пресет"

data class DNDTime(
    val hour: Int,
    val minute: Int
)

data class Preset(
    val id: String,
    val drawableId: Int
)

val VIS_PRESETS = listOf(
    Preset("pads", R.drawable.vis_pads),
    Preset("barsBottom", R.drawable.vis_bars_bottom),
    Preset("barsCenter", R.drawable.vis_bars_center),
    Preset("bricksSmall", R.drawable.vis_bricks),
    Preset("flame", R.drawable.vis_wave_bottom),
    Preset("waveCenter", R.drawable.vis_wave_center)
)

val CLOCK_TYPES = listOf(
    Preset("small", R.drawable.clock_small),
    Preset("middle", R.drawable.clock_middle),
    Preset("large", R.drawable.clock_large),
)

@OptIn(ExperimentalMaterial3Api::class)
class SettingsViewModel(
    private val deviceId: String,
    private val devicePlatform: String,
    private val mediaController: MediaControllerCompat?
) : ViewModel() {
    private val _jingleEnabled = MutableStateFlow(false)
    private val _ssImages = MutableStateFlow(false)
    private val _netStatus = MutableStateFlow(NetStatus(true))
    private val _renameError = MutableStateFlow(false)
    private val _deviceName = MutableStateFlow("")
    private var _rawEQValues = mutableListOf(0f, 0f, 0f, 0f, 0f)
    private val _eqValues = MutableStateFlow(listOf<EQBand>())
    private val _presetName = MutableStateFlow(CUSTOM_PRESET_NAME)
    private val _dndEnabled = MutableStateFlow(false)
    private val _dndStartValue = MutableStateFlow(DNDTime(0, 0))
    private val _dndStopValue = MutableStateFlow(DNDTime(0, 0))
    private val _visPresetName = MutableStateFlow("wave")
    private val _visRandomEnabled = MutableStateFlow(false)
    private val _clockType = MutableStateFlow("small")
    private val _screenAutoBrightness = MutableStateFlow(true)
    private val _screenBrightness = MutableStateFlow(1f)

    val jingleEnabled: StateFlow<Boolean>
        get() = _jingleEnabled
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

    init {
        // For preview
        updateEQValues(_rawEQValues)

        thread {
            _deviceName.value = QuasarClient.getDeviceById(deviceId)!!.name

            // Activation sound
            val jingleRes = QuasarClient.getJingleStatus(deviceId)
            if (!jingleRes.ok) {
                _netStatus.value = NetStatus(false, jingleRes.error)
                return@thread
            }
            _jingleEnabled.value = jingleRes.data!!.enabled!!

            // Screensavers
            val ssRes = QuasarClient.getSSType(deviceId)
            if (!ssRes.ok) {
                _netStatus.value = NetStatus(false, ssRes.error)
                return@thread
            }
            _ssImages.value = ssRes.data!!.type == "image"

            // EQ
            val eqRes = QuasarClient.getEQData(deviceId)
            if (!eqRes.ok) {
                _netStatus.value = NetStatus(false, eqRes.error)
                return@thread
            }
            _rawEQValues = (eqRes.data!!.data as MutableList<Float>)
            updateEQValues(_rawEQValues)

            // DND
            val dndRes = QuasarClient.getDNDData(deviceId)
            if (!dndRes.ok) {
                _netStatus.value = NetStatus(false, dndRes.error)
                return@thread
            }
            _dndEnabled.value = dndRes.data!!.enabled!!
            val start = dndRes.data.start!!.split(':')
            val stop = dndRes.data.stop!!.split(':')
            _dndStartValue.value = DNDTime(start[0].toInt(), start[1].toInt())
            _dndStopValue.value = DNDTime(stop[0].toInt(), stop[1].toInt())

            // Yandex.Station Max specific
            if (devicePlatform == "yandexstation_2") {
                val screenRes = QuasarClient.getScreenSettings(deviceId)
                if (!screenRes.ok) {
                    _netStatus.value = NetStatus(false, screenRes.error)
                    return@thread
                }
                _visPresetName.value = screenRes.data!!.visualizer_preset!!
                _visRandomEnabled.value = screenRes.data.visualizer_random!!
                _clockType.value = screenRes.data.clock_type!!
                _screenAutoBrightness.value = screenRes.data.autobrightness!!
                _screenBrightness.value = screenRes.data.brightness!!
            }
        }
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

    fun toggleJingle() {
        thread {
            val res = QuasarClient.setJingleStatus(deviceId, !_jingleEnabled.value)
            if (!res.ok) {
                _netStatus.value = NetStatus(false, res.error)
            } else {
                _jingleEnabled.value = !_jingleEnabled.value
            }
        }
    }

    fun toggleSSType() {
        thread {
            val res = QuasarClient.setSSType(deviceId, if (_ssImages.value) "video" else "image")
            if (!res.ok) {
                _netStatus.value = NetStatus(false, res.error)
            } else {
                _ssImages.value = !ssImages.value
            }
        }
    }

    fun unlinkDevice() {
        thread {
            val res = QuasarClient.unlinkDevice(deviceId)
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
            val res = QuasarClient.renameDevice(deviceId, name)
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

    fun updateEQBand(id: Int, value: Float) {
        thread {
            _rawEQValues[id] = value
            val res = QuasarClient.setEQData(deviceId, _rawEQValues.toList())
            if (res.ok) {
                updateEQValues(_rawEQValues)
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }

    fun updateAllEQ(data: List<Float>) {
        thread {
            val res = QuasarClient.setEQData(deviceId, data)
            if (res.ok) {
                _rawEQValues = data.toMutableList()
                updateEQValues(data)
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }

    fun toggleDND() {
        thread {
            val res = QuasarClient.setDNDData(deviceId = deviceId, enable = !_dndEnabled.value)
            if (res.ok) {
                _dndEnabled.value = !_dndEnabled.value
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }

    fun setDNDValues(start: TimePickerState, stop: TimePickerState) {
        thread {
            val sStart = "${start.hour.toString().padStart(2, '0')}:" +
                    "${start.minute.toString().padStart(2, '0')}:00"
            val sStop = "${stop.hour.toString().padStart(2, '0')}:" +
                    "${stop.minute.toString().padStart(2, '0')}:00"
            val res = QuasarClient.setDNDData(
                deviceId = deviceId,
                enable = true,
                start = sStart, stop = sStop
            )
            if (res.ok) {
                _dndStartValue.value = DNDTime(start.hour, start.minute)
                _dndStopValue.value = DNDTime(stop.hour, stop.minute)
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }

    fun setVisPreset(name: String) {
        thread {
            val res = QuasarClient.setVisualizerPreset(
                deviceId = deviceId,
                name = name
            )
            if (res.ok) {
                _visPresetName.value = name
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
    fun toggleVisRandom() {
        thread {
            val res = QuasarClient.setVisualizerPreset(
                deviceId = deviceId,
                random = !_visRandomEnabled.value
            )
            if (res.ok) {
                _visRandomEnabled.value = !_visRandomEnabled.value
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
    fun setClockType(type: String) {
        thread {
            val res = QuasarClient.setClockType(
                deviceId = deviceId,
                type = type
            )
            if (res.ok) {
                _clockType.value = type
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
    fun toggleAutoBrightness() {
        thread {
            val res = QuasarClient.setAuthBrightnessState(
                deviceId = deviceId,
                state = !_screenAutoBrightness.value
            )
            if (res.ok) {
                _screenAutoBrightness.value = !_screenAutoBrightness.value
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
    fun updateScreenBrightness(level: Float) {
        thread {
            val roundedLevel = (level * 100).roundToInt() / 100f
            val res = QuasarClient.setBrightnessLevel(
                deviceId = deviceId,
                level = roundedLevel
            )
            if (res.ok) {
                _screenBrightness.value = roundedLevel
            } else {
                _netStatus.value = NetStatus(false, res.error)
            }
        }
    }
}

class SettingsViewModelFactory(
    private val deviceId: String,
    private val devicePlatform: String,
    private val mediaController: MediaControllerCompat?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(deviceId, devicePlatform, mediaController) as T
    }
}
*/
