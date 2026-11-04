package com.example.beeing.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.beeing.hourRangeLabel
import com.example.beeing.mergedRatings
import com.example.beeing.wear.ui.FieldBottom
import com.example.beeing.wear.ui.Ink
import com.example.beeing.wear.ui.ScoreScreen
import com.example.beeing.wear.ui.TagsScreen
import com.example.beeing.wear.ui.WearTheme
import kotlinx.coroutines.delay

/** Score, then tags, then save — for the hour in [EXTRA_HOUR], or the one about to expire. */
class RatingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefer = intent.getLongExtra(EXTRA_HOUR, 0L).takeIf { it > 0L }
        setContent { WearTheme { RatingApp(prefer, onDone = ::finish) } }
    }

    companion object {
        const val EXTRA_HOUR = "hour"
        fun intent(ctx: Context, hour: Long? = null) = Intent(ctx, RatingActivity::class.java).apply {
            hour?.let { putExtra(EXTRA_HOUR, it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}

/** Hours this watch knows are rated: the phone's copy plus its own saves. */
fun ratedHours(ctx: Context): Set<Long> {
    val state = WatchStore.state(ctx) ?: return emptySet()
    return mergedRatings(state, WatchStore.local(ctx)).mapTo(HashSet()) { it.timestamp }
}

private fun msToNextMinute(now: Long) = 60_000L - now % 60_000L + 50L

@Composable
private fun RatingApp(prefer: Long?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val state = remember { WatchStore.state(ctx) }
    var flow by remember { mutableStateOf(state?.let { startFlow(System.currentTimeMillis(), ratedHours(ctx), prefer) }) }
    var saved by remember { mutableStateOf(false) }

    // Only while this screen exists: catch the hour turning under it.
    LaunchedEffect(Unit) {
        while (true) {
            delay(msToNextMinute(System.currentTimeMillis()))
            flow = flow?.onClock(System.currentTimeMillis(), ratedHours(ctx)) ?: run { onDone(); null }
        }
    }

    val f = flow
    if (state == null || f == null) {
        Message(if (state == null) "Open Beeing on your phone to set up" else "All caught up")
        LaunchedEffect(Unit) { delay(1500); onDone() }
        return
    }
    if (saved) {
        Message("Saved")
        LaunchedEffect(f) { delay(700); saved = false }
        return
    }
    BackHandler(enabled = f.step == RatingFlow.Step.TAGS) { flow = f.copy(step = RatingFlow.Step.SCORE) }
    when (f.step) {
        RatingFlow.Step.SCORE -> ScoreScreen(
            hourLabel = hourRangeLabel(f.hour), moreWaiting = f.waiting.size > 1, score = f.score, notice = f.notice,
            onScore = { flow = f.copy(score = it) },
            onSwitchHour = if (f.waiting.size > 1) ({ flow = f.switchHour() }) else null,
            onNext = { flow = f.copy(step = RatingFlow.Step.TAGS) }
        )
        RatingFlow.Step.TAGS -> TagsScreen(
            header = "${f.score} · ${hourRangeLabel(f.hour)}", topTags = state.topTags, allTags = state.tags,
            selected = f.tags, notice = f.notice,
            onToggle = { t -> flow = f.copy(tags = if (t in f.tags) f.tags - t else f.tags + t) },
            onSave = {
                val now = System.currentTimeMillis()
                saveFromWatch(ctx, f.hour, f.score ?: return@TagsScreen, state.tags.filter { it in f.tags }, now)
                val next = f.afterSave(now, ratedHours(ctx))
                if (next == null) onDone() else { flow = next; saved = true }
            }
        )
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().background(FieldBottom), contentAlignment = Alignment.Center) {
        Text(text, color = Ink, fontSize = 15.sp)
    }
}
