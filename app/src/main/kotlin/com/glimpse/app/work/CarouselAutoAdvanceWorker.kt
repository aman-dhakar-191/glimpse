package com.glimpse.app.work

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.glimpse.app.data.CarouselSettingsStore
import com.glimpse.app.widgets.ShapedCarouselWidget
import com.glimpse.app.widgets.ShapedCarouselWidgetRenderer
import java.util.concurrent.TimeUnit

// Periodically advances every instance of the "Glimpse Carousel" widget by
// one page — the exact same page-advance logic the widget's own arrow
// button triggers on tap (see ShapedCarouselWidgetRenderer.advanceAndPush),
// just fired on a schedule instead of a touch. Opt-in and off by default
// (see CarouselSettingsStore) — most people would rather tap through at
// their own pace than have it move on its own.
class CarouselAutoAdvanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(applicationContext, ShapedCarouselWidget::class.java)
        )
        appWidgetIds.forEach { appWidgetId ->
            ShapedCarouselWidgetRenderer.advanceAndPush(applicationContext, appWidgetId)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "carousel_auto_advance"

        // Reads the current CarouselSettingsStore interval and (re)schedules
        // to match — an interval of 0 (off) cancels any existing periodic
        // work instead of scheduling one. Called both at app startup (so a
        // setting saved last session is still honored after a reboot) and
        // immediately whenever the setting changes (see
        // CarouselSettingsViewModel) — UPDATE (not KEEP) so a changed
        // interval actually takes effect right away instead of waiting for
        // the previous schedule to run out.
        fun reschedule(context: Context) {
            val minutes = CarouselSettingsStore.loadAutoAdvanceMinutes(context)
            val workManager = WorkManager.getInstance(context)
            if (minutes <= CarouselSettingsStore.AUTO_ADVANCE_OFF) {
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<CarouselAutoAdvanceWorker>(minutes.toLong(), TimeUnit.MINUTES).build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
