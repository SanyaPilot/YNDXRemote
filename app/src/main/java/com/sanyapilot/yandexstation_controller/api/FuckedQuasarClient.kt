package com.sanyapilot.yandexstation_controller.api
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.Exception


@Serializable
data class DevicesResponse(
    val devices: List<Speaker>
)

@Serializable
data class Speaker(
    val id: String,
    val name: String,
    val platform: String,
    val networkInfo: NetworkInfo
)

@Serializable
data class NetworkInfo(
    val external_port: Int? = null,
    val ip_addresses: List<String>? = null,
    val mac_addresses: List<String>? = null
)

@Serializable
data class LinkDeviceBody(
    val device_id: String,
    val code: Int?
)

@Serializable
data class GenericResponse(
    val status: String,
    val reason: String? = null
)

enum class LinkDeviceErrors {
    DEVICE_OFFLINE, ALREADY_RUNNING, REGISTERED_ALREADY, INVALID_CODE, UNAUTHORIZED, UNKNOWN, TIMEOUT
}

data class LinkDeviceResult(
    val ok: Boolean,
    val error: LinkDeviceErrors? = null
)

enum class SettingsErrors {
    UNAUTHORIZED, NOT_LINKED, INVALID_VALUE, UNSUPPORTED_ACTION, TIMEOUT, UNKNOWN
}

@Serializable
data class SettingsResponse(
    val status: String,
    val enabled: Boolean? = null,
    val type: String? = null
)

data class UnlinkDeviceResult(
    val ok: Boolean,
    val error: SettingsErrors? = null
)

@Serializable
data class BoolSettingBody(
    val device_id: String,
    val realtime_update: Boolean,
    val state: Boolean
)

data class GenericSettingResult(
    val ok: Boolean,
    val error: SettingsErrors? = null
)

data class BoolSettingResult(
    val ok: Boolean,
    val enabled: Boolean? = null,
    val error: SettingsErrors? = null
)

@Serializable
data class TypeSettingBody(
    val device_id: String,
    val realtime_update: Boolean,
    val state: String
)

data class TypeSettingResult(
    val ok: Boolean,
    val type: String? = null,
    val error: SettingsErrors? = null
)

@Serializable
data class NameSettingBody(
    val device_id: String,
    val name: String
)

@Serializable
data class EQSettingBody(
    val device_id: String,
    val data: List<Float>,
    val realtime_update: Boolean
)

@Serializable
data class EQSettingResponse(
    val status: String,
    val reason: String? = null,
    val data: List<Float>
)

data class EQSettingResult(
    val ok: Boolean,
    val error: SettingsErrors? = null,
    val data: List<Float>? = null
)

@Serializable
data class DNDSettingBody(
    val device_id: String,
    val realtime_update: Boolean,
    val enable: Boolean,
    val start: String? = null,
    val stop: String? = null
)

@Serializable
data class DNDSettingResponse(
    val status: String,
    val reason: String? = null,
    val enabled: Boolean? = null,
    val start: String? = null,
    val stop: String? = null
)

data class DNDSettingResult(
    val ok: Boolean,
    val enabled: Boolean? = null,
    val error: SettingsErrors? = null,
    val start: String? = null,
    val stop: String? = null
)

@Serializable
data class ScreenSettingBody(
    val device_id: String,
    val realtime_update: Boolean,
    val visualizer_preset: String? = null,
    val autobrightness: Boolean? = null,
    val brightness: Float? = null,
    val clock_type: String? = null
)

@Serializable
data class ScreenSettingResponse(
    val status: String,
    val reason: String? = null,
    val visualizer_preset: String? = null,
    val autobrightness: Boolean? = null,
    val brightness: Float? = null,
    val clock_type: String? = null
)

data class ScreenSettingResult(
    val ok: Boolean,
    val error: SettingsErrors? = null,
    val visualizer_preset: String? = null,
    val autobrightness: Boolean? = null,
    val brightness: Float? = null,
    val clock_type: String? = null
)

const val FQ_BACKEND_URL = "https://testing.yndxfuck.ru"
val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

