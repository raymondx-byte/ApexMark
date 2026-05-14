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
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.apexmark.AppForegroundTracker
import com.apexmark.MainActivity
import com.apexmark.R
import com.apexmark.engine.ClipboardClipKind
import com.apexmark.engine.ConvertActions
import com.apexmark.engine.ConvertResult
import com.apexmark.engine.ConvertUiFeedback
import com.apexmark.engine.MarkdownConverter
import com.apexmark.ui.ConvertMenuUi

object FloatingPortalServiceLocator {
    var instance: FloatingPortalService? = null
    val bubbleVisibleFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** 与主界面 Compose 对齐：在打开 App / 手势刷新通知时递增。 */
    val clipboardUiEpoch = kotlinx.coroutines.flow.MutableStateFlow(0L)

    private val mainHandler = Handler(Looper.getMainLooper())

    fun notifyConvertResult(success: Boolean) {
        instance?.onConvertResult(success)
    }

    fun requestNotificationUpdate() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyClipboardUiEpochAndRefresh()
        } else {
            mainHandler.post { applyClipboardUiEpochAndRefresh() }
        }
    }

    private fun applyClipboardUiEpochAndRefresh() {
        clipboardUiEpoch.value = clipboardUiEpoch.value + 1L
        instance?.scheduleNotificationRefresh()
    }
}

