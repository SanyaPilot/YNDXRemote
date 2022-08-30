package com.sanyapilot.yandexstation_controller.api
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.concurrent.thread

@Serializable
data class DevicesResponse (
    //val status: String,
    //val request_id: String,
    val rooms: List<Room>,
    //val groups: List<Int>? = null,
    //val unconfigured_devices: List<Device>,
    val speakers: List<GenericDevice>
)

@Serializable
data class Room (
    //val id: String,
    val name: String,
    val devices: List<GenericDevice>,
)

@Serializable
data class GenericDevice (
    val id: String,
    val name: String,
    val type: String,
    val icon_url: String,
    val quasar_info: QuasarInfo? = null,
)

@Serializable
data class QuasarInfo (
    val device_id: String,
    val platform: String,
    val multiroom_available: Boolean,
    val multistep_scenarios_available: Boolean
)

@Serializable
data class Capability (
    val reportable: Boolean,
    val retrievable: Boolean,
    val type: String,
    //val parameters: Parameters
)

@Serializable
data class ScenarioResponse (
    val scenarios: List<Scenario>
)

@Serializable
data class Scenario (
    val id: String,
    val name: String,
    val executable: Boolean,
    val devices: List<String>,
    val triggers: List<ScenarioTrigger>,
    val is_active: Boolean
)

@Serializable
data class NewScenario (
    val name: String,
    val icon: String,
    val triggers: List<ScenarioTrigger>,
    val steps: List<ScenarioStep>
)

@Serializable
data class ScenarioTrigger (
    val type: String,
    val value: String
)

@Serializable
data class ScenarioStep (
    val type: String,
    val parameters: ScenarioParameters
)

@Serializable
data class ScenarioParameters (
    val requested_speaker_capabilities: List<String>,
    val launch_devices: List<ScenarioDevice>
)

@Serializable
data class ScenarioDevice (
    val id: String,
    val capabilities: List<ScenarioCapability>
)

@Serializable
data class ScenarioCapability (
    val type: String,
    val state: ScenarioCapState
)

@Serializable
data class ScenarioCapState (
    val instance: String,
    val value: String
)

data class Speaker(
    val id: String,
    val name: String,
    val type: String,
    val icon_url: String,
    val quasar_info: QuasarInfo,
    val scenarioId: String // Scenario for TTS over quasar
)

const val EN_MASK = "0123456789abcdef-"
const val RU_MASK = "енвапролдгясмитбю"

const val QUASAR_URL_PREFIX = "https://iot.quasar.yandex.ru/m/user"
val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

object QuasarClient {
    private var devices: MutableList<GenericDevice> = ArrayList()
    private val rooms: MutableList<Room> = ArrayList()
    private val noRoomDevices: MutableList<GenericDevice> = ArrayList()
    private val json = Json { ignoreUnknownKeys = true }

    private val speakers = mutableListOf<Speaker>()
    //private val scenarios = mutableMapOf<String, Scenario>()

    private fun decodeScenarioID(id: String): String {
        var result = ""
        for (sym in id.drop(4)) {
            result += EN_MASK[RU_MASK.indexOf(sym)]
        }
        return result
    }
    private fun encodeScenarioID(id: String): String {
        var result = "ЯСК "
        for (sym in id) {
            result += RU_MASK[EN_MASK.indexOf(sym)]
        }
        return result
    }

