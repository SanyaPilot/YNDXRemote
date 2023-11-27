package com.sanyapilot.yandexstation_controller.api
import android.util.Log
import com.sanyapilot.yandexstation_controller.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
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

interface APIResponse {
    val status: String
    val reason: String?
}

data class ReqResult<T>(
    val ok: Boolean,
    val error: SettingsErrors? = null,
    val data: T? = null
)

enum class LinkDeviceErrors {
    DEVICE_OFFLINE, ALREADY_RUNNING, REGISTERED_ALREADY, INVALID_CODE, UNAUTHORIZED, UNKNOWN, TIMEOUT
}

@Serializable
data class LinkDeviceBody(
    val device_id: String,
    val code: Int?
)

data class LinkDeviceResult(
    val ok: Boolean,
    val error: LinkDeviceErrors? = null
)

enum class SettingsErrors {
    UNAUTHORIZED, NOT_LINKED, INVALID_VALUE, UNSUPPORTED_ACTION, TIMEOUT, UNKNOWN
}

@Serializable
data class GenericResponse(
    override val status: String,
    override val reason: String? = null
) : APIResponse

@Serializable
data class SettingsResponse(
    override val status: String,
    override val reason: String? = null,
    val enabled: Boolean? = null,
    val type: String? = null
) : APIResponse

@Serializable
data class BoolSettingBody(
    val device_id: String,
    val realtime_update: Boolean,
    val state: Boolean
)

@Serializable
data class TypeSettingBody(
    val device_id: String,
    val realtime_update: Boolean,
    val state: String
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
    override val status: String,
    override val reason: String? = null,
    val data: List<Float>
) : APIResponse

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
    override val status: String,
    override val reason: String? = null,
    val enabled: Boolean? = null,
    val start: String? = null,
    val stop: String? = null
) : APIResponse

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
    override val status: String,
    override val reason: String? = null,
    val visualizer_preset: String? = null,
    val autobrightness: Boolean? = null,
    val brightness: Float? = null,
    val clock_type: String? = null
) : APIResponse

@Serializable
data class UserInfoResponse(
    override val status: String,
    override val reason: String? = null,
    val name: String? = null,
    val timezone: String? = null
) : APIResponse

@Serializable
data class TimezoneBody(
    val value: String
)

val FQ_BACKEND_URL = if (BuildConfig.DEBUG) "https://testing.yndxfuck.ru" else "https://yndxfuck.ru"
val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

