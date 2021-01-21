package com.wzf.daemon

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.annotation.Nullable
import java.util.*

object DaemonEnv {
    const val DEFAULT_WAKE_UP_INTERVAL = 6 * 60 * 1000
    private const val MINIMAL_WAKE_UP_INTERVAL = 3 * 60 * 1000
    var sApp: Context? = null
    var sServiceClass: Class<out AbsWorkService?>? = null
    private var sWakeUpInterval = DEFAULT_WAKE_UP_INTERVAL
    var sInitialized = false
    val BIND_STATE_MAP: MutableMap<Class<out Service?>, ServiceConnection> =
        HashMap<Class<out Service?>, ServiceConnection>()

    /**
     * @param app            Application Context.
     * @param wakeUpInterval 定时唤醒的时间间隔(ms).
     */
    fun initialize(
        app: Context,
        serviceClass: Class<out AbsWorkService?>,
        @Nullable wakeUpInterval: Int
    ) {
        sApp = app
        sServiceClass = serviceClass
        if (wakeUpInterval != null) sWakeUpInterval = wakeUpInterval
        sInitialized = true
    }

    fun startServiceMayBind(serviceClass: Class<out Service?>) {
        if (!sInitialized) return
        val i = Intent(sApp, serviceClass)
        startServiceSafely(i)
        val bound: ServiceConnection? = BIND_STATE_MAP[serviceClass]
        if (bound == null) sApp!!.bindService(i, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                BIND_STATE_MAP[serviceClass] = this
            }

            override fun onServiceDisconnected(name: ComponentName) {
                BIND_STATE_MAP.remove(serviceClass)
                startServiceSafely(i)
                if (!sInitialized) return
                sApp!!.bindService(i, this, Context.BIND_AUTO_CREATE)
            }

            override fun onBindingDied(name: ComponentName) {
                onServiceDisconnected(name)
            }
        }, Context.BIND_AUTO_CREATE)
    }

    fun startServiceSafely(i: Intent?) {
        if (!sInitialized) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sApp!!.startForegroundService(i)
            } else {
                sApp!!.startService(i)
            }
        } catch (ignored: Exception) {
        }
    }

    val wakeUpInterval: Int
        get() = Math.max(sWakeUpInterval, MINIMAL_WAKE_UP_INTERVAL)
}