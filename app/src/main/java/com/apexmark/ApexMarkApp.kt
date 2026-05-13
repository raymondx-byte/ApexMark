package com.apexmark

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.apexmark.service.FloatingPortalService
import com.apexmark.ui.theme.ThemePreference

class ApexMarkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePreference.init(this)
        registerActivityLifecycleCallbacks(AppForegroundTracker)
        // 进程一启动就排队启动前台 Service，比放在 MainActivity.onCreate 早数百毫秒。
        Handler(Looper.getMainLooper()).post {
            try { FloatingPortalService.start(this) } catch (_: Exception) {}
        }
    }
}

/** 追踪 app 是否有可见的 Activity；用于判断转换可否直接调用而无需启动透明 Activity。 */
object AppForegroundTracker : Application.ActivityLifecycleCallbacks {
    @Volatile var visibleActivities: Int = 0
    val isForeground: Boolean get() = visibleActivities > 0

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        if (activity !is com.apexmark.service.ClipboardConvertActivity) visibleActivities++
    }
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        if (activity !is com.apexmark.service.ClipboardConvertActivity) {
            visibleActivities = (visibleActivities - 1).coerceAtLeast(0)
        }
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
