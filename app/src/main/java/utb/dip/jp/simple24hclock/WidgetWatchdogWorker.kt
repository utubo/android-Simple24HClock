package utb.dip.jp.simple24hclock

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WidgetWatchdogWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        restart(applicationContext)
        return Result.success()
    }
}

internal fun startWatchdogWorker(context: Context) {
    val constraints = Constraints.Builder()
        .build()

    val request = PeriodicWorkRequestBuilder<WidgetWatchdogWorker>(
        15, TimeUnit.MINUTES
    )
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "WIDGET_WATCHDOG",
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}

internal fun stopWatchdogWorker(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork("WIDGET_WATCHDOG")
}