    private fun fetchDevices() {
        val result = Session.get("$QUASAR_URL_PREFIX/devices")
        val body = result.response!!.body!!.string()
        Log.d(TAG, body)
        val parsed = json.decodeFromString<DevicesResponse>(body)
        result.response.close()

        Log.e(TAG, parsed.toString())
        for (room in parsed.rooms) {
            rooms.add(room)
            for (device in room.devices) {
                devices.add(device)
            }
        }
        for (device in parsed.speakers) {
            devices.add(device)
            noRoomDevices.add(device)
        }
    }
    private fun loadScenarios(): Map<String, Scenario> {
        val result = Session.get("$QUASAR_URL_PREFIX/scenarios")
        val body = result.response!!.body!!.string()
        Log.d(TAG, body)
        val parsed = json.decodeFromString<ScenarioResponse>(body)
        val scenarios = mutableMapOf<String, Scenario>()
        for (scen in parsed.scenarios) {
            if (scen.name.startsWith("ЯСК")) {
                scenarios[decodeScenarioID(scen.name)] = scen
            }
        }
        return scenarios
    }
    private fun createScenario(deviceId: String) {
        val name = encodeScenarioID(deviceId)
        val scenario = NewScenario(
            name = name,
            icon = "home",
            triggers = listOf(ScenarioTrigger(
                type = "scenario.trigger.voice",
                value = name.drop(4)
            )),
            steps = listOf(ScenarioStep(
                type = "scenarios.steps.actions",
                parameters = ScenarioParameters(
                    requested_speaker_capabilities = listOf(),
                    launch_devices = listOf(ScenarioDevice(
                        id = deviceId,
                        capabilities = listOf(ScenarioCapability(
                            type = "devices.capabilities.quasar.server_action",
                            state = ScenarioCapState(
                                instance = "phrase_action",
                                value = "пустышка"
                            )
                        ))
                    ))
                )
            ))
        )
        val jsonString = json.encodeToString(scenario)
        val body = jsonString.toRequestBody(JSON_MEDIA_TYPE)
        val result = Session.post("$QUASAR_URL_PREFIX/scenarios", body)
        assert(JSONObject(result.response!!.body!!.string()).getString("status") == "ok")
    }
    fun prepareSpeakers() {
        if (devices.size == 0) {
            fetchDevices()
        }
        for (device in devices) {
            if (device.type.startsWith("devices.types.smart_speaker")) {
                var scenarios = loadScenarios()
                if (!scenarios.containsKey(device.id)) {
                    createScenario(device.id)
                    scenarios = loadScenarios()
                }
                val newSpeaker = Speaker(
                    id = device.id,
                    name = device.name,
                    type = device.type,
                    icon_url = device.icon_url,
                    quasar_info = device.quasar_info!!,
                    scenarioId = scenarios[device.id]!!.id
                )
                speakers.add(newSpeaker)
            }
        }
    }
    fun getSpeakers(): List<Speaker> = speakers

    fun getSpeakerById(deviceId: String): Speaker? {
        if (speakers.size > 0) {
            for (speaker in speakers) {
                if (speaker.id == deviceId) {
                    return speaker
                }
            }
        }
        return null
    }
    fun getSpeakersByRoom(): Map<String?, List<GenericDevice>> {
        if (devices.size == 0) {
            fetchDevices()
        }
        val result = mutableMapOf<String?, MutableList<GenericDevice>>()
        for (room in rooms) {
            result[room.name] = mutableListOf()
            for (device in room.devices) {
                if (device.type.startsWith("devices.types.smart_speaker"))
                    result[room.name]!!.add(device)
            }
            if (result[room.name]!!.size == 0)
                result.remove(room.name)
        }
        result[null] = mutableListOf()
        for (device in noRoomDevices) {
            if (device.type.startsWith("devices.types.smart_speaker"))
                result[null]!!.add(device)
        }
        Log.d(TAG, result.toString())
        return result
    }
    fun send(device: Speaker, text: String, isTTS: Boolean) {
        thread(start = true) {
            val name = encodeScenarioID(device.id)
            val mode = if (isTTS) "phrase_action" else "text_action"
            val scenario = NewScenario(
                name = name,
                icon = "home",
                triggers = listOf(
                    ScenarioTrigger(
                        type = "scenario.trigger.voice",
                        value = name.drop(4)
                    )
                ),
                steps = listOf(
                    ScenarioStep(
                        type = "scenarios.steps.actions",
                        parameters = ScenarioParameters(
                            requested_speaker_capabilities = listOf(),
                            launch_devices = listOf(
                                ScenarioDevice(
                                    id = device.id,
                                    capabilities = listOf(
                                        ScenarioCapability(
                                            type = "devices.capabilities.quasar.server_action",
                                            state = ScenarioCapState(
                                                instance = mode,
                                                value = text
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            val scenId = device.scenarioId

            val jsonString = json.encodeToString(scenario)
            var body = jsonString.toRequestBody(JSON_MEDIA_TYPE)
            var result = Session.put("$QUASAR_URL_PREFIX/scenarios/$scenId", body)
            assert(JSONObject(result.response!!.body!!.string()).getString("status") == "ok")

            body = byteArrayOf().toRequestBody(null, 0)
            result = Session.post("$QUASAR_URL_PREFIX/scenarios/$scenId/actions", body)
            assert(JSONObject(result.response!!.body!!.string()).getString("status") == "ok")
        }
    }
}