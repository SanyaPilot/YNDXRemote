package com.sanyapilot.yandexstation_controller.api
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


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
data class LinkDeviceResponse(
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

const val FQ_BACKEND_URL = "https://yndxfuck.ru"
val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

object FuckedQuasarClient {
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
        val parsed = json.decodeFromString<LinkDeviceResponse>(res.response!!.body.string())
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
        val parsed = json.decodeFromString<LinkDeviceResponse>(res.response!!.body.string())
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
}