object FuckedQuasarClient {
    const val TAG = "FuckedQuasarClient"
    private var devices = listOf<Speaker>()
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchDevices(): RequestResponse {
        val result = Session.get("$FQ_BACKEND_URL/glagol/device_list")
        if (!result.ok) {
            return result
        }
        val body = result.response!!.body.string()
        val parsed = json.decodeFromString<DevicesResponse>(body)
        result.response.close()
        devices = parsed.devices
        return result
    }
    fun getDevices(): List<Speaker> = devices

    fun getDeviceById(deviceId: String): Speaker? {
        if (devices.isNotEmpty()) {
            for (device in devices) {
                if (device.id == deviceId) {
                    return device
                }
            }
        }
        return null
    }

    fun linkDeviceStage1(deviceId: String): LinkDeviceResult {
        val body = json.encodeToString(LinkDeviceBody(device_id = deviceId, code = null))
        val res = Session.post("$FQ_BACKEND_URL/reg_device", body.toRequestBody(JSON_MEDIA_TYPE))
        if (res.errorId == Errors.TIMEOUT) {
            return LinkDeviceResult(false, LinkDeviceErrors.TIMEOUT)
        }
        val parsed = json.decodeFromString<GenericResponse>(res.response!!.body.string())
        return if (parsed.status == "ok") {
            LinkDeviceResult(true)
        } else {
            LinkDeviceResult(
                false,
                when (parsed.reason!!) {
                    "already_running" -> LinkDeviceErrors.ALREADY_RUNNING
                    "unauthorized" -> LinkDeviceErrors.UNAUTHORIZED
                    "registered_already" -> LinkDeviceErrors.REGISTERED_ALREADY
                    "timeout" -> LinkDeviceErrors.DEVICE_OFFLINE
                    "device_offline" -> LinkDeviceErrors.DEVICE_OFFLINE
                    else -> LinkDeviceErrors.UNKNOWN
                }
            )
        }
    }

    fun linkDeviceStage2(deviceId: String, code: Int): LinkDeviceResult {
        val body = json.encodeToString(LinkDeviceBody(device_id = deviceId, code = code))
        val res = Session.post("$FQ_BACKEND_URL/submit_2fa", body.toRequestBody(JSON_MEDIA_TYPE))
        if (res.errorId == Errors.TIMEOUT) {
            return LinkDeviceResult(false, LinkDeviceErrors.TIMEOUT)
        }
        val parsed = json.decodeFromString<GenericResponse>(res.response!!.body.string())
        return if (parsed.status == "ok") {
            LinkDeviceResult(true)
        } else {
            LinkDeviceResult(
                false,
                when (parsed.reason!!) {
                    "unauthorized" -> LinkDeviceErrors.UNAUTHORIZED
                    "invalid_code" -> LinkDeviceErrors.INVALID_CODE
                    "timeout" -> LinkDeviceErrors.DEVICE_OFFLINE
                    else -> LinkDeviceErrors.UNKNOWN
                }
            )
        }
    }

