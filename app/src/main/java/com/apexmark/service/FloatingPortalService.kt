package com.apexmark.service

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.apexmark.AppForegroundTracker
import com.apexmark.R
import com.apexmark.engine.ConvertResult
import com.apexmark.engine.MarkdownConverter
import com.apexmark.engine.StyleStyler

object FloatingPortalServiceLocator {
    var instance: FloatingPortalService? = null
    /** 公开给 UI 观察的悬浮球可见态。 */
    val bubbleVisibleFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
}

class FloatingPortalService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleContainer: FrameLayout? = null
    private var iconView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 转换引擎按需懒构造（Flexmark 初始化约 100ms，一次性付费）。 */
    val converter: MarkdownConverter by lazy { MarkdownConverter(StyleStyler()) }

    private var screenWidth = 0
    private var isAnimating = false
    private var isScreenOn = true
    private var isDocked = true
    private var isDockedLeft = false
    private var bubbleVisible = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var touchDownTime = 0L
    private var longPressTriggered = false

    private val autoDockRunnable = Runnable { if (!isDocked && !isAnimating) snapToEdge() }
    private val resetAnimatingRunnable = Runnable { isAnimating = false }
    private val longPressRunnable = Runnable {
        if (!isDragging) {
            longPressTriggered = true
            onBubbleLongPress()
        }
    }

    companion object {
        const val CHANNEL_ID = "apexmark_convert_v3"
        const val NOTIFICATION_ID = 1001
        const val BUBBLE_SIZE_DP = 48
        private const val IDLE_ALPHA = 0.5f
        private const val DOCKED_ALPHA = 0.35f
        private const val ACTIVE_ALPHA = 0.95f
        private const val DRAG_THRESHOLD_SQ = 25f
        private const val DOCK_HIDE_FRACTION = 0.4f
        private const val AUTO_DOCK_DELAY = 4000L
        private const val ANIMATING_TIMEOUT = 3000L
        private const val LONG_PRESS_MS = 450L

        const val ACTION_SHOW_BUBBLE = "com.apexmark.SHOW_BUBBLE"
        const val ACTION_TOGGLE_BUBBLE = "com.apexmark.TOGGLE_BUBBLE"
        const val ACTION_CONVERT = "com.apexmark.CONVERT"

        fun start(context: Context) {
            val intent = Intent(context, FloatingPortalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startWithBubble(context: Context) {
            val intent = Intent(context, FloatingPortalService::class.java).apply {
                action = ACTION_SHOW_BUBBLE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun toggleBubble(context: Context) {
            val intent = Intent(context, FloatingPortalService::class.java).apply {
                action = ACTION_TOGGLE_BUBBLE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FloatingPortalServiceLocator.instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        screenWidth = resources.displayMetrics.widthPixels

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerScreenReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingPortalServiceLocator.instance = null
        mainHandler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        removeBubble()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun onScreenOff() {
        isScreenOn = false
        bubbleContainer?.visibility = View.GONE
        bubbleContainer?.animate()?.cancel()
        iconView?.animate()?.cancel()
    }

    private fun onScreenOn() {
        isScreenOn = true
        if (bubbleVisible) {
            bubbleContainer?.visibility = View.VISIBLE
            bubbleContainer?.alpha = if (isDocked) DOCKED_ALPHA else IDLE_ALPHA
        }
    }

    // region Bubble

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble() {
        if (bubbleVisible) return
        val sizePx = (BUBBLE_SIZE_DP * resources.displayMetrics.density).toInt()

        val iv = ImageView(this).apply {
            setImageResource(R.drawable.ic_apexmark_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        iconView = iv

        val container = FrameLayout(this).apply {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            addView(iv, FrameLayout.LayoutParams(sizePx, sizePx))
            alpha = DOCKED_ALPHA
        }
        bubbleContainer = container

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            sizePx, sizePx, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - sizePx + (sizePx * DOCK_HIDE_FRACTION).toInt()
            y = resources.displayMetrics.heightPixels / 3
        }
        layoutParams = lp
        isDocked = true
        isDockedLeft = false
        bubbleVisible = true

        try {
            windowManager.addView(container, lp)
            setupTouch()
        } catch (_: Exception) {
            bubbleVisible = false
        }
        FloatingPortalServiceLocator.bubbleVisibleFlow.value = bubbleVisible
    }

    private fun removeBubble() {
        try { bubbleContainer?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        bubbleContainer = null
        iconView = null
        layoutParams = null
        bubbleVisible = false
        isAnimating = false
        mainHandler.removeCallbacks(autoDockRunnable)
        mainHandler.removeCallbacks(resetAnimatingRunnable)
        mainHandler.removeCallbacks(longPressRunnable)
        FloatingPortalServiceLocator.bubbleVisibleFlow.value = false
    }

    private fun toggleBubble() {
        if (bubbleVisible) removeBubble() else createBubble()
    }

    // region Touch

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        bubbleContainer?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    longPressTriggered = false
                    touchDownTime = SystemClock.elapsedRealtime()
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    mainHandler.removeCallbacks(autoDockRunnable)
                    bubbleContainer?.animate()?.alpha(ACTIVE_ALPHA)?.setDuration(80)?.start()
                    // 仅在弹出态接受长按（贴边态首次点击只是弹出）
                    if (!isDocked) {
                        mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > DRAG_THRESHOLD_SQ) {
                        if (!isDragging) {
                            isDragging = true
                            mainHandler.removeCallbacks(longPressRunnable)
                        }
                    }
                    if (isDragging) {
                        isDocked = false
                        layoutParams?.x = initialX + dx.toInt()
                        layoutParams?.y = initialY + dy.toInt()
                        try { bubbleContainer?.let { windowManager.updateViewLayout(it, layoutParams) } } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    when {
                        longPressTriggered -> { /* 已触发长按，无需 click */ }
                        isDragging -> snapToEdge()
                        isDocked -> popOutFromEdge()
                        else -> onBubbleClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val sizePx = (BUBBLE_SIZE_DP * resources.displayMetrics.density).toInt()
        val currentX = layoutParams?.x ?: 0
        val midX = currentX + sizePx / 2
        val hideOffset = (sizePx * DOCK_HIDE_FRACTION).toInt()
        isDockedLeft = midX < screenWidth / 2
        val targetX = if (isDockedLeft) -hideOffset else screenWidth - sizePx + hideOffset

        ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.0f)
            addUpdateListener { anim ->
                layoutParams?.x = anim.animatedValue as Int
                try { bubbleContainer?.let { windowManager.updateViewLayout(it, layoutParams) } } catch (_: Exception) {}
            }
            start()
        }
        bubbleContainer?.animate()?.alpha(DOCKED_ALPHA)?.setDuration(300)?.start()
        isDocked = true
    }

    private fun popOutFromEdge() {
        val sizePx = (BUBBLE_SIZE_DP * resources.displayMetrics.density).toInt()
        val currentX = layoutParams?.x ?: 0
        val targetX = if (isDockedLeft) 0 else screenWidth - sizePx

        ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 250
            interpolator = OvershootInterpolator(1.5f)
            addUpdateListener { anim ->
                layoutParams?.x = anim.animatedValue as Int
                try { bubbleContainer?.let { windowManager.updateViewLayout(it, layoutParams) } } catch (_: Exception) {}
            }
            start()
        }
        bubbleContainer?.animate()?.alpha(IDLE_ALPHA)?.setDuration(200)?.start()
        isDocked = false
        scheduleAutoDock()
    }

    private fun scheduleAutoDock() {
        mainHandler.removeCallbacks(autoDockRunnable)
        mainHandler.postDelayed(autoDockRunnable, AUTO_DOCK_DELAY)
    }

    // region Conversion

    private fun onBubbleClick() {
        if (isAnimating) return
        isAnimating = true
        startSpinAnimation()
        launchConvertActivity(ClipboardConvertActivity.DIR_MD_TO_HTML)
        mainHandler.removeCallbacks(resetAnimatingRunnable)
        mainHandler.postDelayed(resetAnimatingRunnable, ANIMATING_TIMEOUT)
    }

    private fun onBubbleLongPress() {
        if (isAnimating) return
        isAnimating = true
        startSpinAnimation(reverse = true)
        // 触觉反馈，区别于短按
        bubbleContainer?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        launchConvertActivity(ClipboardConvertActivity.DIR_HTML_TO_MD)
        mainHandler.removeCallbacks(resetAnimatingRunnable)
        mainHandler.postDelayed(resetAnimatingRunnable, ANIMATING_TIMEOUT)
    }

    private fun launchConvertActivity(direction: String) {
        // 若 app 自身已在前台，直接调用引擎，避免任务切换的放大缩小动画。
        if (AppForegroundTracker.isForeground) {
            convertDirectly(direction)
            return
        }
        val intent = Intent(this, ClipboardConvertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtra(ClipboardConvertActivity.EXTRA_DIRECTION, direction)
        }
        startActivity(intent)
    }

    private fun convertDirectly(direction: String) {
        val result: ConvertResult = try {
            if (direction == ClipboardConvertActivity.DIR_HTML_TO_MD)
                converter.convertHtmlClipboardToMarkdown(this)
            else
                converter.convertClipboard(this)
        } catch (e: Exception) {
            ConvertResult.Error(e.message ?: "error")
        }
        val (ok, msg) = formatResult(direction, result)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        onConvertResult(ok)
    }

    private fun formatResult(direction: String, result: ConvertResult): Pair<Boolean, String> = when (result) {
        is ConvertResult.Success -> true to if (direction == ClipboardConvertActivity.DIR_HTML_TO_MD)
            getString(R.string.converted_to_markdown_with_count, result.charCount)
        else
            getString(R.string.converted_success_with_count, result.charCount)
        is ConvertResult.Empty -> false to getString(R.string.clipboard_empty)
        is ConvertResult.NotMarkdown -> false to getString(R.string.not_markdown)
        is ConvertResult.NotHtml -> false to getString(R.string.not_html)
        is ConvertResult.TooLarge -> false to getString(R.string.content_too_large, result.sizeMb)
        is ConvertResult.Error -> false to getString(R.string.convert_error, result.message.take(50))
    }

    fun onConvertResult(success: Boolean) {
        mainHandler.removeCallbacks(resetAnimatingRunnable)
        mainHandler.post {
            if (success) finishSpinWithSuccess() else finishSpinWithError()
            scheduleAutoDock()
        }
    }

    // region Animation

    private fun startSpinAnimation(reverse: Boolean = false) {
        val iv = iconView ?: return
        val bc = bubbleContainer ?: return
        if (!isScreenOn) { isAnimating = false; return }
        bc.alpha = 1f
        val from = if (reverse) 0f else 0f
        val to = if (reverse) -360f else 360f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(iv, View.ROTATION, from, to).apply {
                    duration = 500; interpolator = AccelerateDecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(iv, View.SCALE_X, 1f, 1.15f, 1f).apply { duration = 500 },
                ObjectAnimator.ofFloat(iv, View.SCALE_Y, 1f, 1.15f, 1f).apply { duration = 500 }
            )
            start()
        }
    }

    private fun finishSpinWithSuccess() {
        val bc = bubbleContainer ?: run { isAnimating = false; return }
        if (!isScreenOn) { isAnimating = false; return }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(bc, View.SCALE_X, 1f, 1.25f, 0.9f, 1.05f, 1f).apply { duration = 400; startDelay = 100 },
                ObjectAnimator.ofFloat(bc, View.SCALE_Y, 1f, 1.25f, 0.9f, 1.05f, 1f).apply { duration = 400; startDelay = 100 }
            )
            start()
        }
        mainHandler.postDelayed({
            bc.animate().alpha(IDLE_ALPHA).setDuration(600).start()
            isAnimating = false
        }, 800)
    }

    private fun finishSpinWithError() {
        val iv = iconView ?: run { isAnimating = false; return }
        val bc = bubbleContainer ?: run { isAnimating = false; return }
        if (!isScreenOn) { isAnimating = false; return }
        ObjectAnimator.ofFloat(iv, View.TRANSLATION_X,
            0f, -8f, 8f, -6f, 6f, -3f, 3f, 0f
        ).apply { duration = 400; startDelay = 100; start() }
        mainHandler.postDelayed({
            bc.animate().alpha(IDLE_ALPHA).setDuration(400).start()
            isAnimating = false
        }, 600)
    }

    // region Notification

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val mdIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, ClipboardConvertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                putExtra(ClipboardConvertActivity.EXTRA_DIRECTION, ClipboardConvertActivity.DIR_MD_TO_HTML)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val htmlIntent = PendingIntent.getActivity(
            this, 4,
            Intent(this, ClipboardConvertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                putExtra(ClipboardConvertActivity.EXTRA_DIRECTION, ClipboardConvertActivity.DIR_HTML_TO_MD)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rv = RemoteViews(packageName, R.layout.notification_convert)
        rv.setTextViewText(R.id.notification_title, getString(R.string.bubble_running))
        rv.setTextViewText(R.id.btn_convert, getString(R.string.notification_md_to_html))
        rv.setTextViewText(R.id.btn_html_to_md, getString(R.string.notification_html_to_md))
        rv.setOnClickPendingIntent(R.id.btn_convert, mdIntent)
        rv.setOnClickPendingIntent(R.id.btn_html_to_md, htmlIntent)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_BUBBLE -> if (!bubbleVisible) { createBubble() }
            ACTION_TOGGLE_BUBBLE -> toggleBubble()
        }
        return START_STICKY
    }
}
