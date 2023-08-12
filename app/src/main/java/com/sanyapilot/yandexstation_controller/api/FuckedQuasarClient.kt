package com.sanyapilot.yandexstation_controller.api
import android.util.Log
import com.sanyapilot.yandexstation_controller.main_screen.TAG
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType


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
    val external_port: Int,
    val ip_addresses: List<String>,
    val mac_addresses: List<String>
)

const val FQ_BACKEND_URL = "https://testing.yndxfuck.ru"
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
        Log.d(TAG, body)
        val parsed = json.decodeFromString<DevicesResponse>(body)
        result.response.close()

        Log.e(TAG, parsed.toString())
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
}