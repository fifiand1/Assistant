package com.wzf.assistant.service

import android.accounts.Account
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Notification
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wzf.daemon.AbsWorkService
import com.wzf.assistant.R
import java.util.concurrent.Executors

class SlaveService : AbsWorkService() {
    private val TAG = "SlaveService"

    private var mContext: Context? = null
    private lateinit var wakeLock: WakeLock
    private val mMediaPlayer: MediaPlayer? = null
    private val mAudioManager: AudioManager? = null
    private val mAudioFocusChange = OnAudioFocusChangeListener { focusChange: Int ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.w(TAG, "AUDIOFOCUS_GAIN")
                try {
                    startPlayMusic()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> Log.w(TAG, "AUDIOFOCUS_LOSS")
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> Log.w(TAG, "AUDIOFOCUS_LOSS_TRANSIENT")
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Log.w(
                TAG,
                "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
            )
        }
    }
    private val executor = Executors.newCachedThreadPool()
    override fun onUnbind(intent: Intent): Boolean {
        return false
    }

    override fun startNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setTicker(getString(R.string.service_ticker))
            .setContentTitle(getString(R.string.service_title))
            .setContentText(getString(R.string.service_text))
            .setWhen(System.currentTimeMillis())
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onCreate() {
        super.onCreate()
        mContext = this


        //建立账号
        val accountManager = AccountManager.get(mContext)
        val riderAccount = Account(getString(R.string.app_name), ACCOUNT_TYPE)
        accountManager.addAccountExplicitly(
            riderAccount,
            getString(R.string.app_name),
            null
        )
        ContentResolver.setIsSyncable(riderAccount, ACCOUNT_AUTHORITY, 1)
        ContentResolver.addPeriodicSync(riderAccount, ACCOUNT_AUTHORITY, Bundle(), 60)

        //开启同步
        ContentResolver.setSyncAutomatically(riderAccount, ACCOUNT_AUTHORITY, true)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(
            TAG,
            "onStartCommand() called with: intent = [" + intent + "], " + "flags = [" + flags + "], startId = ["
                    + startId + "]"
        )
        // mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        // if (mAudioManager != null)
        // mAudioManager.requestAudioFocus(mAudioFocusChange, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        // startPlayMusic();
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?, alwaysNull: Void?): IBinder? {
        return null
    }

    private fun startPlayMusic() {
//        if (mMediaPlayer == null) {
//            mMediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.no_notice);
//        }
//        Log.w(TAG, mMediaPlayer + "-start play");
//        mMediaPlayer.setLooping(true);
//        mMediaPlayer.start();
    }

    private fun stopPlayMusic() {
//            Log.w(TAG, "stop play");
//        if (mMediaPlayer != null) {
//            mMediaPlayer.stop();
//        }
    }

    override fun shouldStopService(intent: Intent?, flags: Int, startId: Int): Boolean {
        return sShouldStopService
    }

    @SuppressLint("InvalidWakeLockTag")
    override fun initForEachStartService() {
        // 保持屏幕常亮
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
            powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "SlaveService_wakeLock")

        wakeLock.setReferenceCounted(false)
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
    }

    override fun startWork(intent: Intent?, flags: Int, startId: Int) {
        Log.i(TAG, intent.toString())
        Log.i(TAG, intent?.extras.toString())
        isRunning = true
    }

    override fun stopWork(intent: Intent?, flags: Int, startId: Int) {
        stopService()
    }

    override fun isWorkRunning(intent: Intent?, flags: Int, startId: Int): Boolean {
        return isRunning
    }


    override fun onServiceKilled(rootIntent: Intent?) {
        Log.d(TAG, "onServiceKilled() called with: rootIntent = [$rootIntent]")
        try {
            stopPlayMusic()
            wakeLock.release()

            stopForeground(true)
        } catch (e: Exception) {
            Log.w(TAG, e.message)
        }
    }

    companion object {
        private const val TAG = "SlaveService"
        private const val ACCOUNT_TYPE = "com.wzf.assistant"
        private const val ACCOUNT_AUTHORITY = "wzf"
        var sShouldStopService = false
        var isRunning = false
        private const val NOTIFICATION_ID = 0x233
        fun stopService() {
            isRunning = false
            sShouldStopService = true
            cancelJobAlarmSub()
        }
    }
}