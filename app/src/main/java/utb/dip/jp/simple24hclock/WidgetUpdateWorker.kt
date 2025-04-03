package utb.dip.jp.simple24hclock

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val WORK_NAME = "Simple24HClockUpdateWorker"
        fun enqueue(context: Context, delay: Long = 0) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            if (delay == 0L) {
                request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            } else {
                request.setInitialDelay(delay, TimeUnit.MILLISECONDS)
            }
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request.build())
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        // update widgets
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAllAppWidgets(context, appWidgetManager)
        // schedule next
        val delay = 60000 - (System.currentTimeMillis() % 60000)
        enqueue(context, delay)
        return Result.success()
    }
}

internal fun restart(context: Context) {
    WidgetUpdateWorker.enqueue(context)
}

internal fun stop(context: Context) {
    WidgetUpdateWorker.cancel(context)
}
