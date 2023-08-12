package com.sanyapilot.yandexstation_controller
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
import com.sanyapilot.yandexstation_controller.api.*
import okhttp3.Request

private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
private const val PLAYER_NOTIFICATION_ID = 732

const val DEVICE_ID = "com.sanyapilot.yandexstation_controller.deviceId"
const val DEVICE_NAME = "com.sanyapilot.yandexstation_controller.deviceName"

class StationControlService : MediaBrowserServiceCompat() {
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var mediaMetadataBuilder: MediaMetadataCompat.Builder
    private lateinit var station: YandexStationService
    private lateinit var notificationManager: NotificationManager
    private lateinit var volumeProvider: VolumeProviderCompat

    private var deviceId: String? = null
    private var coverURL: String? = null
    private var initialFetchingDone = false
    private var seekTime: Int? = null
    private var hasNext = true
    private var hasPrev = true
    private lateinit var prevAction: String
    private var isForeground = false
    private var waitForVolumeChange = false
    private var wasIdle = false

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "Service started!")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create a MediaSessionCompat
        // MediaSession callbacks here
        val mediaSessionCallback = object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                station.play()
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
                station.pause()
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
                Log.d(TAG, "onStop callback")
                station.endLocal()
                stopSelf()
                mediaSession.isActive = false
                mediaSession.release()
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                isForeground = false
            }

            override fun onSkipToNext() {
                station.nextTrack()
            }

            override fun onSkipToPrevious() {
                station.prevTrack()
            }

            override fun onSeekTo(pos: Long) {
                val intPos = (pos / 1000).toInt()
                val curState = mediaSession.controller.playbackState
                seekTime = intPos
                station.seek(intPos)
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
                    station.shuffle()
            }

            override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
                when (command) {
                    "sendCommand" -> station.sendCommand(extras!!.getString("text")!!)
                    "sendTTS" -> station.sendTTS(extras!!.getString("text")!!)
                    "navUp" -> station.navUp(extras?.getInt("steps"))
                    "navDown" -> station.navDown(extras?.getInt("steps"))
                    "navLeft" -> station.navLeft(extras?.getInt("steps"))
                    "navRight" -> station.navRight(extras?.getInt("steps"))
                    "click" -> station.click()
                    "navBack" -> station.navBack()
                    "navHome" -> station.navHome()
                    "playTrack" -> station.playTrack(extras!!.getString("id")!!, extras.getFloat("offset"))
                    "playPlaylist" -> station.playPlaylist(extras!!.getString("id")!!)
                    "playRadio" -> station.playRadio(extras!!.getString("id")!!)
                    "playMyVibe" -> station.playMyVibe()
                    "playFavs" -> station.playFavs()
                    "likeTrack" -> station.likeTrack()
                }
            }

            override fun onCustomAction(action: String?, extras: Bundle?) {
                when (action) {
                    "likeTrack" -> station.likeTrack()
                    "stop" -> onStop()
                }
            }
        }

        // Volume management
        volumeProvider = object : VolumeProviderCompat(
            VOLUME_CONTROL_ABSOLUTE,
            10,
            5
        ) {
            override fun onSetVolumeTo(volume: Int) {
                station.setVolume(volume.toFloat())
                waitForVolumeChange = true
            }

            override fun onAdjustVolume(direction: Int) {
                if (direction != 0) {
                    station.setVolume((currentVolume + direction).toFloat())
                    currentVolume += direction
                    waitForVolumeChange = true
                }
            }
        }

        mediaSession = MediaSessionCompat(baseContext, TAG).apply {
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
        val curDeviceId = intent!!.getStringExtra(DEVICE_ID)
        val curDeviceName = intent.getStringExtra(DEVICE_NAME)
        if (curDeviceId != deviceId) {
            if (deviceId != null) {
                // Switch the device
                Log.d(TAG, "Switching device!")
                station.endLocal()
                mediaSession.isActive = false
                stopForeground(Service.STOP_FOREGROUND_DETACH)
            }
            Log.d(TAG, "DeviceID: $curDeviceId")
            val speaker = FuckedQuasarClient.getDeviceById(curDeviceId!!)!!

            station = YandexStationService(
                speaker = speaker,
                client = GlagolClient(speaker),
                listener = { observer(it) },
                closedListener = {
                    // Close session
                    stopSelf()
                    mediaSession.isActive = false
                    mediaSession.release()
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    isForeground = false
                }
            )

            // Start MediaSession and go foreground
            val sessionActivityIntent = Intent(this, DeviceActivity::class.java).apply {
                putExtra("deviceId", curDeviceId)
                putExtra("deviceName", curDeviceName)
            }
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                this, 0, sessionActivityIntent, PendingIntent.FLAG_IMMUTABLE
            )
            mediaSession.setSessionActivity(sessionActivityPendingIntent)

            mediaMetadataBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Fetching data...")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, station.speaker.name)
            mediaSession.setMetadata(mediaMetadataBuilder.build())
            mediaSession.isActive = true
            updateNotification()

            deviceId = curDeviceId
        }

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
            setSmallIcon(stationIcons.getOrDefault(station.speaker.platform, R.drawable.station_icon))
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

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
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
        val idle = data.playerState == null || data.playerState.title == "" || data.playerState.showPlayer == false
        if (idle) {
            if (wasIdle)  // Skip update
                return

            // Recreate builders to reset all data
            mediaMetadataBuilder = MediaMetadataCompat.Builder()
            stateBuilder = PlaybackStateCompat.Builder()

            stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_STOP)
            addCustomActions()
            mediaMetadataBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Idle")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, station.speaker.name)
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
            mediaMetadataBuilder.putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                (data.playerState.duration * 1000).toLong()
            )
        }

        // Remove seek bar if needs
        if (!data.playerState.hasProgressBar || data.playerState.showPlayer == false)
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
                    val image = ResourcesCompat.getDrawable(resources, R.drawable.round_smart_display_200, null)!!.toBitmap()
                    Log.e(TAG, "IMAGE: $image")
                    mediaMetadataBuilder.putBitmap(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        image
                    )
                } else {
                    val request = Request.Builder()
                        .url(curImageURL)
                        .build()

                    Session.client.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val image = BitmapFactory.decodeStream(resp.body.byteStream())
                            mediaMetadataBuilder.putBitmap(
                                MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                                image
                            )
                        }
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
            if (isForeground && !data.playing && description.title == data.playerState.title && prevArtist == data.playerState.subtitle) {
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
}