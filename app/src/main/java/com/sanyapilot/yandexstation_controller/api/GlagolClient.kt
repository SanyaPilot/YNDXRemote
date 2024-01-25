package com.sanyapilot.yandexstation_controller.api

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

enum class GlagolErrors {
    DEVICE_NOT_DISCOVERED
}

data class GlagolResponse(
    val ok: Boolean,
    val errorId: GlagolErrors? = null
)

@Serializable
data class TokenResponse(
    val token: String
)

@Serializable
data class GlagolMessage(
    val conversationToken: String,
    val id: String,
    val payload: GlagolPayload,
    val sentTime: ULong
)

@Serializable
data class StationResponse(
    val state: StationState
)

@Serializable
data class StationState(
    val aliceState: String,
    val canStop: Boolean,
    val playerState: PlayerState? = null,
    val playing: Boolean,
    val volume: Float
)

@Serializable
data class PlayerState(
    val duration: Float,
    val entityInfo: EntityInfo,
    val extra: PlayerExtra? = null,
    val hasNext: Boolean,
    val hasPause: Boolean,
    val hasPlay: Boolean,
    val hasPrev: Boolean,
    val hasProgressBar: Boolean,
    val playlistId: String,
    val playlistType: String,
    val progress: Float,
    val showPlayer: Boolean? = null,
    val subtitle: String,
    val title: String,
    val type: String
)

@Serializable
data class EntityInfo(
    val id: String,
    val next: GenericTrack? = null,
    val prev: GenericTrack? = null,
    val repeatMode: String? = null,
    val shuffled: Boolean? = null,
    val type: String
)

@Serializable
data class PlayerExtra(
    val coverURI: String
)

@Serializable
data class GenericTrack(
    val id: String,
    val type: String
)

@Serializable
data class GlagolPayload(
    val command: String,
    val text: String? = null,
    val volume: Float? = null,
    val position: Int? = null,
    val serverActionEventPayload: ServerActionEventPayload? = null,
    val action: String? = null,
    var scrollAmount: String? = null,
    var scrollExactValue: Int? = null,
    val type: String? = null,
    val id: String? = null,
    var offset: Float? = null
)

@Serializable
data class ServerActionEventPayload(
    val type: String,
    val name: String,
    val payload: ServerActionPayload
)

@Serializable
data class ServerActionPayload(
    val form_update: ServerActionForm,
    val resubmit: Boolean
)

@Serializable
data class ServerActionForm(
    val name: String,
    val slots: List<FormSlot>
)

@Serializable
data class FormSlot(
    val type: String,
    val name: String,
    val value: String
)

// Glagol local communication protocol

val QUASAR_BACKEND_URL = "https://quasar.yandex.ru"
class GlagolClient(private val speaker: Speaker) : WebSocketListener() {
    companion object {
        private const val TAG = "GlagolClient"
    }

    private var localDevice: LocalDevice? = null
    private var token: String? = null
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var ws: WebSocket
    private lateinit var listener: (data: StationState) -> Unit
    private var closedListener: ((Speaker) -> Unit)? = null
    private var failureListener: ((Speaker) -> Unit)? = null

    fun start(func: (data: StationState) -> Unit): GlagolResponse {
        localDevice = mDNSWorker.getDevice(speaker.id)
        if (localDevice == null)
            return GlagolResponse(false, GlagolErrors.DEVICE_NOT_DISCOVERED)

        // Get device token
        val response = Session.get("$QUASAR_BACKEND_URL/glagol/token?device_id=${speaker.id}&platform=${speaker.platform}")
        val parsed = json.decodeFromString<TokenResponse>(response.response!!.body.string())
        token = parsed.token
        Log.d(TAG, "Got token $token")

        try {
            Session.wsConnect("wss://${localDevice!!.host}:${localDevice!!.port}", this)
        } catch (e: Exception) {
            Log.e(TAG, "Error at connecting to the websocket!", e)
            failureListener?.let { it(speaker) }
        }

        listener = func

        return GlagolResponse(true)
    }

    fun stop() {
        ws.close(1000, null)
    }

    fun send(payload: GlagolPayload) {
        thread(start = true) {
            val message = GlagolMessage(
                conversationToken = token!!,
                id = UUID.randomUUID().toString(),
                payload = payload,
                sentTime = System.currentTimeMillis().toULong()
            )
            val encoded = json.encodeToString(message)
            ws.send(encoded)
            Log.d(TAG, "SENT MESSAGE")
            Log.d(TAG, encoded)
        }
    }

    fun setOnSocketClosedListener(callback: (Speaker) -> Unit) {
        closedListener = callback
    }

    fun setOnFailureListener(callback: (Speaker) -> Unit) {
        failureListener = callback
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "SOCKET OPENED")
        ws = webSocket

