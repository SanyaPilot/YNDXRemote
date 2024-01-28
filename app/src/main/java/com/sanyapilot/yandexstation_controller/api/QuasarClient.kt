@file:Suppress("PropertyName")  // Models require underscores

package com.sanyapilot.yandexstation_controller.api
import android.util.Log
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class DevicesResponse(
    val households: List<QuasarHousehold>
)

@Serializable
data class QuasarHousehold (
    val name: String,
    val rooms: List<QuasarRoom>,
    val all: List<QuasarDevice>,
    @Contextual
    val sharing_info: Any? = null
)

@Serializable
data class QuasarRoom (
    val name: String,
    val items: List<QuasarDevice>,
)

@Serializable
data class QuasarDevice (
    val id: String,
    val name: String,
    val type: String,
    val quasar_info: QuasarInfo? = null,
)

@Serializable
data class QuasarInfo (
    val device_id: String,
    val platform: String
)

@Serializable
data class Speaker(
    val id: String,
    val smartHomeId: String,
    val name: String,
    val platform: String
)

enum class SettingsErrors {
    UNAUTHORIZED, NOT_LINKED, INVALID_VALUE, TIMEOUT, UNKNOWN, NO_INTERNET
}

interface APIResponse {
    val status: String
    val code: String?
}

data class ReqResult<T>(
    val ok: Boolean,
    val error: SettingsErrors? = null,
    val data: T? = null
)

@Serializable
data class GenericResponse(
    override val status: String,
    override val code: String? = null,
    val message: String? = null
) : APIResponse

@Serializable
data class ConfigUpdateResponse(
    override val status: String,
    override val code: String? = null,
    val message: String? = null,
    val version: String? = null
) : APIResponse

@Serializable
data class ConfigResponse(
    override val status: String,
    override val code: String? = null,
    val quasar_config: DeviceConfig? = null,
    val quasar_config_version: String? = null
) : APIResponse

@Serializable
data class PostConfigBody(
    val config: DeviceConfig,
    val version: String
)

@Serializable
data class RenameDeviceBody(
    val new_name: String,
    val old_name: String
)

@Serializable
data class DeviceConfig(
    var equalizer: EqualizerConfig? = null,
    var screenSaverConfig: ScreenSaverConfig? = null,
    var hdmiAudio: Boolean? = null,
    var dndMode: DNDModeConfig? = null,
    var led: LEDConfig? = null,
    var tof: Boolean? = null,
    val locale: String,
    val location: Map<String, String> = mapOf(),
    var name: String,
    val beta: Boolean
)

@Serializable
data class EqualizerConfig(
    val enabled: Boolean,
    val bands: List<EqualizerBandConfig>,
    val custom_preset_bands: MutableList<Float>,
    val active_preset_id: String,
    val smartEnabled: Boolean? = null
)

@Serializable
data class EqualizerBandConfig(
    var gain: Float,
    val freq: Int,
    val width: Int
)

@Serializable
data class ScreenSaverConfig(
    val type: String
)

@Serializable
data class DNDModeConfig(
    var enabled: Boolean,
    var starts: String,
    var ends: String,
    val platformSettings: Map<String, String> = mapOf()
)

@Serializable
data class LEDConfig(
    val brightness: LEDBrightnessConfig,
    val music_equalizer_visualization: LEDEQVisConfig,
    val time_visualization: LEDTimeVisConfig
)

@Serializable
data class LEDBrightnessConfig(
    var auto: Boolean,
    var value: Float
)

@Serializable
data class LEDEQVisConfig(
    var auto: Boolean,
    var style: String
)

@Serializable
data class LEDTimeVisConfig(
    var size: String
)

val QUASAR_IOT_BACKEND_URL = "https://iot.quasar.yandex.ru/m/user"
val QUASAR_IOT_V2_BACKEND_URL = "https://iot.quasar.yandex.ru/m/v2/user"
val QUASAR_IOT_V3_BACKEND_URL = "https://iot.quasar.yandex.ru/m/v3/user"

val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