class FloatingPortalService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleContainer: FrameLayout? = null
    private var iconView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    val converter: MarkdownConverter by lazy { MarkdownConverter() }

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
    private var longPressTriggered = false

    private val autoDockRunnable = Runnable { if (!isDocked && !isAnimating) snapToEdge() }
    private val resetAnimatingRunnable = Runnable {
        isAnimating = false
        iconView?.rotation = 0f
    }
    private val longPressRunnable = Runnable {
        if (!isDragging) {
            longPressTriggered = true
            onBubbleLongPress()
        }
    }
    /** 剪贴板变化等高频事件合并为一次刷新，减轻 NotificationManager / Binder 压力。 */
    private val clipRefreshDebounced = Runnable { applyForegroundNotificationUpdate() }

    private var clipboardManager: ClipboardManager? = null
    private val primaryClipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        MarkdownConverter.discardPendingPeekAfterClipboardChanged()
        scheduleNotificationRefresh()
    }

    private fun applyForegroundNotificationUpdate() {
        try {
            val notification = buildNotification()
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    fun scheduleNotificationRefresh() {
        mainHandler.removeCallbacks(clipRefreshDebounced)
        mainHandler.postDelayed(clipRefreshDebounced, 180L)
    }

    private fun refreshNotificationFromUserGesture() {
        try {
            MarkdownConverter.discardPendingPeekAfterClipboardChanged()
            applyForegroundNotificationUpdate()
        } catch (_: Exception) {
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
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(primaryClipChangedListener)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerScreenReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        FloatingPortalServiceLocator.instance = null
        mainHandler.removeCallbacksAndMessages(null)
        try {
            clipboardManager?.removePrimaryClipChangedListener(primaryClipChangedListener)
        } catch (_: Exception) {
        }
        clipboardManager = null
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
                    refreshNotificationFromUserGesture()
                    isDragging = false
                    longPressTriggered = false
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    mainHandler.removeCallbacks(autoDockRunnable)
                    bubbleContainer?.animate()?.alpha(ACTIVE_ALPHA)?.setDuration(80)?.start()
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
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
                        longPressTriggered -> Unit
                        isDragging -> snapToEdge()
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

    private fun scheduleAutoDock() {
        mainHandler.removeCallbacks(autoDockRunnable)
        mainHandler.postDelayed(autoDockRunnable, AUTO_DOCK_DELAY)
    }

    // region Conversion

    private fun labelForConvertAction(action: String, plainTidyLabels: Boolean = false): String {
        if (plainTidyLabels) return getString(R.string.notif_plain_tidy_blanks)
        return when (action) {
            ConvertActions.MD_TO_WPS -> getString(R.string.notif_md_wps)
            ConvertActions.MD_TO_HTML_EMAIL -> getString(R.string.notif_md_html)
            ConvertActions.HTML_TO_WPS -> getString(R.string.notif_html_wps)
            ConvertActions.HTML_OR_TEXT_TO_MD -> getString(R.string.notif_html_text_md)
            ConvertActions.WPS_OR_TEXT_TO_MD -> getString(R.string.notif_wps_text_md)
            ConvertActions.WPS_TO_MD -> getString(R.string.notif_wps_md)
            ConvertActions.CLIPBOARD_TO_HTML_EMAIL -> getString(R.string.notif_clipboard_to_html_email)
            else -> getString(R.string.notif_md_wps)
        }
    }

    private fun menuDp(px: Int): Int = (px * resources.displayMetrics.density + 0.5f).toInt()

    @SuppressLint("SetTextI18n")
    private fun onBubbleClick() {
        if (isAnimating) return
        refreshNotificationFromUserGesture()
        startClipboardPeekForBubble()
    }

    private fun startClipboardPeekForBubble() {
        try {
            startActivity(
                Intent(this, ClipboardPeekActivity::class.java).apply {
                    putExtra(ClipboardPeekActivity.EXTRA_PEEK_TARGET, ClipboardPeekActivity.PEEK_BUBBLE)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_NO_HISTORY
                    )
                }
            )
        } catch (_: Exception) {
        }
    }

    /** 由 [ClipboardPeekActivity] 在焦点判型后调用；须同步执行，否则 Activity 立即 finish 会导致 PopupWindow 无法附着。 */
    @SuppressLint("SetTextI18n")
    internal fun showBubbleConvertMenu(kind: ClipboardClipKind) {
        if (!bubbleVisible || isAnimating) return
        if (kind == ClipboardClipKind.IMAGE) {
            ConvertUiFeedback.showCenteredToast(this, getString(R.string.clipboard_image_unsupported))
            return
        }
        val anchor = bubbleContainer ?: return
        val (primary, secondary) = ConvertActions.primarySecondaryForKind(kind)
        val plainTidy = kind == ClipboardClipKind.PLAIN

        val btnWidth = menuDp(268)
        val pillRadius = menuDp(999).toFloat()
        val strokeW = menuDp(2)
        val primaryBg = ConvertMenuUi.primaryPill(pillRadius)
        val secondaryBg = ConvertMenuUi.secondaryPill(pillRadius, strokeW)

        lateinit var pw: PopupWindow
        val tvPrimary = TextView(this).apply {
            text = labelForConvertAction(primary, plainTidy)
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(menuDp(20), menuDp(14), menuDp(20), menuDp(14))
            background = primaryBg
            setOnClickListener {
                pw.dismiss()
                startConvertFromBubble(primary)
            }
        }
        val tvSecondary = TextView(this).apply {
            text = labelForConvertAction(secondary, plainTidy)
            setTextColor(Color.parseColor("#0050B0"))
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(menuDp(20), menuDp(14), menuDp(20), menuDp(14))
            background = secondaryBg
            setOnClickListener {
                pw.dismiss()
                startConvertFromBubble(secondary)
            }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = menuDp(18)
            setPadding(pad, pad, pad, pad)
            background = ConvertMenuUi.panelCard(menuDp(28).toFloat(), menuDp(1))
            elevation = menuDp(6).toFloat()
            val lp1 = LinearLayout.LayoutParams(btnWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp1.bottomMargin = menuDp(12)
            addView(tvPrimary, lp1)
            addView(tvSecondary, LinearLayout.LayoutParams(btnWidth, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        pw = PopupWindow(
            panel,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = menuDp(10).toFloat()
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }

        panel.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val mw = panel.measuredWidth
        val mh = panel.measuredHeight
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val dm = resources.displayMetrics
        val xOff = (dm.widthPixels - mw) / 2 - loc[0]
        val yOff = (dm.heightPixels - mh) / 2 - loc[1]
        pw.showAtLocation(anchor, Gravity.TOP or Gravity.START, xOff, yOff)
    }

    private fun startConvertFromBubble(action: String) {
        if (isAnimating) return
        isAnimating = true
        startSpinAnimation()
        launchConvertActivity(action)
        mainHandler.removeCallbacks(resetAnimatingRunnable)
        mainHandler.postDelayed(resetAnimatingRunnable, ANIMATING_TIMEOUT)
    }

    private fun onBubbleLongPress() {
        bubbleContainer?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    private fun launchConvertActivity(action: String) {
        if (AppForegroundTracker.isForeground) {
            convertDirectly(action)
            return
        }
        val intent = Intent(this, ClipboardConvertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtra(ConvertActions.EXTRA, action)
        }
        startActivity(intent)
    }

    private fun convertDirectly(action: String) {
        val result: ConvertResult = try {
            converter.convertForAction(this, action)
        } catch (e: Exception) {
            ConvertResult.Error(e.message ?: "error")
        }
        val (ok, msg) = ConvertUiFeedback.toastMessage(this, action, result)
        ConvertUiFeedback.showCenteredToast(this, msg)
        onConvertResult(ok)
        FloatingPortalServiceLocator.requestNotificationUpdate()
    }

    /** 通知二级菜单等：前台时直接转换（与 [convertDirectly] 相同）。 */
    fun performConvertDirectly(action: String) = convertDirectly(action)

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

    private fun notificationMenuPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 905,
            Intent(this, NotificationMenuActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                )
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun buildNotification(): Notification {
        val menuPi = notificationMenuPendingIntent()
        val rv = RemoteViews(packageName, R.layout.notification_convert)
        rv.setTextViewText(R.id.notif_tap_convert, getString(R.string.notif_tap_convert_hint))
        rv.setOnClickPendingIntent(R.id.notif_tap_convert, menuPi)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_stat_silent)
            .setShowWhen(false)
            .setCustomContentView(rv)
            .setCustomBigContentView(rv)
            .setContentIntent(menuPi)
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
        FloatingPortalServiceLocator.requestNotificationUpdate()
        return START_STICKY
    }
}