    fun unlinkDevice(deviceId: String): UnlinkDeviceResult {
        val body = json.encodeToString(LinkDeviceBody(device_id = deviceId, code = null))
        val res = Session.post("$FQ_BACKEND_URL/unlink_device", body.toRequestBody(JSON_MEDIA_TYPE))
        if (res.errorId == Errors.TIMEOUT) {
            return UnlinkDeviceResult(false, SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        return if (code == 200) {
            UnlinkDeviceResult(true)
        } else {
            UnlinkDeviceResult(
                false,
                when (code) {
                    400 -> SettingsErrors.NOT_LINKED
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }

    fun getJingleStatus(deviceId: String): BoolSettingResult {
        val res = Session.get("$FQ_BACKEND_URL/get_jingle_status?device_id=$deviceId")
        if (res.errorId == Errors.TIMEOUT) {
            return BoolSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        return if (code == 200) {
            val parsed = json.decodeFromString<SettingsResponse>(res.response.body.string())
            BoolSettingResult(true, parsed.enabled)
        } else {
            BoolSettingResult(
                ok = false,
                error = when (code) {
                    400 -> SettingsErrors.NOT_LINKED
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }
    fun setJingleStatus(deviceId: String, enabled: Boolean): BoolSettingResult {
        val body = json.encodeToString(BoolSettingBody(
            device_id = deviceId, realtime_update = true, state = enabled
        ))
        val res = Session.post("$FQ_BACKEND_URL/update_jingle", body.toRequestBody(JSON_MEDIA_TYPE))
        if (res.errorId == Errors.TIMEOUT) {
            return BoolSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        return if (code == 200) {
            BoolSettingResult(true)
        } else {
            BoolSettingResult(
                ok = false,
                error = when (code) {
                    400 -> SettingsErrors.NOT_LINKED
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }

    fun getSSType(deviceId: String): TypeSettingResult {
        val res = Session.get("$FQ_BACKEND_URL/get_ss_type_status?device_id=$deviceId")
        if (res.errorId == Errors.TIMEOUT) {
            return TypeSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        return if (code == 200) {
            val parsed = json.decodeFromString<SettingsResponse>(res.response.body.string())
            TypeSettingResult(true, parsed.type)
        } else {
            TypeSettingResult(
                ok = false,
                error = when (code) {
                    400 -> SettingsErrors.NOT_LINKED
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }
    fun setSSType(deviceId: String, type: String): TypeSettingResult {
        val body = json.encodeToString(TypeSettingBody(
            device_id = deviceId, realtime_update = true, state = type
        ))
        val res = Session.post("$FQ_BACKEND_URL/update_ss_type", body.toRequestBody(JSON_MEDIA_TYPE))
        if (res.errorId == Errors.TIMEOUT) {
            return TypeSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        return if (code == 200) {
            TypeSettingResult(true)
        } else {
            TypeSettingResult(
                ok = false,
                error = when (code) {
                    400 -> SettingsErrors.NOT_LINKED
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }
    fun renameDevice(deviceId: String, name: String): GenericSettingResult {
        val body = json.encodeToString(NameSettingBody(
            device_id = deviceId, name = name
        ))
        val res = Session.post("$FQ_BACKEND_URL/rename_device", body.toRequestBody(JSON_MEDIA_TYPE))
        if (res.errorId == Errors.TIMEOUT) {
            return GenericSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val resp = res.response!!
        if (resp.code == 200) {
            return GenericSettingResult(true)
        } else {
            val parsed = json.decodeFromString<GenericResponse>(resp.body.string())
            return GenericSettingResult(
                ok = false,
                error = when (resp.code) {
                    400 -> when (parsed.reason) {
                        "not_registered" -> SettingsErrors.NOT_LINKED
                        "invalid_name" -> SettingsErrors.INVALID_VALUE
                        else -> SettingsErrors.UNKNOWN
                    }
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }
    fun getEQData(deviceId: String): EQSettingResult {
        val res = Session.get("$FQ_BACKEND_URL/get_eq_data?device_id=$deviceId")
        if (res.errorId == Errors.TIMEOUT) {
            return EQSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        return if (code == 200) {
            val parsed = json.decodeFromString<EQSettingResponse>(res.response.body.string())
            EQSettingResult(true, data = parsed.data)
        } else {
            EQSettingResult(
                ok = false,
                error = when (code) {
                    400 -> SettingsErrors.NOT_LINKED
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }
    fun setEQData(deviceId: String, data: List<Float>): EQSettingResult {
        val body = json.encodeToString(EQSettingBody(
            device_id = deviceId, realtime_update = true, data = data
        ))
        val res = Session.post("$FQ_BACKEND_URL/update_eq", body.toRequestBody(JSON_MEDIA_TYPE))
        if (res.errorId == Errors.TIMEOUT) {
            return EQSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        return if (code == 200) {
            EQSettingResult(true)
        } else {
            EQSettingResult(
                ok = false,
                error = when (code) {
                    400 -> SettingsErrors.NOT_LINKED
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }
    fun getDNDData(deviceId: String): DNDSettingResult {
        val res = Session.get("$FQ_BACKEND_URL/get_dnd_status?device_id=$deviceId")
        if (res.errorId == Errors.TIMEOUT) {
            return DNDSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        return if (code == 200) {
            val parsed = json.decodeFromString<DNDSettingResponse>(res.response.body.string())
            DNDSettingResult(true, enabled = parsed.enabled, start = parsed.start, stop = parsed.stop)
        } else {
            DNDSettingResult(
                ok = false,
                error = when (code) {
                    400 -> SettingsErrors.NOT_LINKED
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }
    fun setDNDData(deviceId: String, enable: Boolean, start: String? = null, stop: String? = null): DNDSettingResult {
        if (!enable && (start != null || stop != null)) {
            throw IllegalArgumentException()
        }
        if ((start != null && stop == null) || (start == null && stop != null)) {
            throw IllegalArgumentException()
        }
        val body = json.encodeToString(DNDSettingBody(
            device_id = deviceId, realtime_update = true,
            enable = enable, start = start, stop = stop
        ))
        val res = Session.post("$FQ_BACKEND_URL/update_dnd", body.toRequestBody(JSON_MEDIA_TYPE))
        if (res.errorId == Errors.TIMEOUT) {
            return DNDSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        return if (code == 200) {
            DNDSettingResult(true)
        } else {
            val parsed = json.decodeFromString<DNDSettingResponse>(res.response.body.string())
            DNDSettingResult(
                ok = false,
                error = when (code) {
                    400 -> when (parsed.reason) {
                        "not_registered" -> SettingsErrors.NOT_LINKED
                        else -> SettingsErrors.UNKNOWN
                    }
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }
    fun getScreenSettings(deviceId: String): ScreenSettingResult {
        val res = Session.get("$FQ_BACKEND_URL/get_screen_settings?device_id=$deviceId")
        if (res.errorId == Errors.TIMEOUT) {
            return ScreenSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        val parsed: ScreenSettingResponse? = try {
            json.decodeFromString<ScreenSettingResponse>(res.response.body.string())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode JSON!\n${e.message}")
            null
        }
        return if (code == 200 && parsed != null) {
            ScreenSettingResult(
                true,
                visualizer_preset = parsed.visualizer_preset,
                autobrightness = parsed.autobrightness,
                brightness = parsed.brightness,
                clock_type = parsed.clock_type
            )
        } else {
            ScreenSettingResult(
                ok = false,
                error = when (code) {
                    400 -> when (parsed?.reason) {
                        "not_registered" -> SettingsErrors.NOT_LINKED
                        "unsupported_action" -> SettingsErrors.UNSUPPORTED_ACTION
                        else -> SettingsErrors.UNKNOWN
                    }
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }
    fun setVisualizerPreset(deviceId: String, name: String): ScreenSettingResult {
        val body = json.encodeToString(ScreenSettingBody(
            device_id = deviceId, realtime_update = true, visualizer_preset = name
        ))
        val res = Session.post(
            "$FQ_BACKEND_URL/update_screen_settings", body.toRequestBody(JSON_MEDIA_TYPE)
        )
        if (res.errorId == Errors.TIMEOUT) {
            return ScreenSettingResult(false, error = SettingsErrors.TIMEOUT)
        }
        val code = res.response!!.code
        val parsed: ScreenSettingResponse? = try {
            json.decodeFromString<ScreenSettingResponse>(res.response.body.string())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode JSON!\n${e.message}")
            null
        }
        return if (code == 200 && parsed != null) {
            ScreenSettingResult(true)
        } else {
            ScreenSettingResult(
                ok = false,
                error = when (code) {
                    400 -> when (parsed?.reason) {
                        "not_registered" -> SettingsErrors.NOT_LINKED
                        "unsupported_action" -> SettingsErrors.UNSUPPORTED_ACTION
                        else -> SettingsErrors.UNKNOWN
                    }
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> SettingsErrors.UNKNOWN
                }
            )
        }
    }
}
