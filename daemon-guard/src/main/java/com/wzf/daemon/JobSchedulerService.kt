package com.wzf.daemon

import android.annotation.TargetApi
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build

/**
 * Created by GuoWee on 2018/5/18.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class JobSchedulerService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        if (DaemonEnv.sInitialized) {
            DaemonEnv.startServiceMayBind(DaemonEnv.sServiceClass!!)
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return false
    }
}