package com.sanyapilot.yandexstation_controller
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.VolumeProviderCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.sanyapilot.yandexstation_controller.api.*
import okhttp3.Request

private const val MY_MEDIA_ROOT_ID = "media_root_id"
private const val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
private const val PLAYER_NOTIFICATION_ID = 732

const val DEVICE_ID = "com.sanyapilot.yandexstation_controller.deviceId"

class StationControlService : MediaBrowserServiceCompat() {
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var mediaMetadataBuilder: MediaMetadataCompat.Builder
    private lateinit var station: YandexStationService
    private lateinit var notificationManager: NotificationManager
    private lateinit var volumeProvider: VolumeProviderCompat

    private var coverURL: String? = null
    private var initialFetchingDone = false
    private var seekTime: Int? = null
    private var hasNext = true
    private var hasPrev = true
    private lateinit var prevAction: String

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
                    1F
                )
                mediaSession.setPlaybackState(stateBuilder.build())
                prevAction = "pause"
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
            }

            override fun onStop() {
                Log.e(TAG, "onStop")
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
        }

        // Volume management
        volumeProvider = object : VolumeProviderCompat(
            VOLUME_CONTROL_ABSOLUTE,
            10,
            5
        ) {
            override fun onSetVolumeTo(volume: Int) {
                station.setVolume(volume.toFloat())
            }

            override fun onAdjustVolume(direction: Int) {
                if (direction != 0)
                    station.setVolume((currentVolume + direction).toFloat())
            }
        }

        mediaSession = MediaSessionCompat(baseContext, TAG).apply {
            // Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY)
            setPlaybackState(stateBuilder.build())

            mediaMetadataBuilder = MediaMetadataCompat.Builder()
            //    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Test artist")
            //    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Testi title")

            // MySessionCallback() has methods that handle callbacks from a media controller
            setCallback(mediaSessionCallback)
            setMetadata(mediaMetadataBuilder.build())

            // Set the session's token so that client activities can communicate with it.
            setSessionToken(sessionToken)

            // Remote playback
            setPlaybackToRemote(volumeProvider)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceId = intent!!.getStringExtra(DEVICE_ID)
        Log.d(TAG, "DeviceID: $deviceId")
        val speaker = FuckedQuasarClient.getDeviceById(deviceId!!)!!
        Log.e(TAG, speaker.id)

        station = YandexStationService(
            speaker = speaker,
            client = GlagolClient(speaker)
        ) { observer(it) }

        // Start MediaSession and go foreground
        mediaMetadataBuilder
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Fetching data...")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, station.speaker.name)
        mediaSession.setMetadata(mediaMetadataBuilder.build())
        mediaSession.isActive = true

        startForeground(PLAYER_NOTIFICATION_ID, buildMediaNotification())

        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateNotification() {
        notificationManager.notify(PLAYER_NOTIFICATION_ID, buildMediaNotification())
    }

    private fun buildMediaNotification(): Notification {
        val controller = mediaSession.controller
        val mediaMetadata = controller.metadata
        val description = mediaMetadata.description

        val builder = NotificationCompat.Builder(this@StationControlService, PLAYER_CHANNEL_ID).apply {
            // Add the metadata for the currently playing track
            setContentTitle(description.title)
            setContentText(description.subtitle)
            setSubText(description.description)
            setLargeIcon(description.iconBitmap)

            // Enable launching the player by clicking the notification
            setContentIntent(controller.sessionActivity)

            setOnlyAlertOnce(true)
            setOngoing(true)

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
            setSmallIcon(R.drawable.ic_round_play_arrow_24)
            //color = ContextCompat.getColor(baseContext, R.color.primaryDark)

            // Skip prev button
            addAction(
                NotificationCompat.Action(
                    R.drawable.ic_round_skip_previous_24,
                    "Next",
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
                        R.drawable.ic_round_pause_24
                    else R.drawable.ic_round_play_arrow_24,
                    "PAUSE",
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

            // Take advantage of MediaStyle features
            setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)

                // Add a cancel button
                .setShowCancelButton(true)
                .setCancelButtonIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this@StationControlService,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
            )
        }
        return builder.build()
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
        val active = data.playerState != null && data.playerState.title != ""
        if (!active) {
            stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY)
            mediaMetadataBuilder
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Idle")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, station.speaker.name)
            mediaSession.setPlaybackState(stateBuilder.build())
            mediaSession.setMetadata(mediaMetadataBuilder.build())

            hasPrev = false
            hasNext = false
            updateNotification()
            return
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
                (data.playerState!!.progress * 1000).toLong(),
                1F
            )
            prevAction = "play"
            updateState = true
        }

        // Set currently supported actions
        stateBuilder.setActions(
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            (if (data.playerState!!.hasPrev) PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS else 0) or
            (if (data.playerState.hasNext) PlaybackStateCompat.ACTION_SKIP_TO_NEXT else 0) or
            (if (data.playerState.hasProgressBar) PlaybackStateCompat.ACTION_SEEK_TO else 0)
        )

        hasPrev = data.playerState.hasPrev
        hasNext = data.playerState.hasNext

        if (description.title != data.playerState.title || description.subtitle != data.playerState.subtitle) {
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
                val request = Request.Builder()
                    .url(curImageURL)
                    .build()

                Session.client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val image = BitmapFactory.decodeStream(resp.body!!.byteStream())
                        mediaMetadataBuilder.putBitmap(
                            MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                            image
                        )
                    }
                }
                coverURL = curImageURL
                updateMeta = true
            }
        }

        // Set current volume level
        if (volumeProvider.currentVolume != (data.volume * 10).toInt())
            volumeProvider.currentVolume = (data.volume * 10).toInt()

        if (updateState) mediaSession.setPlaybackState(stateBuilder.build())
        if (updateMeta) mediaSession.setMetadata(mediaMetadataBuilder.build())

        updateNotification()
    }
}