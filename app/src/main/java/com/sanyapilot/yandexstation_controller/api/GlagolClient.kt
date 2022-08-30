package com.sanyapilot.yandexstation_controller.api

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
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
    val volume: Float? = null,
    val position: Int? = null,
    val serverActionEventPayload: ServerActionEventPayload? = null
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
class GlagolClient(private val speaker: Speaker) : WebSocketListener() {
    private var localDevice: LocalDevice? = null
    private var token: String? = null
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var ws: WebSocket
    private lateinit var listener: (data: StationState) -> Unit
    private var closedListener: (() -> Unit)? = null
    private var failureListener: (() -> Unit)? = null

    fun start(func: (data: StationState) -> Unit): GlagolResponse {
        localDevice = mDNSWorker.getDevice(speaker.quasar_info.device_id)
        if (localDevice == null)
            return GlagolResponse(false, GlagolErrors.DEVICE_NOT_DISCOVERED)

        // Get device token
        val response = Session.get("https://quasar.yandex.ru/glagol/token?device_id=${speaker.quasar_info.device_id}&platform=${speaker.quasar_info.platform}")
        val parsed = json.decodeFromString<TokenResponse>(response.response!!.body!!.string())
        token = parsed.token
        Log.d(TAG, "Got token $token")

        Session.wsConnect("wss://${localDevice!!.host}:${localDevice!!.port}", this)

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

    fun setOnSocketClosedListener(callback: () -> Unit) {
        closedListener = callback
    }

    fun setOnFailureListener(callback: () -> Unit) {
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
        listener(json.decodeFromString<StationResponse>(text).state)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        t.printStackTrace()
        failureListener?.let { it() }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "SOCKET CLOSING...")
        closedListener?.let { it() }
    }
}

data class LocalDevice(
    val uuid: String,
    val host: String,
    val port: Int
)

// Thanks to
// https://stackoverflow.com/questions/57940021/nsdmanager-resolvelistener-error-code-3-failure-already-active
class NsdHelper(val context: Context) {

    // Declare DNS-SD related variables for service discovery
    val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    private var resolveListenerBusy = AtomicBoolean(false)
    private var pendingNsdServices = ConcurrentLinkedQueue<NsdServiceInfo>()
    var resolvedNsdServices: MutableList<NsdServiceInfo> = Collections.synchronizedList(ArrayList<NsdServiceInfo>())

    companion object {

        // Type of services to look for
        const val NSD_SERVICE_TYPE: String = "_yandexio._tcp."
        // Services' Names must start with this
        // const val NSD_SERVICE_NAME: String = "MyServiceName-"
    }

    // Initialize Listeners
    fun initializeNsd() {
        // Initialize only resolve listener
        initializeResolveListener()
    }

    // Instantiate DNS-SD discovery listener
    private fun initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        discoveryListener = object : NsdManager.DiscoveryListener {

            override fun onDiscoveryStarted(regType: String) {
                // Called as soon as service discovery begins.
                Log.d(TAG, "Service discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // A service was found! Do something with it
                Log.d(TAG, "Service discovery success: $service")

                if ( service.serviceType == NSD_SERVICE_TYPE ) {
                    // Both service type and service name are the ones we want
                    // If the resolver is free, resolve the service to get all the details
                    if (resolveListenerBusy.compareAndSet(false, true)) {
                        nsdManager.resolveService(service, resolveListener)
                    } else {
                        // Resolver was busy. Add the service to the list of pending services
                        pendingNsdServices.add(service)
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: $service")

                // If the lost service was in the queue of pending services, remove it
                for (pending in pendingNsdServices) {
                    if (pending.serviceName == service.serviceName)
                        pendingNsdServices.remove(pending)
                }

                // If the lost service was in the list of resolved services, remove it
                synchronized(resolvedNsdServices) {
                    for (resolved in pendingNsdServices) {
                        if (resolved.serviceName == service.serviceName)
                            resolvedNsdServices.remove(resolved)
                    }
                }

                // Do the rest of the processing for the lost service
                onNsdServiceLost(service)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start Discovery failed: Error code: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop Discovery failed: Error code: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }
    }

    // Instantiate DNS-SD resolve listener to get extra information about the service
    private fun initializeResolveListener() {
        resolveListener =  object : NsdManager.ResolveListener {

            override fun onServiceResolved(service: NsdServiceInfo) {
                Log.d(TAG, "Resolve Succeeded: $service")

                // Register the newly resolved service into our list of resolved services
                resolvedNsdServices.add(service)

                // Process the newly resolved service
                onNsdServiceResolved(service)

                // Process the next service waiting to be resolved
                resolveNextInQueue()
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Called when the resolve fails. Use the error code to debug.
                Log.e(TAG, "Resolve failed: $serviceInfo - Error code: $errorCode")

                // Process the next service waiting to be resolved
                resolveNextInQueue()
            }
        }
    }

    // Start discovering services on the network
    fun discoverServices() {
        // Cancel any existing discovery request
        stopDiscovery()

        initializeDiscoveryListener()

        nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    // Stop DNS-SD service discovery
    fun stopDiscovery() {
        if (discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } finally {
            }
            discoveryListener = null
        }
    }

    // Resolve next NSD service pending resolution
    private fun resolveNextInQueue() {
        // Get the next NSD service waiting to be resolved from the queue
        val nextNsdService = pendingNsdServices.poll()
        if (nextNsdService != null) {
            // There was one. Send to be resolved.
            nsdManager.resolveService(nextNsdService, resolveListener)
        }
        else {
            // There was no pending service. Release the flag
            resolveListenerBusy.set(false)
        }
    }

    // Function to be overriden with custom logic for new service resolved
    private fun onNsdServiceResolved(service: NsdServiceInfo) {
        Log.d(TAG, "Device ${service.serviceName} deviceId ${String(service.attributes["deviceId"]!!)}")
        mDNSWorker.addDevice(LocalDevice(
            uuid = String(service.attributes["deviceId"]!!),
            host = service.host.hostAddress!!,
            port = service.port
        ))
    }

    // Function to be overriden with custom logic for service lost
    private fun onNsdServiceLost(service: NsdServiceInfo) {
        Log.d(TAG, "Service ${service.serviceName} lost!")
    }
}

object mDNSWorker {
    private val devices = mutableListOf<LocalDevice>()
    private val listeners = mutableMapOf<String, () -> Unit>()

    fun init(context: Context) {
        Log.d(TAG, "Registering listener")
        val helper = NsdHelper(context)
        helper.initializeNsd()
        helper.discoverServices()
    }
    fun addListener(uuid: String, listener: () -> Unit) {
        listeners[uuid] = listener
    }
    fun addDevice(device: LocalDevice) {
        devices.add(device)
        listeners[device.uuid]?.let { it() }
    }
    fun removeDevice(uuid: String) {
        for (device in devices) {
            if (device.uuid == uuid)
                devices.remove(device)
                return
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