object FuckedQuasarClient {
    const val TAG = "FuckedQuasarClient"
    private var devices = listOf<Speaker>()
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchDevices(): RequestResponse {
        val result = Session.get("$FQ_BACKEND_URL/glagol/device_list")
        if (!result.ok || result.response == null) {
            return result
        }
        val body = result.response.body.string()
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

    private inline fun <reified T: APIResponse> doRequest(
        type: String, url: String, body: RequestBody? = null, deviceId: String? = null
    ): ReqResult<T> {
        val res = when(type) {
            "GET" -> {
                if (deviceId != null) {
                    Session.get("$FQ_BACKEND_URL$url?device_id=$deviceId")
                } else {
                    Session.get("$FQ_BACKEND_URL$url")
                }
            }
            "POST" -> {
                if (body == null) {
                    throw IllegalArgumentException("Request body is required!")
                }
                Session.post(FQ_BACKEND_URL + url, body)
            }

            else -> throw IllegalArgumentException("Unsupported request type!")
        }
        if (res.errorId == Errors.TIMEOUT) {
            return ReqResult(false, error = SettingsErrors.TIMEOUT)
        }
        if (res.response == null) {
            return ReqResult(false, error = SettingsErrors.UNKNOWN)
        }
        val code = res.response.code
        val parsed: T? = try {
            json.decodeFromString<T>(res.response.body.string())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode JSON!\n${e.message}")
            null
        }
        res.response.close()
        return if (code == 200 && parsed != null) {
            ReqResult(ok = true, data = parsed)
        } else {
            ReqResult(
                ok = false,
                error = when (code) {
                    400 -> when (parsed?.reason) {
                        "not_registered" -> SettingsErrors.NOT_LINKED
                        "invalid_name" -> SettingsErrors.INVALID_VALUE
                        "unsupported_action" -> SettingsErrors.UNSUPPORTED_ACTION
                        else -> {
                            Log.e(TAG,"Unexpected server response!\nCode: $code\nResponse: $parsed")
                            SettingsErrors.UNKNOWN
                        }
                    }
                    401 -> SettingsErrors.UNAUTHORIZED
                    else -> {
                        Log.e(TAG,"Unexpected server response!\nCode: $code\nResponse: $parsed")
                        SettingsErrors.UNKNOWN
                    }
                }
            )
        }
    }
    private inline fun <reified T: APIResponse> doGET(url: String, deviceId: String? = null): ReqResult<T> {
        return doRequest(type = "GET", url = url, deviceId = deviceId)
    }
    private inline fun <reified T: APIResponse> doPOST(url: String, body: RequestBody): ReqResult<T> {
        return doRequest(type = "POST", url = url, body = body)
    }

    fun linkDeviceStage1(deviceId: String): LinkDeviceResult {
        val body = json.encodeToString(LinkDeviceBody(device_id = deviceId, code = null))
        val res = Session.post("$FQ_BACKEND_URL/reg_device", body.toRequestBody(JSON_MEDIA_TYPE))
        if (res.errorId == Errors.TIMEOUT) {
            return LinkDeviceResult(false, LinkDeviceErrors.TIMEOUT)
        }
        if (res.response == null) {
            return LinkDeviceResult(false, error = LinkDeviceErrors.UNKNOWN)
        }
        val parsed = try {
            json.decodeFromString<GenericResponse>(res.response.body.string())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode JSON!\n${e.message}")
            null
        }
        return if (parsed?.status == "ok") {
            LinkDeviceResult(true)
        } else {
            LinkDeviceResult(
                false,
                when (parsed?.reason!!) {
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
        if (res.response == null) {
            return LinkDeviceResult(false, error = LinkDeviceErrors.UNKNOWN)
        }
        val parsed = try {
            json.decodeFromString<GenericResponse>(res.response.body.string())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode JSON!\n${e.message}")
            null
        }
        return if (parsed?.status == "ok") {
            LinkDeviceResult(true)
        } else {
            LinkDeviceResult(
                false,
                when (parsed?.reason!!) {
                    "unauthorized" -> LinkDeviceErrors.UNAUTHORIZED
                    "invalid_code" -> LinkDeviceErrors.INVALID_CODE
                    "timeout" -> LinkDeviceErrors.DEVICE_OFFLINE
                    else -> LinkDeviceErrors.UNKNOWN
                }
            )
        }
    }

    fun unlinkDevice(deviceId: String): ReqResult<GenericResponse> {
        val body = json.encodeToString(
            LinkDeviceBody(device_id = deviceId, code = null)
        ).toRequestBody(JSON_MEDIA_TYPE)
        return doPOST(url = "/unlink_device", body = body)
    }

    fun getJingleStatus(deviceId: String): ReqResult<SettingsResponse> {
        return doGET(url = "/get_jingle_status", deviceId = deviceId)
    }
    fun setJingleStatus(deviceId: String, enabled: Boolean): ReqResult<GenericResponse> {
        val body = json.encodeToString(BoolSettingBody(
            device_id = deviceId, realtime_update = true, state = enabled
        )).toRequestBody(JSON_MEDIA_TYPE)
        return doPOST(url = "/update_jingle", body = body)
    }

    fun getSSType(deviceId: String): ReqResult<SettingsResponse> {
        return doGET(url = "/get_ss_type_status", deviceId = deviceId)
    }
    fun setSSType(deviceId: String, type: String): ReqResult<GenericResponse> {
        val body = json.encodeToString(TypeSettingBody(
            device_id = deviceId, realtime_update = true, state = type
        )).toRequestBody(JSON_MEDIA_TYPE)
        return doPOST(url = "/update_ss_type", body = body)
    }

    fun renameDevice(deviceId: String, name: String): ReqResult<GenericResponse> {
        val body = json.encodeToString(NameSettingBody(
            device_id = deviceId, name = name
        )).toRequestBody(JSON_MEDIA_TYPE)
        return doPOST(url = "/rename_device", body = body)
    }

    fun getEQData(deviceId: String): ReqResult<EQSettingResponse> {
        return doGET(url = "/get_eq_data", deviceId = deviceId)
    }
    fun setEQData(deviceId: String, data: List<Float>): ReqResult<GenericResponse> {
        val body = json.encodeToString(EQSettingBody(
            device_id = deviceId, realtime_update = true, data = data
        )).toRequestBody(JSON_MEDIA_TYPE)
        return doPOST(url = "/update_eq", body = body)
    }

    fun getDNDData(deviceId: String): ReqResult<DNDSettingResponse> {
        return doGET(url = "/get_dnd_status", deviceId = deviceId)
    }
    fun setDNDData(deviceId: String, enable: Boolean, start: String? = null, stop: String? = null): ReqResult<GenericResponse> {
        if (!enable && (start != null || stop != null)) {
            throw IllegalArgumentException()
        }
        if ((start != null && stop == null) || (start == null && stop != null)) {
            throw IllegalArgumentException()
        }
        val body = json.encodeToString(DNDSettingBody(
            device_id = deviceId, realtime_update = true,
            enable = enable, start = start, stop = stop
        )).toRequestBody(JSON_MEDIA_TYPE)
        return doPOST(url = "/update_dnd", body = body)
    }

    fun getScreenSettings(deviceId: String): ReqResult<ScreenSettingResponse> {
        return doGET(url = "/get_screen_settings", deviceId = deviceId)
    }
    fun setVisualizerPreset(deviceId: String, name: String): ReqResult<GenericResponse> {
        val body = json.encodeToString(ScreenSettingBody(
            device_id = deviceId, realtime_update = true, visualizer_preset = name
        )).toRequestBody(JSON_MEDIA_TYPE)
        return doPOST(url = "/update_screen_settings", body = body)
    }
    fun setClockType(deviceId: String, type: String): ReqResult<GenericResponse> {
        val body = json.encodeToString(ScreenSettingBody(
            device_id = deviceId, realtime_update = true, clock_type = type
        )).toRequestBody(JSON_MEDIA_TYPE)
        return doPOST(url = "/update_screen_settings", body = body)
    }

    // User
    fun getUserInfo() : ReqResult<UserInfoResponse> {
        return doGET(url = "/user_info")
    }
    fun updateTimezone(value: String) : ReqResult<GenericResponse> {
        val body = json.encodeToString(TimezoneBody(value)).toRequestBody(JSON_MEDIA_TYPE)
        return doPOST(url = "/update_timezone", body = body)
    }
}
