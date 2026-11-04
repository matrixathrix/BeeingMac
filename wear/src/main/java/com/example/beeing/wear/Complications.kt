package com.example.beeing.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.GoalProgressComplicationData
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.example.beeing.WatchView

// Three complications, each in several slot shapes. Update period 0: they change only
// when WatchSurfaces.refresh pushes (a save, a sync, the hour, a restart). Icons are
// single-colour so they render in ambient too; nothing shows a minute countdown.

private fun plain(s: String) = PlainComplicationText.Builder(s).build()

private fun mono(ctx: Context, res: Int) = MonochromaticImage.Builder(Icon.createWithResource(ctx, res)).build()

/** Rating screen when an hour waits, else the launcher (the caught-up picture). */
private fun tap(ctx: Context, v: WatchView, code: Int): PendingIntent = PendingIntent.getActivity(
    ctx, 100 + code,
    if (v.waiting.isNotEmpty()) RatingActivity.intent(ctx, v.waiting.first())
    else Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

private fun streakText(v: WatchView) = when { v.paused -> "Paused ${v.streak}"; v.atRisk -> "${v.streak}!"; else -> "${v.streak}" }

abstract class BeeingComplication : SuspendingComplicationDataSourceService() {
    abstract fun build(type: ComplicationType, v: WatchView): ComplicationData?

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val v = WatchSurfaces.view(this)
        return if (v.setUp) build(request.complicationType, v) else NoDataComplicationData()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, WatchView(true, listOf(0L), 5, 8, 12, false, false, false, 4, true))
}

/** Today's ring: hours rated of the day's goal. */
class RingComplication : BeeingComplication() {
    override fun build(type: ComplicationType, v: WatchView): ComplicationData? {
        val label = "${v.todayHours}/${v.goal}"
        val desc = plain("${v.todayHours} of ${v.goal} hours today")
        val t = tap(this, v, 1)
        return when (type) {
            ComplicationType.GOAL_PROGRESS -> if (Build.VERSION.SDK_INT >= 33)
                GoalProgressComplicationData.Builder(v.todayHours.toFloat(), v.goal.toFloat(), desc)
                    .setText(plain(label)).setTapAction(t).build() else null
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(v.todayHours.coerceAtMost(v.goal).toFloat(), 0f, v.goal.toFloat(), desc)
                    .setText(plain(label)).setTapAction(t).build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(plain(label), desc).setTapAction(t).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(desc, desc).setTapAction(t).build()
            else -> null
        }
    }
}

/** Waiting hours: the one you act on. */
class WaitingComplication : BeeingComplication() {
    override fun build(type: ComplicationType, v: WatchView): ComplicationData? {
        val n = v.waiting.size
        val desc = plain(if (n == 0) "All caught up" else "$n ${if (n == 1) "hour" else "hours"} waiting")
        val icon = mono(this, if (n == 0) R.drawable.ic_sym_check_circle else R.drawable.ic_sym_hourglass)
        val t = tap(this, v, 2)
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(plain(if (n == 0) " " else "$n"), desc).setMonochromaticImage(icon).setTapAction(t).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(desc, desc).setMonochromaticImage(icon).setTapAction(t).build()
            else -> null
        }
    }
}

/** Streak + savers, in the phone's states (at risk "12!", beginner "Paused 12"). */
class StreakComplication : BeeingComplication() {
    override fun build(type: ComplicationType, v: WatchView): ComplicationData? {
        val icon = mono(this, if (v.paused || v.beginner) R.drawable.ic_sym_beginner else R.drawable.ic_sym_fire)
        val desc = plain(
            (if (v.paused) "Streak paused at ${v.streak}" else "${v.streak}-day streak${if (v.atRisk) ", at risk" else ""}") +
                " · ${v.savers} savers"
        )
        val t = tap(this, v, 3)
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(plain(streakText(v)), desc)
                .setTitle(plain("${v.savers}/5")).setMonochromaticImage(icon).setTapAction(t).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                plain(
                    when {
                        v.paused -> "Paused ${v.streak} · ${v.savers} savers"
                        v.atRisk -> "${v.streak}-day streak! · ${v.savers} savers"
                        else -> "${v.streak}-day streak · ${v.savers} savers"
                    }
                ), desc
            ).setMonochromaticImage(icon).setTapAction(t).build()
            else -> null
        }
    }
}

/** Push all three; called from WatchSurfaces.refresh only. */
fun refreshComplications(ctx: Context) {
    listOf(RingComplication::class.java, WaitingComplication::class.java, StreakComplication::class.java).forEach {
        ComplicationDataSourceUpdateRequester.create(ctx, ComponentName(ctx, it)).requestUpdateAll()
    }
}
