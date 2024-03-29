package com.sanyapilot.yandexstation_controller.service
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media.MediaBrowserServiceCompat
import androidx.media.VolumeProviderCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.sanyapilot.yandexstation_controller.R
import com.sanyapilot.yandexstation_controller.api.GlagolClient
import com.sanyapilot.yandexstation_controller.api.GlagolPayload
import com.sanyapilot.yandexstation_controller.api.QuasarClient
import com.sanyapilot.yandexstation_controller.api.Session
import com.sanyapilot.yandexstation_controller.api.Speaker
import com.sanyapilot.yandexstation_controller.api.StationState
import com.sanyapilot.yandexstation_controller.api.mDNSWorker
import com.sanyapilot.yandexstation_controller.device.DeviceActivity
import com.sanyapilot.yandexstation_controller.main_screen.PLAYER_CHANNEL_ID
import com.sanyapilot.yandexstation_controller.misc.fallbackConfig
import com.sanyapilot.yandexstation_controller.misc.stationConfigs
import kotlin.concurrent.thread

private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
private const val PLAYER_NOTIFICATION_ID = 732

const val DEVICE_ID = "com.sanyapilot.yandexstation_controller.deviceId"
const val DEVICE_NAME = "com.sanyapilot.yandexstation_controller.deviceName"
const val DEVICE_PLATFORM = "com.sanyapilot.yandexstation_controller.devicePlatform"

