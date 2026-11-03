package io.github.namjh654.papy.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * 지금 화면에 있는 Activity(약한 참조). Activity 가 필요한 SDK(Play 리뷰 등)를 KMP 인터페이스 뒤에서 쓸 때.
 * PapyApplication 이 한 번 등록한다.
 */
object CurrentActivity : Application.ActivityLifecycleCallbacks {
    private var ref: WeakReference<Activity>? = null

    val activity: Activity? get() = ref?.get()

    fun register(application: Application) = application.registerActivityLifecycleCallbacks(this)

    override fun onActivityResumed(activity: Activity) {
        ref = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
