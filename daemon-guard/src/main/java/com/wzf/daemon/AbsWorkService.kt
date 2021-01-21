package com.wzf.daemon

import android.content.Intent
import android.os.IBinder
import android.annotation.TargetApi
import android.app.Notification
import android.os.Build
import com.wzf.daemon.DaemonEnv
import com.wzf.daemon.WatchGuardService
import com.wzf.daemon.AbsWorkService.WorkNotificationService
import android.content.ComponentName
import android.content.pm.PackageManager
import com.wzf.daemon.AbsWorkService
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.Service
import android.content.Context
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import com.wzf.daemon.WakeUpReceiver

abstract class AbsWorkService : Service() {
    protected var mFirstStarted = true

    /**
     * 是否 任务完成, 不再需要服务运行?
     *
     * @return 应当停止服务, true; 应当启动服务, false; 无法判断, 什么也不做, null.
     */
    abstract fun shouldStopService(intent: Intent?, flags: Int, startId: Int): Boolean?
    abstract fun startWork(intent: Intent?, flags: Int, startId: Int)
    abstract fun stopWork(intent: Intent?, flags: Int, startId: Int)

    /**
     * 任务是否正在运行?
     *
     * @return 任务正在运行, true; 任务当前不在运行, false; 无法判断, 什么也不做, null.
     */
    abstract fun isWorkRunning(intent: Intent?, flags: Int, startId: Int): Boolean?
    @Nullable
    abstract fun onBind(intent: Intent?, alwaysNull: Void?): IBinder?
    abstract fun onServiceKilled(rootIntent: Intent?)
    override fun onCreate() {
        super.onCreate()
        initForEachStartService()
    }

    /**
     * 1.防止重复启动，可以任意调用 DaemonEnv.startServiceMayBind(Class serviceClass);
     * 2.利用漏洞启动前台服务而不显示通知;
     * 3.在子线程中运行定时任务，处理了运行前检查和销毁时保存的问题;
     * 4.启动守护服务;
     * 5.守护 Service 组件的启用状态, 使其不被 MAT 等工具禁用.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected fun onStart(intent: Intent?, flags: Int, startId: Int): Int {

        //启动守护服务，运行在:watch子进程中
        DaemonEnv.startServiceMayBind(WatchGuardService::class.java)

        //业务逻辑: 实际使用时，根据需求，将这里更改为自定义的条件，判定服务应当启动还是停止 (任务是否需要运行)
        val shouldStopService = shouldStopService(intent, flags, startId)
        if (shouldStopService != null) {
            if (shouldStopService) stopService(intent, flags, startId) else startService(
                intent,
                flags,
                startId
            )
        }
        if (mFirstStarted) {
            mFirstStarted = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeNotification(this)
            } else {
                //API Level 17 及以下的Android系统中，启动前台服务而不显示通知
                startNotification()
                //API Level 18 及以上的Android系统中，启动前台服务而不显示通知
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    DaemonEnv.startServiceSafely(
                        Intent(
                            application,
                            WorkNotificationService::class.java
                        )
                    )
                }
            }
            //守护 Service 组件的启用状态, 使其不被 MAT 等工具禁用
            packageManager.setComponentEnabledSetting(
                ComponentName(packageName, WatchGuardService::class.java.name),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        return START_STICKY
    }

    protected open fun startNotification() {
        startForeground(HASH_CODE, Notification())
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun startForeNotification(context: Context) {
        val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val mChannel =
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(mChannel)
        val notification = Notification.Builder(context, CHANNEL_ID).setAutoCancel(true).build()
        startForeground(CHANNEL_POSITION, notification)
    }

    fun startService(intent: Intent?, flags: Int, startId: Int) {

        //检查服务是否不需要运行
        val shouldStopService = shouldStopService(intent, flags, startId)
        if (shouldStopService != null && shouldStopService) return
        //若还没有取消订阅，说明任务仍在运行，为防止重复启动，直接 return
        val workRunning = isWorkRunning(intent, flags, startId)
        if (workRunning != null && workRunning) return
        //业务逻辑
        startWork(intent, flags, startId)
    }

    /**
     * 针对每次启动service都需要初始化的一些操作。例如：注册广播和取消注册广播的场景
     */
    protected abstract fun initForEachStartService()

    /**
     * 停止服务并取消定时唤醒
     *
     *
     * 停止服务使用取消订阅的方式实现，而不是调用 Context.stopService(Intent name)。因为：
     * 1.stopService 会调用 Service.onDestroy()，而 AbsWorkService 做了保活处理，会把 Service 再拉起来；
     * 2.我们希望 AbsWorkService 起到一个类似于控制台的角色，即 AbsWorkService 始终运行 (无论任务是否需要运行)，
     * 而是通过 onStart() 里自定义的条件，来决定服务是否应当启动或停止。
     */
    fun stopService(intent: Intent?, flags: Int, startId: Int) {
        //取消对任务的订阅
        stopWork(intent, flags, startId)
        //取消 Job / Alarm / Subscription
        cancelJobAlarmSub()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return onStart(intent, flags, startId)
    }

    @Nullable
    override fun onBind(intent: Intent): IBinder? {
        onStart(intent, 0, 0)
        return onBind(intent, null)
    }

    protected fun onEnd(rootIntent: Intent?) {
        onServiceKilled(rootIntent)
        if (!DaemonEnv.sInitialized) return
        DaemonEnv.sServiceClass?.let { DaemonEnv.startServiceMayBind(it) }
        DaemonEnv.startServiceMayBind(WatchGuardService::class.java)
    }

    /**
     * 最近任务列表中划掉卡片时回调
     */
    override fun onTaskRemoved(rootIntent: Intent) {
        onEnd(rootIntent)
    }

    /**
     * 设置-正在运行中停止服务时回调
     */
    override fun onDestroy() {
        onEnd(null)
    }

    class WorkNotificationService : Service() {
        /**
         * 利用漏洞在 API Level 18 及以上的 Android 系统中，启动前台服务而不显示通知
         */
        override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
            startForeground(HASH_CODE, Notification())
            stopSelf()
            return START_STICKY
        }

        override fun onBind(intent: Intent): IBinder? {
            return null
        }
    }

    companion object {
        protected const val HASH_CODE = 1
        public var CHANNEL_ID = "channel_id"
        private const val CHANNEL_POSITION = 1
        private const val CHANNEL_NAME = "channel_name"

        /**
         * 用于在不需要服务运行的时候取消 Job / Alarm / Subscription.
         */
        fun cancelJobAlarmSub() {
            if (!DaemonEnv.sInitialized) return
            DaemonEnv.sApp?.sendBroadcast(Intent(WakeUpReceiver.ACTION_CANCEL_JOB_ALARM_SUB))
        }
    }
}