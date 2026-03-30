package com.testpipreactnative

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class VideoPlayerActivity : AppCompatActivity(), Player.Listener {
  private lateinit var playerView: PlayerView
  private lateinit var controlsOverlay: View
  private lateinit var topChrome: View
  private lateinit var centerTransportOverlay: View
  private lateinit var bottomChrome: View
  private lateinit var playerTitleView: TextView
  private lateinit var playerStatusView: TextView
  private lateinit var playerPositionView: TextView
  private lateinit var playerBufferView: TextView
  private lateinit var playerDurationView: TextView
  private lateinit var playerProgressBar: DefaultTimeBar
  private lateinit var seekBackButton: Button
  private lateinit var playPauseButton: Button
  private lateinit var seekForwardButton: Button
  private lateinit var pipButton: Button
  private lateinit var closeButton: Button

  private var player: ExoPlayer? = null
  private var mediaSession: MediaSession? = null
  private var currentStream: NativeStreamDescriptor? = null
  private var shouldResumeWhenVisible: Boolean = false
  private var areControlsVisible: Boolean = true
  private var topChromeBasePaddingLeft: Int = 0
  private var topChromeBasePaddingTop: Int = 0
  private var topChromeBasePaddingRight: Int = 0
  private var topChromeBasePaddingBottom: Int = 0
  private var bottomChromeBasePaddingLeft: Int = 0
  private var bottomChromeBasePaddingTop: Int = 0
  private var bottomChromeBasePaddingRight: Int = 0
  private var bottomChromeBasePaddingBottom: Int = 0
  private val progressHandler = Handler(Looper.getMainLooper())
  private var isScrubbingProgressBar: Boolean = false
  private var scrubPositionMs: Long = 0L
  private val hideControlsRunnable = Runnable {
    hideControls()
  }
  private val progressUpdateRunnable = object : Runnable {
    override fun run() {
      updateProgressViews()
      progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    setContentView(R.layout.activity_video_player)

    bindViews()
    configureWindowInsets()
    initializePlayer()
    registerUiListeners()
    updateControlState()
    updatePictureInPictureParams()

    handleStreamIntent(intent, isNewIntent = false)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleStreamIntent(intent, isNewIntent = true)
  }

  override fun onStart() {
    super.onStart()
    startProgressUpdates()
    scheduleAutoHideControls()

    if (shouldResumeWhenVisible && !isInPictureInPictureModeCompat()) {
      player?.play()
      shouldResumeWhenVisible = false
    }
  }

  override fun onStop() {
    stopProgressUpdates()

    val activePlayer = player
    if (activePlayer != null && !isInPictureInPictureModeCompat()) {
      shouldResumeWhenVisible = activePlayer.isPlaying
      activePlayer.pause()
    }

    super.onStop()
  }

  override fun onDestroy() {
    VideoPlayerModule.emitActivityClosed(currentStream)
    stopProgressUpdates()

    playerView.player = null
    mediaSession?.release()
    mediaSession = null

    player?.removeListener(this)
    player?.release()
    player = null

    super.onDestroy()
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()

    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
      Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
      shouldAutoEnterPictureInPicture()
    ) {
      enterPictureInPictureManually()
    }
  }

  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

    if (isInPictureInPictureMode) {
      cancelAutoHideControls()
      controlsOverlay.visibility = View.GONE
    } else {
      controlsOverlay.visibility = View.VISIBLE
      showControls(animate = false)
    }

    updateControlState()
    updateProgressViews()
    VideoPlayerModule.emitPictureInPictureChange(isInPictureInPictureMode)
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    updatePlaybackChrome()
    updateProgressViews()
    refreshControlsVisibilityForPlaybackState()
    updatePictureInPictureParams()
    emitPlayerState()
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    updatePlaybackChrome()
    updateProgressViews()
    refreshControlsVisibilityForPlaybackState()
    updatePictureInPictureParams()
    emitPlayerState()
  }

  override fun onPlayerError(error: PlaybackException) {
    playerStatusView.text = "Playback error"
    updateProgressViews()
    updateControlState()
    showControls()
    VideoPlayerModule.emitError(error.localizedMessage ?: "Unknown playback error")
  }

  override fun onVideoSizeChanged(videoSize: VideoSize) {
    updatePictureInPictureParams()
  }

  private fun bindViews() {
    playerView = findViewById(R.id.player_view)
    controlsOverlay = findViewById(R.id.controls_overlay)
    topChrome = findViewById(R.id.top_chrome)
    centerTransportOverlay = findViewById(R.id.center_transport_overlay)
    bottomChrome = findViewById(R.id.bottom_chrome)
    playerTitleView = findViewById(R.id.player_title)
    playerStatusView = findViewById(R.id.player_status)
    playerPositionView = findViewById(R.id.player_position)
    playerBufferView = findViewById(R.id.player_buffer)
    playerDurationView = findViewById(R.id.player_duration)
    playerProgressBar = findViewById(R.id.player_progress_bar)
    seekBackButton = findViewById(R.id.seek_back_button)
    playPauseButton = findViewById(R.id.play_pause_button)
    seekForwardButton = findViewById(R.id.seek_forward_button)
    pipButton = findViewById(R.id.pip_button)
    closeButton = findViewById(R.id.close_button)

    topChromeBasePaddingLeft = topChrome.paddingLeft
    topChromeBasePaddingTop = topChrome.paddingTop
    topChromeBasePaddingRight = topChrome.paddingRight
    topChromeBasePaddingBottom = topChrome.paddingBottom
    bottomChromeBasePaddingLeft = bottomChrome.paddingLeft
    bottomChromeBasePaddingTop = bottomChrome.paddingTop
    bottomChromeBasePaddingRight = bottomChrome.paddingRight
    bottomChromeBasePaddingBottom = bottomChrome.paddingBottom
  }

  private fun configureWindowInsets() {
    ViewCompat.setOnApplyWindowInsetsListener(controlsOverlay) { view, insets ->
      val systemBarInsets = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
      )

      view.setPadding(systemBarInsets.left, 0, systemBarInsets.right, 0)
      topChrome.setPadding(
        topChromeBasePaddingLeft,
        topChromeBasePaddingTop + systemBarInsets.top,
        topChromeBasePaddingRight,
        topChromeBasePaddingBottom,
      )
      bottomChrome.setPadding(
        bottomChromeBasePaddingLeft,
        bottomChromeBasePaddingTop,
        bottomChromeBasePaddingRight,
        bottomChromeBasePaddingBottom + systemBarInsets.bottom,
      )

      insets
    }

    ViewCompat.requestApplyInsets(controlsOverlay)
  }

  private fun initializePlayer() {
    val newPlayer = ExoPlayer.Builder(this).build().apply {
      setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(C.USAGE_MEDIA)
          .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
          .build(),
        true,
      )
      addListener(this@VideoPlayerActivity)
    }

    player = newPlayer
    playerView.player = newPlayer
    mediaSession = MediaSession.Builder(this, newPlayer).build()
  }

  private fun registerUiListeners() {
    controlsOverlay.setOnClickListener {
      toggleControls()
    }

    seekBackButton.setOnClickListener {
      seekBy(-SEEK_INTERVAL_MS)
    }

    playPauseButton.setOnClickListener {
      val activePlayer = player ?: return@setOnClickListener
      if (activePlayer.isPlaying) {
        activePlayer.pause()
        showControls(animate = false)
      } else {
        activePlayer.play()
        scheduleAutoHideControls()
      }
      updatePlaybackChrome()
    }

    seekForwardButton.setOnClickListener {
      seekBy(SEEK_INTERVAL_MS)
    }

    pipButton.setOnClickListener {
      enterPictureInPictureManually()
    }

    closeButton.setOnClickListener {
      finish()
    }

    playerProgressBar.addListener(object : TimeBar.OnScrubListener {
      override fun onScrubStart(timeBar: TimeBar, position: Long) {
        isScrubbingProgressBar = true
        scrubPositionMs = position
        cancelAutoHideControls()
        updateProgressViews()
      }

      override fun onScrubMove(timeBar: TimeBar, position: Long) {
        scrubPositionMs = position
        updateProgressViews()
      }

      override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
        if (!canceled) {
          player?.seekTo(position)
        }

        isScrubbingProgressBar = false
        scrubPositionMs = 0L
        updateProgressViews()
        refreshControlsVisibilityForPlaybackState()
      }
    })

    playerView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
      if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
        updatePictureInPictureParams()
      }
    }
  }

  private fun handleStreamIntent(intent: Intent?, isNewIntent: Boolean) {
    val stream = VideoPlayerContract.fromIntent(intent)
    if (stream == null) {
      VideoPlayerModule.emitError("No stream configuration was provided to the native player")
      finish()
      return
    }

    currentStream = stream
    playerTitleView.text = stream.title
    prepareStream(stream)
    updateProgressViews()
    VideoPlayerModule.emitStreamChanged(stream)

    if (isNewIntent) {
      controlsOverlay.visibility = View.VISIBLE
      showControls(animate = false)
    }
  }

  private fun prepareStream(stream: NativeStreamDescriptor) {
    val activePlayer = player ?: return
    val mediaItem = buildMediaItem(stream)

    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
      .setAllowCrossProtocolRedirects(true)
      .setUserAgent("TestPipReactNative")

    if (stream.headers.isNotEmpty()) {
      httpDataSourceFactory.setDefaultRequestProperties(stream.headers)
    }

    val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
    val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

    activePlayer.setMediaSource(mediaSource)
    activePlayer.prepare()
    activePlayer.playWhenReady = true
    activePlayer.play()

    updatePlaybackChrome()
    updateProgressViews()
    showControls(animate = false)
    updatePictureInPictureParams()
  }

  private fun buildMediaItem(stream: NativeStreamDescriptor): MediaItem {
    val mediaMetadata = MediaMetadata.Builder()
      .setTitle(stream.title)
      .build()

    val builder = MediaItem.Builder()
      .setUri(stream.url)
      .setMimeType(stream.mimeType)
      .setMediaMetadata(mediaMetadata)

    val drmUuid = drmUuidFor(stream.drmScheme)
    if (drmUuid != null && !stream.drmLicenseUrl.isNullOrBlank()) {
      builder.setDrmConfiguration(
        MediaItem.DrmConfiguration.Builder(drmUuid)
          .setLicenseUri(stream.drmLicenseUrl)
          .setMultiSession(true)
          .build(),
      )
    }

    return builder.build()
  }

  private fun drmUuidFor(drmScheme: String?): UUID? {
    return when (drmScheme?.lowercase(Locale.US)) {
      "widevine" -> C.WIDEVINE_UUID
      "playready" -> C.PLAYREADY_UUID
      "clearkey" -> C.CLEARKEY_UUID
      else -> null
    }
  }

  private fun enterPictureInPictureManually() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      VideoPlayerModule.emitError("Picture-in-Picture requires Android 8 or later")
      return
    }

    if (!supportsPictureInPicture()) {
      VideoPlayerModule.emitError("Picture-in-Picture is not supported on this device")
      return
    }

    try {
      enterPictureInPictureMode(buildPictureInPictureParams())
    } catch (error: Exception) {
      VideoPlayerModule.emitError(
        error.localizedMessage ?: "Unable to enter Picture-in-Picture mode",
      )
    }
  }

  private fun updatePlaybackChrome() {
    val activePlayer = player
    if (activePlayer == null) {
      playerStatusView.text = "Preparing native player"
      playerPositionView.text = formatPlaybackTime(0L)
      playerBufferView.text = "Buffer 0%"
      playerDurationView.text = formatPlaybackTime(0L)
      playPauseButton.text = "Play"
      return
    }

    playerStatusView.text = when {
      activePlayer.playerError != null -> "Playback error"
      activePlayer.playbackState == Player.STATE_BUFFERING -> "Buffering native stream"
      activePlayer.isPlaying -> "Playing in Android Media3"
      activePlayer.playbackState == Player.STATE_READY -> "Ready in native player"
      activePlayer.playbackState == Player.STATE_ENDED -> "Playback ended"
      else -> "Idle"
    }

    playPauseButton.text = if (activePlayer.isPlaying) "Pause" else "Play"
    updateControlState()
  }

  private fun updateControlState() {
    val activePlayer = player
    val hasKnownDuration = activePlayer?.duration?.let { it != C.TIME_UNSET && it > 0L } == true
    val canSeek = activePlayer?.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) == true

    pipButton.isEnabled = supportsPictureInPicture() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    playPauseButton.isEnabled = activePlayer != null
    seekBackButton.isEnabled = canSeek
    seekForwardButton.isEnabled = canSeek
    playerProgressBar.isEnabled = canSeek && hasKnownDuration
  }

  private fun startProgressUpdates() {
    progressHandler.removeCallbacks(progressUpdateRunnable)
    progressHandler.post(progressUpdateRunnable)
  }

  private fun stopProgressUpdates() {
    progressHandler.removeCallbacks(progressUpdateRunnable)
    cancelAutoHideControls()
  }

  private fun toggleControls() {
    if (isInPictureInPictureModeCompat()) {
      return
    }

    if (areControlsVisible) {
      hideControls()
    } else {
      showControls()
    }
  }

  private fun showControls(animate: Boolean = true) {
    if (isInPictureInPictureModeCompat()) {
      return
    }

    controlsOverlay.visibility = View.VISIBLE
    areControlsVisible = true
    updateChromeVisibility(visible = true, animate = animate)
    scheduleAutoHideControls()
  }

  private fun hideControls(animate: Boolean = true) {
    if (isInPictureInPictureModeCompat() || isScrubbingProgressBar) {
      return
    }

    cancelAutoHideControls()
    areControlsVisible = false
    updateChromeVisibility(visible = false, animate = animate)
  }

  private fun updateChromeVisibility(visible: Boolean, animate: Boolean) {
    val chromeViews = listOf(topChrome, centerTransportOverlay, bottomChrome)

    chromeViews.forEach { chromeView ->
      chromeView.animate().cancel()

      if (visible) {
        if (chromeView.visibility != View.VISIBLE) {
          chromeView.alpha = if (animate) 0f else 1f
          chromeView.visibility = View.VISIBLE
        }

        if (animate) {
          chromeView.animate()
            .alpha(1f)
            .setDuration(CONTROLS_FADE_DURATION_MS)
            .start()
        } else {
          chromeView.alpha = 1f
        }
      } else {
        if (animate && chromeView.visibility == View.VISIBLE) {
          chromeView.animate()
            .alpha(0f)
            .setDuration(CONTROLS_FADE_DURATION_MS)
            .withEndAction {
              if (!areControlsVisible) {
                chromeView.visibility = View.GONE
              }
            }
            .start()
        } else {
          chromeView.alpha = 0f
          chromeView.visibility = View.GONE
        }
      }
    }
  }

  private fun refreshControlsVisibilityForPlaybackState() {
    if (isInPictureInPictureModeCompat()) {
      return
    }

    val activePlayer = player
    val shouldKeepControlsVisible =
      activePlayer == null ||
      activePlayer.playerError != null ||
      activePlayer.playbackState == Player.STATE_ENDED ||
      (!activePlayer.isPlaying && activePlayer.playbackState != Player.STATE_BUFFERING)

    if (shouldKeepControlsVisible) {
      showControls()
    } else if (areControlsVisible) {
      scheduleAutoHideControls()
    }
  }

  private fun scheduleAutoHideControls() {
    cancelAutoHideControls()

    val activePlayer = player ?: return
    if (!areControlsVisible || isScrubbingProgressBar || isInPictureInPictureModeCompat()) {
      return
    }

    if (
      activePlayer.isPlaying &&
      activePlayer.playbackState == Player.STATE_READY &&
      activePlayer.playerError == null
    ) {
      progressHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_DELAY_MS)
    }
  }

  private fun cancelAutoHideControls() {
    progressHandler.removeCallbacks(hideControlsRunnable)
  }

  private fun updateProgressViews() {
    val activePlayer = player
    if (activePlayer == null) {
      playerProgressBar.setDuration(0L)
      playerProgressBar.setBufferedPosition(0L)
      playerProgressBar.setPosition(0L)
      return
    }

    val durationMs = activePlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
    val currentPositionMs = if (isScrubbingProgressBar) scrubPositionMs else activePlayer.currentPosition
    val safePositionMs = currentPositionMs.coerceAtLeast(0L)
    val bufferedPositionMs = activePlayer.bufferedPosition.coerceAtLeast(safePositionMs)

    playerPositionView.text = formatPlaybackTime(safePositionMs)
    playerDurationView.text = if (durationMs > 0L) {
      formatPlaybackTime(durationMs)
    } else {
      "Live"
    }

    playerBufferView.text = if (durationMs > 0L) {
      val clampedBufferedMs = bufferedPositionMs.coerceAtMost(durationMs)
      val bufferedPercent = ((clampedBufferedMs * 100f) / durationMs).roundToInt().coerceIn(0, 100)
      "Buffer $bufferedPercent%"
    } else {
      when (activePlayer.playbackState) {
        Player.STATE_BUFFERING -> "Buffering"
        Player.STATE_READY -> "Streaming"
        else -> "Waiting"
      }
    }

    playerProgressBar.setDuration(durationMs)
    playerProgressBar.setBufferedPosition(
      if (durationMs > 0L) bufferedPositionMs.coerceAtMost(durationMs) else 0L,
    )
    playerProgressBar.setPosition(
      if (durationMs > 0L) safePositionMs.coerceAtMost(durationMs) else 0L,
    )
    updateControlState()
  }

  private fun seekBy(offsetMs: Long) {
    val activePlayer = player ?: return
    val durationMs = activePlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
    val targetPositionMs = (activePlayer.currentPosition + offsetMs).coerceAtLeast(0L)
    val clampedTargetPositionMs = if (durationMs != null) {
      targetPositionMs.coerceAtMost(durationMs)
    } else {
      targetPositionMs
    }

    activePlayer.seekTo(clampedTargetPositionMs)
    updateProgressViews()
    scheduleAutoHideControls()
  }

  private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
      String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
  }

  private fun supportsPictureInPicture(): Boolean {
    return packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
  }

  private fun shouldAutoEnterPictureInPicture(): Boolean {
    val activePlayer = player ?: return false
    return activePlayer.playbackState == Player.STATE_READY && activePlayer.isPlaying
  }

  private fun buildPictureInPictureParams(): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
      .setAspectRatio(calculatePictureInPictureAspectRatio())

    val sourceRect = Rect()
    if (playerView.getGlobalVisibleRect(sourceRect)) {
      builder.setSourceRectHint(sourceRect)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      builder.setAutoEnterEnabled(shouldAutoEnterPictureInPicture())
      builder.setSeamlessResizeEnabled(true)
    }

    return builder.build()
  }

  private fun updatePictureInPictureParams() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !supportsPictureInPicture()) {
      return
    }

    try {
      setPictureInPictureParams(buildPictureInPictureParams())
    } catch (_: Exception) {
    }
  }

  private fun calculatePictureInPictureAspectRatio(): Rational {
    val activePlayer = player
    val videoSize = activePlayer?.videoSize

    val ratio = when {
      videoSize != null && videoSize.width > 0 && videoSize.height > 0 -> {
        var width = videoSize.width.toFloat()
        var height = videoSize.height.toFloat()

        if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
          val previousWidth = width
          width = height
          height = previousWidth
        }

        (width * videoSize.pixelWidthHeightRatio) / height
      }
      playerView.width > 0 && playerView.height > 0 -> {
        playerView.width.toFloat() / playerView.height.toFloat()
      }
      else -> 16f / 9f
    }

    val clampedRatio = ratio.coerceIn(MIN_PIP_RATIO, MAX_PIP_RATIO)
    val denominator = 1000
    val numerator = (clampedRatio * denominator).roundToInt().coerceAtLeast(1)

    return Rational(numerator, denominator)
  }

  private fun emitPlayerState() {
    VideoPlayerModule.emitPlayerState(
      state = currentStateLabel(),
      isPlaying = player?.isPlaying == true,
      stream = currentStream,
    )
  }

  private fun currentStateLabel(): String {
    val activePlayer = player ?: return "idle"

    return when {
      activePlayer.playerError != null -> "error"
      activePlayer.playbackState == Player.STATE_BUFFERING -> "buffering"
      activePlayer.isPlaying -> "playing"
      activePlayer.playbackState == Player.STATE_READY -> "ready"
      activePlayer.playbackState == Player.STATE_ENDED -> "ended"
      else -> "idle"
    }
  }

  private fun isInPictureInPictureModeCompat(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
  }

  companion object {
    private const val MIN_PIP_RATIO = 100f / 239f
    private const val MAX_PIP_RATIO = 239f / 100f
    private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    private const val SEEK_INTERVAL_MS = 10_000L
    private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3_000L
    private const val CONTROLS_FADE_DURATION_MS = 180L
  }
}