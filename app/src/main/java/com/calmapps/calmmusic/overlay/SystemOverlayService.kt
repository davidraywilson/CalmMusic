package com.calmapps.calmmusic.overlay

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.calmapps.calmmusic.CalmMusic
import com.calmapps.calmmusic.MainActivity
import com.calmapps.calmmusic.PlaybackService
import com.calmapps.calmmusic.data.PlaybackStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SystemOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var closeTargetView: ComposeView? = null
    private var closeTargetLayoutParams: WindowManager.LayoutParams? = null
    private var originalX: Int = 0
    private var originalY: Int = 0
    private lateinit var playbackStateManager: PlaybackStateManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        playbackStateManager = (application as CalmMusic).playbackStateManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            showOverlay()
        }
        return START_STICKY
    }

    private fun showOverlay() {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

            val density = resources.displayMetrics.density
            val marginDp = 0
            val marginPx = (marginDp * density).toInt()
            val statusBarResId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeightPx = if (statusBarResId > 0) {
                resources.getDimensionPixelSize(statusBarResId)
            } else {
                (24 * density).toInt() // reasonable fallback
            }
            // Place the pill just below the status bar
            y = statusBarHeightPx + marginPx

            // Remember the "home" position so we can snap back after drag
            originalX = 0
            originalY = y

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        val view = ComposeView(this).apply {
            val lifecycleOwner = MyLifecycleOwner()
            lifecycleOwner.performRestore(null)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            })

            setContent {
                OverlayContent()
            }
        }

        overlayLayoutParams = layoutParams

        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    @Composable
    private fun OverlayContent() {
        val state by playbackStateManager.state.collectAsState()

        // Only show if a song is loaded AND the app is in the background
        val showOverlay = state.songId != null && !state.isAppInForeground

        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { showCloseTarget() },
                            onDragEnd = { handleDragEndOrCancel() },
                            onDragCancel = { handleDragEndOrCancel() }
                        ) { change, dragAmount ->
                            change.consumeAllChanges()
                            moveOverlayBy(dragAmount.x, dragAmount.y)
                        }
                    }
                    .background(Color.Black)
                    .clickable {
                        val intent = Intent(this@SystemOverlayService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)

                        dismissOverlay()
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = state.title.ifBlank{ "Not Playing" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(100.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // Ignore
            }
        }
        overlayView = null
        overlayLayoutParams = null
        hideCloseTarget()
    }

    private fun moveOverlayBy(dx: Float, dy: Float) {
        val view = overlayView ?: return
        val params = overlayLayoutParams ?: return
        params.x += dx.toInt()
        params.y += dy.toInt()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
            // Ignore, view might have been removed
        }
    }

    private fun resetOverlayPosition() {
        val view = overlayView ?: return
        val params = overlayLayoutParams ?: return
        params.x = originalX
        params.y = originalY
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
            // Ignore, view might have been removed
        }
    }

    private fun handleDragEndOrCancel() {
        val params = overlayLayoutParams
        if (params == null) {
            hideCloseTarget()
            return
        }

        val metrics = resources.displayMetrics
        val screenHeight = metrics.heightPixels
        val density = metrics.density
        val closeZoneHeightPx = (120 * density).toInt()
        val thresholdY = screenHeight - closeZoneHeightPx

        val shouldClose = params.y >= thresholdY
        if (shouldClose) {
            // Dragged into the close zone: stop playback and dismiss overlay
            stopPlayback()
            dismissOverlay()
        } else {
            // Not in close zone: snap back and hide the X
            resetOverlayPosition()
            hideCloseTarget()
        }
    }

    private fun showCloseTarget() {
        if (closeTargetView != null) return

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val sizeDp = 64
        val sizePx = (sizeDp * density).toInt()
        val bottomMarginDp = 32
        val bottomMarginPx = (bottomMarginDp * density).toInt()

        val layoutParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = bottomMarginPx
        }

        val view = ComposeView(this).apply {
            val lifecycleOwner = MyLifecycleOwner()
            lifecycleOwner.performRestore(null)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            })

            setContent {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        try {
            windowManager.addView(view, layoutParams)
            closeTargetView = view
            closeTargetLayoutParams = layoutParams
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideCloseTarget() {
        closeTargetView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // Ignore
            }
        }
        closeTargetView = null
        closeTargetLayoutParams = null
    }

    private fun dismissOverlay() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // Ignore
            }
        }
        overlayView = null
        overlayLayoutParams = null
        hideCloseTarget()
        stopSelf()
    }

    private fun stopPlayback() {
        val app = application as? CalmMusic

        // Stop Apple Music playback if active
        try {
            app?.appleMusicPlayer?.pause()
        } catch (_: Exception) {
            // Ignore
        }

        // Stop local playback via MediaController bound to PlaybackService's MediaSession
        try {
            val context = this
            val sessionToken = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            future.addListener({
                try {
                    val controller = future.get()
                    controller.pause()
                    // Do NOT call controller.release() here; it can throw if the
                    // service connection is already unbound. The controller will
                    // be cleaned up when the process dies or GC runs.
                } catch (_: Exception) {
                    // Ignore
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (_: Exception) {
            // Ignore
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
        fun performRestore(savedState: android.os.Bundle?) = savedStateRegistryController.performRestore(savedState)
    }
}