class StationControlService : MediaBrowserServiceCompat() {
    companion object {
        private const val TAG = "StationControlService"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var mediaMetadataBuilder: MediaMetadataCompat.Builder
    private lateinit var glagolClient: GlagolClient
    private lateinit var speaker: Speaker
    private lateinit var notificationManager: NotificationManager
    private lateinit var volumeProvider: VolumeProviderCompat

    private var deviceId: String? = null
    private var coverURL: String? = null
    private var initialFetchingDone = false
    private var seekTime: Int? = null
    private var hasNext = true
    private var hasPrev = true
    private var prevAction: String? = null
    private var isForeground = false
    private var waitForVolumeChange = false
    private var wasIdle = false
    private var prevWasInvalidDuration = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service started!")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create a MediaSessionCompat
        // MediaSession callbacks here
        val mediaSessionCallback = object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                glagolClient.send(GlagolPayload(command = "play"))
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    mediaSession.controller.playbackState.position,
                    0F
                )
                mediaSession.setPlaybackState(stateBuilder.build())
                prevAction = "pause"
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                    updateNotification()
            }

            override fun onPause() {
                glagolClient.send(GlagolPayload(command = "stop"))
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    mediaSession.controller.playbackState.position,
                    0F
                )
                mediaSession.setPlaybackState(stateBuilder.build())
                prevAction = "play"
                stopForeground(Service.STOP_FOREGROUND_DETACH)
                isForeground = false
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                    updateNotification()
            }

            override fun onStop() {
                Log.d(TAG, "mediaSession onStop callback")
                glagolClient.stop()
            }

            override fun onSkipToNext() {
                glagolClient.send(GlagolPayload(command = "next"))
            }

            override fun onSkipToPrevious() {
                glagolClient.send(GlagolPayload(command = "prev"))
            }

            override fun onSeekTo(pos: Long) {
                val intPos = (pos / 1000).toInt()
                val curState = mediaSession.controller.playbackState
                seekTime = intPos
                glagolClient.send(GlagolPayload(
                    command = "rewind",
                    position = intPos
                ))
                stateBuilder.setState(
                    curState.state,
                    pos,
                    curState.playbackSpeed
                )
                mediaSession.setPlaybackState(stateBuilder.build())
            }

            override fun onSetShuffleMode(shuffleMode: Int) {
                Log.d(TAG, "Set shuffle!")
                if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
                    sendText("играй вперемешку")
            }

            override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
                when (command) {
                    "sendCommand" -> sendText(extras!!.getString("text")!!)
                    "sendTTS" -> sendText("Повтори за мной ${extras!!.getString("text")!!}")
                    "navUp" -> navigate("up", extras?.getInt("steps"))
                    "navDown" -> navigate("down", extras?.getInt("steps"))
                    "navLeft" -> navigate("left", extras?.getInt("steps"))
                    "navRight" -> navigate("right", extras?.getInt("steps"))
                    "click" -> click()
                    "navBack" -> sendText("назад")
                    "navHome" -> sendText("домой")
                    "playTrack" -> {
                        val offset = extras?.getFloat("offset")
                        val payload = GlagolPayload(
                            command = "playMusic",
                            type = "track",
                            id = extras!!.getString("id")!!
                        )
                        if (offset != null)
                            payload.offset = offset

                        glagolClient.send(payload)
                    }
                    "playPlaylist" -> {
                        glagolClient.send(GlagolPayload(
                            command = "playMusic",
                            type = "playlist",
                            id = extras!!.getString("id")!!
                        ))
                    }
                    "playRadio" -> playRadio(extras!!.getString("id")!!)
                    "playMyVibe" -> playRadio("user:onyourwave")
                    "playFavs" -> sendText("включи мои любимые")
                    "likeTrack" -> sendText("лайк")
                }
            }

            override fun onCustomAction(action: String?, extras: Bundle?) {
                when (action) {
                    "likeTrack" -> sendText("лайк")
                    "stop" -> onStop()
                }
            }

            private fun sendText(text: String) {
                glagolClient.send(GlagolPayload(
                    command = "sendText",
                    text = text
                ))
            }

            private fun playRadio(id: String) {
                glagolClient.send(GlagolPayload(
                    command = "playMusic",
                    type = "radio",
                    id = id
                ))
            }

            // Unreleased UI navigation through Glagol
            private fun navigate(direction: String, steps: Int?) {
                val payload = GlagolPayload(
                    command = "control",
                    action = "go_$direction"
                )
                if (steps != null) {
                    payload.scrollAmount = "exact"
                    payload.scrollExactValue = steps
                }
                glagolClient.send(payload)
            }
            private fun click() {
                glagolClient.send(GlagolPayload(
                    command = "control",
                    action = "click_action"
                ))
            }
        }

        // Volume management
        volumeProvider = object : VolumeProviderCompat(
            VOLUME_CONTROL_ABSOLUTE,
            10,
            5
        ) {
            override fun onSetVolumeTo(volume: Int) {
                glagolClient.send(GlagolPayload(
                    command = "setVolume",
                    volume = volume.toFloat() / 10
                ))
                waitForVolumeChange = true
            }

            override fun onAdjustVolume(direction: Int) {
                if (direction != 0) {
                    glagolClient.send(GlagolPayload(
                        command = "setVolume",
                        volume = (currentVolume + direction).toFloat() / 10
                    ))
                    currentVolume += direction
                    waitForVolumeChange = true
                }
            }
        }

        mediaSession = MediaSessionCompat(baseContext,
            TAG
        ).apply {
            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP
                )
            addCustomActions()
            setPlaybackState(stateBuilder.build())

            mediaMetadataBuilder = MediaMetadataCompat.Builder()

            // MySessionCallback() has methods that handle callbacks from a media controller
            setCallback(mediaSessionCallback)

            // Set the session's token so that client activities can communicate with it.
            setSessionToken(sessionToken)

            // Remote playback
            setPlaybackToRemote(volumeProvider)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SERVICE START COMMAND")
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateNotification() {
        Log.d(TAG, "Update notification")
        notificationManager.notify(PLAYER_NOTIFICATION_ID, buildMediaNotification())
    }

    private fun buildMediaNotification(): Notification {
        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata.description

        val builder = NotificationCompat.Builder(this@StationControlService, PLAYER_CHANNEL_ID).apply {
            // Take advantage of MediaStyle features
            setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
            )

            // Add the metadata for the currently playing track
            setContentTitle(description.title)
            setContentText(description.subtitle)
            setSubText(description.description)
            setLargeIcon(description.iconBitmap)

            // Enable launching the player by clicking the notification
            setContentIntent(controller.sessionActivity)

            // Stop the service when the notification is swiped away
            setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this@StationControlService,
                    PlaybackStateCompat.ACTION_STOP
                )
            )

            // Make the transport controls visible on the lockscreen
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            // Add an app icon and set its accent color
            // Be careful about the color
            setSmallIcon(stationConfigs.getOrDefault(speaker.platform, fallbackConfig).icon)
            color = ContextCompat.getColor(baseContext, R.color.md_theme_dark_primary)

            // Skip prev button
            addAction(
                NotificationCompat.Action(
                    R.drawable.ic_round_skip_previous_24,
                    "Prev",
                    if (hasPrev) MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this@StationControlService,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    ) else null
                )
            )

            // Play / Pause button
            addAction(
                NotificationCompat.Action(
                    if (controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING)
                        R.drawable.ic_round_pause_24 else R.drawable.ic_round_play_arrow_24,
                    "Play/pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this@StationControlService,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )

            // Skip next button
            addAction(
                NotificationCompat.Action(
                    R.drawable.ic_round_skip_next_24,
                    "Next",
                    if (hasNext) MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this@StationControlService,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    ) else null
                )
            )

            addAction(
                NotificationCompat.Action(
                    R.drawable.round_close_24,
                    "Stop",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this@StationControlService,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
            )

            setOnlyAlertOnce(true)
            setOngoing(controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING)
        }
        return builder.build()
    }

    private fun addCustomActions() {
        stateBuilder.addCustomAction("likeTrack", "Like this track", R.drawable.round_favorite_24)
        stateBuilder.addCustomAction("stop", "Stop", R.drawable.round_close_24)
    }

    private fun startConnection() {
        glagolClient = GlagolClient(speaker)
        glagolClient.setOnSocketClosedListener {
            connCloseListener(it)
        }
        glagolClient.setOnFailureListener {
            connCloseListener(it)
        }
        glagolClient.start { observer(it) }
    }

    private fun connCloseListener(closedSpeaker: Speaker) {
        // If device ID is changed, then it's an expected device change, so keep session alive
        if (closedSpeaker.id == speaker.id) {
            // Unexpected connection failure or user decision
            stopSession()
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        val curDeviceId = rootHints!!.getString(DEVICE_ID)
        val curDeviceName = rootHints.getString(DEVICE_NAME)
        val curDevicePlatform = rootHints.getString(DEVICE_PLATFORM)
        if (curDeviceId != deviceId && curDeviceId != null) {
            Log.d(TAG, "DeviceID: $curDeviceId")
            speaker = QuasarClient.getDeviceById(curDeviceId)
                ?: return BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null)

            thread {
                if (deviceId != null && this::glagolClient.isInitialized) {
                    // Switch the device
                    Log.d(TAG, "Switching device!")
                    glagolClient.stop()
                    mediaSession.isActive = false
                    stopForeground(Service.STOP_FOREGROUND_DETACH)

                    // Reset metadata and vars
                    mediaMetadataBuilder = MediaMetadataCompat.Builder()
                    initialFetchingDone = false
                    coverURL = null
                    waitForVolumeChange = false
                    prevWasInvalidDuration = false
                    wasIdle = false
                }

                // Check if needs to register callback
                if (mDNSWorker.deviceExists(curDeviceId)) {
                    startConnection()
                } else {
                    mDNSWorker.addListener(curDeviceId) { startConnection() }
                }
            }

            // Start MediaSession and go foreground
            val sessionActivityIntent = Intent(this, DeviceActivity::class.java).apply {
                putExtra(DEVICE_ID, curDeviceId)
                putExtra(DEVICE_NAME, curDeviceName)
                putExtra(DEVICE_PLATFORM, curDevicePlatform)
            }
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                this, 0, sessionActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mediaSession.setSessionActivity(sessionActivityPendingIntent)

            mediaMetadataBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, getString(R.string.fetchingData))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, speaker.name)
            mediaSession.setMetadata(mediaMetadataBuilder.build())
            mediaSession.isActive = true
            updateNotification()

            deviceId = curDeviceId
        }

        return BrowserRoot(MY_EMPTY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        // We don't need any browsing
        result.sendResult(null)
    }

    private fun observer(data: StationState) {
        // Check idle
        val idle = data.playerState == null || data.playerState.title == ""
        if (idle) {
            if (wasIdle)  // Skip update
                return

            // Recreate builders to reset all data
            mediaMetadataBuilder = MediaMetadataCompat.Builder()
            stateBuilder = PlaybackStateCompat.Builder()

            stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP)
            addCustomActions()
            mediaMetadataBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, getString(R.string.idle))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, speaker.name)
            mediaSession.setPlaybackState(stateBuilder.build())
            mediaSession.setMetadata(mediaMetadataBuilder.build())

            hasPrev = false
            hasNext = false
            wasIdle = true
            updateNotification()
            return
        } else {
            wasIdle = false
        }

        // Avoid useless updating
        val controller = mediaSession.controller
        val state = controller.playbackState.state
        val url = data.playerState?.extra?.coverURI
        val description = controller.metadata.description
        var updateState = false
        var updateMeta = false

        // Initial fetching or seek performed
        if (!initialFetchingDone || (seekTime != null && data.playerState!!.progress.toInt() == seekTime!!)) {
            stateBuilder.setState(
                if (data.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                (data.playerState!!.progress * 1000).toLong(),
                if (data.playing) 1F else 0F
            )
            updateState = true
            initialFetchingDone = true
            seekTime = null
            prevAction = if (data.playing) "play" else "pause"
        } else if (prevAction == "play" && !data.playing) {
            stateBuilder.setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    (data.playerState!!.progress * 1000).toLong(),
                    0F
                )
            prevAction = "pause"
            updateState = true
        } else if (prevAction == "pause" && data.playing && data.playerState!!.progress > 0) { // Sometimes 0 can be received here. Station bug?
            stateBuilder.setState(
                PlaybackStateCompat.STATE_PLAYING,
                (data.playerState.progress * 1000).toLong(),
                1F
            )
            prevAction = "play"
            updateState = true
        }

        // Set currently supported actions
        stateBuilder.setActions(
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
            (if (data.playing) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY) or
            (if (data.playerState!!.hasPrev) PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS else 0) or
            (if (data.playerState.hasNext) PlaybackStateCompat.ACTION_SKIP_TO_NEXT else 0) or
            (if (data.playerState.hasProgressBar) PlaybackStateCompat.ACTION_SEEK_TO else 0)
        )

        hasPrev = data.playerState.hasPrev
        hasNext = data.playerState.hasNext

        val prevArtist = controller.metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        if (description.title != data.playerState.title || prevArtist != data.playerState.subtitle) {
            mediaMetadataBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, data.playerState.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, data.playerState.subtitle)

            // Update position
            if (state == PlaybackStateCompat.STATE_PLAYING && data.playing){
                stateBuilder.setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    (data.playerState.progress * 1000).toLong(),
                    1F
                )
                updateState = true
            }
            updateMeta = true
        }

        // Sometimes duration can be suddenly 0 while playing typical tracks. Station bug?
        if (data.playerState.duration > 0 || data.playerState.type != "Track") {
            if (updateMeta || prevWasInvalidDuration) {
                mediaMetadataBuilder.putLong(
                    MediaMetadataCompat.METADATA_KEY_DURATION,
                    (data.playerState.duration * 1000).toLong()
                )
                prevWasInvalidDuration = false
                updateMeta = true  // Force metadata update in case of previous 0 duration
            }
        } else {
            prevWasInvalidDuration = true
        }

        // Remove seek bar if needs
        if (!data.playerState.hasProgressBar)
            mediaMetadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)

        if (url != null) {
            val curImageURL =
                if (url != "") "https://" + url.removeSuffix("%%") + "800x800" else "dummy"
            if (coverURL != curImageURL) {
                mediaMetadataBuilder.putString(
                    MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                    curImageURL
                )
                if (curImageURL == "dummy") {
                    val image = ResourcesCompat.getDrawable(resources,
                        R.drawable.round_smart_display_200, null)!!.toBitmap()
                    mediaMetadataBuilder.putBitmap(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        image
                    )
                } else {
                    val res = Session.get(curImageURL)
                    if (res.ok) {
                        val image = BitmapFactory.decodeStream(res.response!!.body.byteStream())
                        mediaMetadataBuilder.putBitmap(
                            MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                            image
                        )
                        res.response.close()
                    }
                }
                coverURL = curImageURL
                updateMeta = true
            }
        }

        // Shuffling mode
        val shuffleMode = controller.shuffleMode
        when (data.playerState.entityInfo.shuffled) {
            null -> {
                if (shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_INVALID) {
                    mediaSession.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_INVALID)
                }
            }
            true -> {
                if (shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_ALL) {
                    mediaSession.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL)
                }
            }
            false -> {
                if (shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE) {
                    mediaSession.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
                }
            }
        }

        // Set current volume level
        val deviceVolume = (data.volume * 10).toInt()
        if (waitForVolumeChange) {
            if (volumeProvider.currentVolume == deviceVolume) {
                volumeProvider.currentVolume = deviceVolume
                waitForVolumeChange = false
            }
        } else if (volumeProvider.currentVolume != (data.volume * 10).toInt())
            volumeProvider.currentVolume = deviceVolume

        if (updateState) mediaSession.setPlaybackState(stateBuilder.build())
        if (updateMeta) mediaSession.setMetadata(mediaMetadataBuilder.build())

        if (updateMeta || updateState) {
            if (isForeground && !data.playing && (data.playerState.hasPlay || data.playerState.hasPause)) {
                // Do not stop foreground if track is changed
                Log.d(TAG, "Stop FG")
                stopForeground(Service.STOP_FOREGROUND_DETACH)
                isForeground = false

                updateNotification()
            } else if (!isForeground && data.playing) {
                Log.d(TAG, "Go FG")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    startForeground(PLAYER_NOTIFICATION_ID, buildMediaNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                else startForeground(PLAYER_NOTIFICATION_ID, buildMediaNotification())
                isForeground = true
            } else {
                updateNotification()
            }
        }
    }

    private fun stopSession() {
        Log.d(TAG, "stopSession() called!")
        stopSelf()
        mediaSession.isActive = false
        mediaSession.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(PLAYER_NOTIFICATION_ID)
        isForeground = false
    }

    override fun onDestroy() {
        Log.d(TAG, "Service is being destroyed")
        super.onDestroy()
    }
}