object QuasarClient {
    const val TAG = "QuasarClient"
    private var devices = mutableListOf<Speaker>()
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchDevices(): RequestResponse {
        val result = Session.get("$QUASAR_IOT_V3_BACKEND_URL/devices")
        if (!result.ok || result.response == null) {
            return result
        }
        val parsed = json.decodeFromString<DevicesResponse>(result.response.body.string())
        result.response.close()

        devices.clear()
        for (house in parsed.households) {
            if (house.sharing_info != null){
                continue
            }
            for (device in house.all) {
                if (device.type.startsWith("devices.types.smart_speaker") && device.quasar_info != null) {
                    devices.add(Speaker(
                        id = device.quasar_info.device_id,
                        smartHomeId = device.id,
                        name = device.name,
                        platform = device.quasar_info.platform
                    ))
                }
            }
        }
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
        method: Methods, prefix: String, path: String, body: RequestBody? = null, deviceId: String? = null
    ): ReqResult<T> {
        val res = when(method) {
            Methods.GET -> {
                if (deviceId != null) {
                    Session.get("$prefix/$deviceId$path")
                } else {
                    Session.get(prefix + path)
                }
            }
            Methods.POST -> {
                if (body == null) {
                    throw IllegalArgumentException("Request body is required!")
                }
                if (deviceId != null) {
                    Session.post("$prefix/$deviceId$path", body)
                } else {
                    Session.post(prefix + path, body)
                }
            }
            Methods.PUT -> {
                if (body == null) {
                    throw IllegalArgumentException("Request body is required!")
                }
                if (deviceId != null) {
                    Session.put("$prefix/$deviceId$path", body)
                } else {
                    Session.put(prefix + path, body)
                }
            }
            Methods.DELETE -> {
                if (deviceId != null) {
                    Session.delete("$prefix/$deviceId$path")
                } else {
                    Session.delete(prefix + path)
                }
            }

            else -> throw IllegalArgumentException("Unsupported request type!")
        }
        if (res.errorId == Errors.TIMEOUT) {
            return ReqResult(false, error = SettingsErrors.TIMEOUT)
        }
        if (res.errorId == Errors.CONNECTION_ERROR) {
            return ReqResult(false, error = SettingsErrors.NO_INTERNET)
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
        return if (code == 200 && parsed != null && parsed.status == "ok") {
            ReqResult(ok = true, data = parsed)
        } else {
            ReqResult(
                ok = false,
                error = when (code) {
                    400 -> when (parsed?.code) {
                        "DEVICE_NOT_FOUND" -> SettingsErrors.NOT_LINKED
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
                },
                data = parsed
            )
        }
    }
    private inline fun <reified T: APIResponse> doGET(prefix: String, path: String, deviceId: String? = null): ReqResult<T> {
        return doRequest(Methods.GET, prefix, path, deviceId = deviceId)
    }
    private inline fun <reified T: APIResponse> doPOST(prefix: String, path: String, deviceId: String? = null, body: RequestBody): ReqResult<T> {
        return doRequest(Methods.POST, prefix, path, body, deviceId)
    }
    private inline fun <reified T: APIResponse> doPUT(prefix: String, path: String, deviceId: String? = null, body: RequestBody): ReqResult<T> {
        return doRequest(Methods.PUT, prefix, path, body, deviceId)
    }
    private inline fun <reified T: APIResponse> doDELETE(prefix: String, path: String, deviceId: String? = null): ReqResult<T> {
        return doRequest(Methods.DELETE, prefix, path, deviceId = deviceId)
    }

    fun unlinkDevice(deviceId: String): ReqResult<GenericResponse> {
        return doDELETE(
            prefix = "$QUASAR_IOT_BACKEND_URL/devices",
            path = "",
            deviceId = deviceId
        )
    }

    fun renameDevice(deviceId: String, newName: String, oldName: String): ReqResult<GenericResponse> {
        return doPUT(
            prefix = "$QUASAR_IOT_BACKEND_URL/devices",
            path = "/name",
            deviceId = deviceId,
            body = json.encodeToString(RenameDeviceBody(
                new_name = newName,
                old_name = oldName
            )).toRequestBody(JSON_MEDIA_TYPE)
        )
    }

    fun getDeviceConfig(deviceId: String): ReqResult<ConfigResponse> {
        return doGET(
            prefix = "$QUASAR_IOT_V2_BACKEND_URL/devices",
            path = "/configuration",
            deviceId = deviceId
        )
    }

    fun updateDeviceConfig(deviceId: String, config: DeviceConfig, version: String): ReqResult<ConfigUpdateResponse> {
        return doPOST(
            prefix = "$QUASAR_IOT_V3_BACKEND_URL/devices",
            path = "/configuration/quasar",
            deviceId = deviceId,
            body = json.encodeToString(PostConfigBody(
                config = config,
                version = version
            )).toRequestBody(JSON_MEDIA_TYPE)
        )
    }
}