        // Sending init message
        val uuid = UUID.randomUUID().toString()
        Log.d(TAG, "Gen UUID: $uuid")
        val message = GlagolMessage(
            conversationToken = token!!,
            id = uuid,
            payload = GlagolPayload(command = "softwareVersion"),
            sentTime = System.currentTimeMillis().toULong()
        )
        val payload = json.encodeToString(message)
        ws.send(payload)
        Log.d(TAG, "Sent test msg! $payload")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "RECEIVED MESSAGE")
        Log.d(TAG, JSONObject(text).getJSONObject("state").toString())
        try {
            listener(json.decodeFromString<StationResponse>(text).state)
        } catch (e: SerializationException) {
            Log.e(TAG, "Station throws invalid response!")
            e.printStackTrace()
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "Failure occurred while working with a WS!")
        t.printStackTrace()
        failureListener?.let { it(speaker) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "SOCKET CLOSING...")
        closedListener?.let { it(speaker) }
    }
}

data class LocalDevice(
    val uuid: String,
    val host: String,
    val port: Int
)

// Thanks to Home Assistant!
// https://github.com/home-assistant/android/blob/16eabfe34f6be1110a3a3899562b3d680b9ad14d/app/src/main/java/io/homeassistant/companion/android/onboarding/discovery/HomeAssistantSearcher.kt
class StationSearcher constructor(
    context: Context,
) : NsdManager.DiscoveryListener {

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager: WifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    companion object {
        private const val SERVICE_TYPE = "_yandexio._tcp"
        private const val TAG = "StationNSD"
        private val lock = ReentrantLock()
    }

    private var isSearching = false
    private var multicastLock: WifiManager.MulticastLock? = null

    fun beginSearch() {
        if (isSearching) {
            return
        }
        isSearching = true
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, this)
        } catch (e: Exception) {
            Log.e(TAG, "Issue starting discover.", e)
            isSearching = false
            return
        }
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock(TAG)
                multicastLock?.setReferenceCounted(true)
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Issue acquiring multicast lock", e)
            // Discovery might still work so continue
        }
    }

    fun stopSearch() {
        if (!isSearching) {
            return
        }
        isSearching = false
        try {
            nsdManager.stopServiceDiscovery(this)
            multicastLock?.release()
            multicastLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Issue stopping discovery", e)
        }
    }

    // Called as soon as service discovery begins.
    override fun onDiscoveryStarted(regType: String) {
        Log.d(TAG, "Service discovery started")
    }

    override fun onServiceFound(foundService: NsdServiceInfo) {
        Log.i(TAG, "Service discovery found: $foundService")
        lock.lock()
        Thread.sleep(100) // Чтобы просралось
        nsdManager.resolveService(
            foundService,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(failedService: NsdServiceInfo?, errorCode: Int) {
                    // discoveryView.onScanError()
                    Log.w(TAG, "Failed to resolve service: $failedService, error: $errorCode")
                    lock.unlock()
                    // Жуткий костыль ALERT!
                    this@StationSearcher.onServiceFound(failedService!!)
                }

                override fun onServiceResolved(resolvedService: NsdServiceInfo?) {
                    Log.i(TAG, "Service resolved: $resolvedService")
                    resolvedService?.let {
                        mDNSWorker.addDevice(LocalDevice(
                            uuid = String(it.attributes["deviceId"]!!),
                            host = it.host.hostAddress!!,
                            port = it.port
                        ))
                    }
                    lock.unlock()
                }
            }
        )
    }

    override fun onServiceLost(service: NsdServiceInfo) {
        // When the network service is no longer available.
        // Internal bookkeeping code goes here.
        Log.i(TAG, "service lost: $service")
        mDNSWorker.removeDevice(service.serviceName.split('-')[1])
    }

    override fun onDiscoveryStopped(serviceType: String) {
        Log.i(TAG, "Discovery stopped: $serviceType")
    }

    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code:$errorCode")
        stopSearch()
    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code:$errorCode")
        stopSearch()
    }
}

object mDNSWorker {
    private val devices = mutableListOf<LocalDevice>()
    private val listeners = mutableMapOf<String, () -> Unit>()
    private val lostListeners = mutableMapOf<String, () -> Unit>()
    private lateinit var helper: StationSearcher

    fun init(context: Context) {
        helper = StationSearcher(context)
    }
    fun stop() {
        helper.stopSearch()
    }
    fun start() {
        helper.beginSearch()
    }
    fun isReady(): Boolean {
        return this::helper.isInitialized
    }
    fun addListener(uuid: String, listener: () -> Unit) {
        listeners[uuid] = listener
    }
    fun removeListener(uuid: String) {
        listeners.remove(uuid)
    }
    fun addOnLostListener(uuid: String, listener: () -> Unit) {
        lostListeners[uuid] = listener
    }
    fun removeOnLostListener(uuid: String) {
        lostListeners.remove(uuid)
    }
    fun removeAllListeners(uuid: String) {
        removeListener(uuid)
        removeOnLostListener(uuid)
    }
    fun addDevice(device: LocalDevice) {
        devices.add(device)
        listeners[device.uuid]?.let { it() }
    }
    fun removeDevice(uuid: String, callListener: Boolean = true) {
        for (device in devices) {
            if (device.uuid == uuid) {
                devices.remove(device)
                if (callListener)
                    lostListeners[uuid]?.let { it() }
                return
            }
        }
    }
    fun getDevice(uuid: String): LocalDevice? {
        for (device in devices) {
            if (device.uuid == uuid)
                return device
        }
        return null
    }
    fun deviceExists(uuid: String): Boolean {
        for (device in devices) {
            if (device.uuid == uuid)
                return true
        }
        return false
    }
}