package com.example.beeing.wear

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.BatteryManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.example.beeing.PATH_DUTY
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable

// Whether this watch will buzz for the hourly reminder: set up, its notifications
// allowed, and off its charger (on the charger it isn't being worn). The phone goes
// silent only while this says yes, so anything doubtful means the phone rings.
//
// Android no longer delivers power-connected broadcasts to manifest receivers, so the
// state is read where the app already runs (the hourly alarm, opening it, a save, a
// sync) and a JobScheduler job that requires charging catches the moment it's plugged
// in. Unplugging is caught at the next of those, so the phone may ring once alongside
// the watch right after you put it on; it never goes silent for a watch on the charger.

private const val CHARGING_JOB_ID = 7001
private const val LAST_KEY = "duty_last" // -1 never sent, 0 off duty, 1 on duty

fun onDuty(ctx: Context): Boolean =
    WatchStore.state(ctx) != null &&
        NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
        !ctx.getSystemService(BatteryManager::class.java).isCharging

/** Sends the duty flag when it changed, and re-arms the plugged-in job while off the charger. */
fun reportDuty(ctx: Context) {
    val duty = onDuty(ctx)
    val prefs = ctx.getSharedPreferences("w", 0)
    if (prefs.getInt(LAST_KEY, -1) != (if (duty) 1 else 0)) {
        try {
            Wearable.getDataClient(ctx).putDataItem(
                PutDataRequest.create(PATH_DUTY).setData(byteArrayOf(if (duty) 1 else 0)).setUrgent()
            )
            prefs.edit { putInt(LAST_KEY, if (duty) 1 else 0) }
        } catch (e: Exception) {
            Log.w("Duty", "duty flag skipped", e)
        }
    }
    if (!ctx.getSystemService(BatteryManager::class.java).isCharging) armChargingJob(ctx)
}

private fun armChargingJob(ctx: Context) {
    val js = ctx.getSystemService(JobScheduler::class.java)
    if (js.getPendingJob(CHARGING_JOB_ID) != null) return
    js.schedule(
        JobInfo.Builder(CHARGING_JOB_ID, ComponentName(ctx, ChargingJob::class.java))
            .setRequiresCharging(true)
            .build()
    )
}

/** Runs once when the watch goes on its charger: off duty, so the phone rings. */
class ChargingJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        reportDuty(this)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
