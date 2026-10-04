package com.notmugil.uta.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.notmugil.uta.MainActivity
import com.notmugil.uta.R

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var subsonicRepository: com.notmugil.uta.data.SubsonicRepository
    @Inject lateinit var trackDao: com.notmugil.uta.data.db.TrackDao
    @Inject lateinit var appPreferences: com.notmugil.uta.data.preferences.AppPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private fun getPreferences(): com.notmugil.uta.data.preferences.AppPreferences {
        return if (::appPreferences.isInitialized) {
            appPreferences
        } else {
            com.notmugil.uta.data.preferences.AppPreferences(applicationContext)
        }
    }

    private val becomingNoisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (getPreferences().pauseOnDisconnect.value) {
                    timber.log.Timber.d("[PlaybackService] Audio becoming noisy -> pausing playback (pauseOnDisconnect=true)")
                    player?.pause()
                } else {
                    timber.log.Timber.d("[PlaybackService] Audio becoming noisy -> continuing playback (pauseOnDisconnect=false)")
                }
            }
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "uta_playback_channel"
        const val ACTION_TOGGLE_FAVORITE = "com.notmugil.uta.ACTION_TOGGLE_FAVORITE"
        const val ACTION_SEEK_TO = "com.notmugil.uta.ACTION_SEEK_TO"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            becomingNoisyReceiver,
            android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp3ExtractorFlags(
                androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
            )
            .setAdtsExtractorFlags(
                androidx.media3.extractor.ts.AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
            )

        val mediaCodecSelector = androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
            if (mimeType == androidx.media3.common.MimeTypes.AUDIO_OPUS) {
                decoders.sortedWith { a, b ->
                    fun score(name: String): Int {
                        return when {
                            name.contains("inproc", ignoreCase = true) -> 1
                            name.startsWith("OMX.google.", ignoreCase = true) -> 2
                            name.contains("c2.android.opus.decoder", ignoreCase = true) -> 100
                            else -> 10
                        }
                    }
                    score(a.name).compareTo(score(b.name))
                }
            } else {
                decoders
            }
        }

        val cacheDataSourceFactory = MediaCacheManager.createCacheDataSourceFactory(this)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this, extractorsFactory)
            .setDataSourceFactory(cacheDataSourceFactory)

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: androidx.media3.exoplayer.audio.AudioSink,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                out.add(
                    object : androidx.media3.exoplayer.audio.MediaCodecAudioRenderer(
                        context,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        audioSink
                    ) {
                        override fun getCodecMaxInputSize(
                            codecInfo: androidx.media3.exoplayer.mediacodec.MediaCodecInfo,
                            format: androidx.media3.common.Format,
                            streamFormats: Array<androidx.media3.common.Format>
                        ): Int {
                            val defaultSize = super.getCodecMaxInputSize(codecInfo, format, streamFormats)
                            if (defaultSize != androidx.media3.common.C.LENGTH_UNSET && defaultSize > 0) {
                                return defaultSize
                            }
                            if (format.sampleMimeType == androidx.media3.common.MimeTypes.AUDIO_OPUS ||
                                codecInfo.mimeType == androidx.media3.common.MimeTypes.AUDIO_OPUS
                            ) {
                                return 64 * 1024
                            }
                            return defaultSize
                        }

                        override fun getMediaFormat(
                            format: androidx.media3.common.Format,
                            codecMimeType: String,
                            codecMaxInputSize: Int,
                            codecOperatingRate: Float
                        ): android.media.MediaFormat {
                            val mediaFormat = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate)
                            if (codecMimeType == androidx.media3.common.MimeTypes.AUDIO_OPUS ||
                                format.sampleMimeType == androidx.media3.common.MimeTypes.AUDIO_OPUS
                            ) {
                                val maxInputSize = if (codecMaxInputSize > 0) codecMaxInputSize else 64 * 1024
                                mediaFormat.setInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize)
                            }
                            return mediaFormat
                        }
                    }
                )
            }
        }.apply {
            setMediaCodecSelector(mediaCodecSelector)
            setEnableDecoderFallback(true)
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.DEFAULT)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        exoPlayer.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                timber.log.Timber.d("[PlaybackService] Audio input format changed: container=${format.containerMimeType} sample=${format.sampleMimeType} bitrate=${format.bitrate} sampleRate=${format.sampleRate} channels=${format.channelCount}")
            }
        })

        var consecutiveErrors = 0
        var lastErrorItemId: String? = null

        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                timber.log.Timber.d("[PlaybackService] Media transition (reason=$reason): uri=${mediaItem?.localConfiguration?.uri}, title=${mediaItem?.mediaMetadata?.title}")
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    if (!getPreferences().autoNextEnabled.value && exoPlayer.repeatMode != Player.REPEAT_MODE_ONE) {
                        timber.log.Timber.d("[PlaybackService] Auto Next disabled -> pausing after track completion")
                        exoPlayer.pause()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    timber.log.Timber.d("[PlaybackService] Player READY: uri=${exoPlayer.currentMediaItem?.localConfiguration?.uri}, audioFormat=${exoPlayer.audioFormat}")
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    consecutiveErrors = 0
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    if (!getPreferences().autoResume.value) {
                        timber.log.Timber.d("[PlaybackService] Audio focus regained, but autoResume=false -> pausing")
                        exoPlayer.pause()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val itemId = exoPlayer.currentMediaItem?.mediaId
                consecutiveErrors = if (itemId == lastErrorItemId) consecutiveErrors + 1 else 1
                lastErrorItemId = itemId

                timber.log.Timber.w(
                    error,
                    "[PlaybackService] Playback error ($consecutiveErrors/3) for item $itemId: ${error.message} (errorCode=${error.errorCode}, name=${error.errorCodeName})"
                )

                val isDecoderError = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED

                try {
                    when {
                        // 1st / 2nd failure on this item: retry same item at current position
                        isDecoderError && consecutiveErrors <= 2 -> {
                            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                            timber.log.Timber.i("[PlaybackService] Retrying same track at position ${pos}ms (attempt $consecutiveErrors)")
                            exoPlayer.seekTo(exoPlayer.currentMediaItemIndex, pos)
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                        // Still failing or other error: skip to next item if available
                        consecutiveErrors <= 3 && exoPlayer.hasNextMediaItem() -> {
                            timber.log.Timber.i("[PlaybackService] Skipping to next media item after error (attempt $consecutiveErrors)")
                            exoPlayer.seekToNextMediaItem()
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                        else -> {
                            timber.log.Timber.e("[PlaybackService] Max retry limit reached ($consecutiveErrors errors), stopping playback")
                            exoPlayer.stop()
                        }
                    }
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "[PlaybackService] Error handling playback failure recovery")
                }
            }
        })

        player = exoPlayer

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleFavoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
        val seekToCommand = SessionCommand(ACTION_SEEK_TO, Bundle.EMPTY)
        @Suppress("DEPRECATION")
        val toggleFavoriteButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Favorite")
            .setIconResId(R.drawable.ic_favorite)
            .setSessionCommand(toggleFavoriteCommand)
            .build()

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(toggleFavoriteCommand)
                    .add(seekToCommand)
                    .build()

                val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_PREPARE)
                    .add(Player.COMMAND_STOP)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                    .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_BACK)
                    .add(Player.COMMAND_SEEK_FORWARD)
                    .add(Player.COMMAND_SET_SHUFFLE_MODE)
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_GET_TIMELINE)
                    .add(Player.COMMAND_GET_METADATA)
                    .add(Player.COMMAND_SET_SPEED_AND_PITCH)
                    .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                    .add(Player.COMMAND_SET_MEDIA_ITEM)
                    .addAll(session.player.availableCommands)
                    .build()

                return MediaSession.ConnectionResult.accept(
                    availableSessionCommands,
                    availablePlayerCommands
                )
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_SEEK_TO) {
                    val pos = args.getLong("position_ms", -1L)
                    val index = args.getInt("media_item_index", -1)
                    val activePlayer = player
                    if (pos >= 0L && activePlayer != null) {
                        if (index >= 0 && index < activePlayer.mediaItemCount) {
                            activePlayer.seekTo(index, pos)
                        } else {
                            activePlayer.seekTo(pos)
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == ACTION_TOGGLE_FAVORITE) {
                    val currentItem = session.player.currentMediaItem
                    val trackId = MediaItemMapper.getTrackId(currentItem)
                    val serverId = if (::subsonicRepository.isInitialized) subsonicRepository.currentServerId else ""
                    if (trackId.isNotBlank() && serverId.isNotBlank() && ::trackDao.isInitialized) {
                        serviceScope.launch {
                            try {
                                val track = trackDao.getTrack(trackId, serverId)
                                if (track != null) {
                                    val newStarred = track.starredAt == null
                                    if (newStarred) {
                                        subsonicRepository.star(trackId)
                                        trackDao.setTrackStarred(trackId, serverId, System.currentTimeMillis())
                                    } else {
                                        subsonicRepository.unstar(trackId)
                                        trackDao.setTrackStarred(trackId, serverId, null)
                                    }
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.w(e, "[PlaybackService] Failed to toggle favorite from custom command")
                            }
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .setCustomLayout(listOf(toggleFavoriteButton))
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .build()
        setMediaNotificationProvider(notificationProvider)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media Playback Controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = player
        if (activePlayer == null || !activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (_: Exception) {}
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
