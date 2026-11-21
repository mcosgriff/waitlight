package io.cosgriff.waitlight.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.cosgriff.waitlight.MainActivity
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.data.vans.DriveByRepository
import io.cosgriff.waitlight.data.vehicles.VehicleRepository
import io.cosgriff.waitlight.domain.DueReminder
import io.cosgriff.waitlight.domain.ReminderKind
import io.cosgriff.waitlight.domain.TimeLeft
import io.cosgriff.waitlight.domain.nextReminderAt
import io.cosgriff.waitlight.domain.reminderRun
import io.cosgriff.waitlight.ui.TimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one [ReminderWorker] waiting for the next vehicle reminder ([nextReminderAt]), or none. It follows the
 * vehicles: a vehicle added, a renewal changed, a switch turned, a reminder marked sent each move the wake. Nothing
 * is fetched and nothing leaves the phone: the reminders are worked out here and posted as local notifications.
 */
@Singleton
class Reminders @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vehicles: VehicleRepository,
    private val clock: Clock,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun start(scope: CoroutineScope) {
        scope.launch {
            vehicles.loadedVehicles.map(::nextReminderAt).distinctUntilChanged().collect(::wakeAt)
        }
    }

    private suspend fun wakeAt(at: Instant?) = withContext(Dispatchers.IO) {
        if (at == null) {
            workManager.cancelUniqueWork(NEXT)
            return@withContext
        }
        // Already waiting for that time, or running for it: an app start (WorkManager starting the app to run the
        // check, say) mustn't stop and restart it, or a reminder could go out twice.
        val tag = "$TAG_AT${at.toEpochMilli()}"
        if (workManager.getWorkInfosForUniqueWork(NEXT).get().any { !it.state.isFinished && tag in it.tags }) return@withContext
        val delay = Duration.between(clock.instant(), at).coerceAtLeast(Duration.ZERO)
        val wake = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay).addTag(tag).build()
        workManager.enqueueUniqueWork(NEXT, ExistingWorkPolicy.REPLACE, wake)
        Log.i(TAG, "Next vehicle reminder check at $at")
    }

    private companion object {
        const val TAG = "Waitlight"
        const val NEXT = "vehicle-reminders"
        const val TAG_AT = "reminder-at:"
    }
}

/**
 * Posts the reminders due now ([reminderRun]) and marks them sent, which moves [Reminders] on to the next. The
 * drive-bys are read here, at the reminder's time, for "halfway, if under 2 drive-bys" and the counts it words.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val vehicles: VehicleRepository,
    private val driveBys: DriveByRepository,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val run = reminderRun(vehicles.loaded(), driveBys.loaded(), clock.instant())
        run.post.forEach(notifier::post)
        vehicles.markSent(run.settled)
        return Result.success()
    }
}

/** Words a [DueReminder] and posts it on the reminders channel; a tap opens that vehicle. */
@Singleton
class ReminderNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    /** The channel, made once at start so it shows in the app's notification settings. */
    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.reminder_channel_desc)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun post(reminder: DueReminder) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Notifications not allowed; ${reminder.kind} for vehicle ${reminder.vehicle.vehicle.id} not shown")
            return
        }
        val (title, body) = words(reminder)
        val id = reminder.vehicle.vehicle.id
        val open = PendingIntent.getActivity(
            context,
            id.toInt(),
            MainActivity.vehicleIntent(context, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((id * ReminderKind.entries.size + reminder.kind.ordinal).toInt(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission withdrawn", e)
        }
    }

    private fun words(reminder: DueReminder): Pair<String, String> {
        val vehicle = reminder.vehicle
        val window = vehicle.window
        val name = vehicle.vehicle.shortName
        val res = context.resources
        val renewal = TimeText.monthYear(window.renews)
        val closes = TimeText.monthDay(window.closes)
        return when (reminder.kind) {
            ReminderKind.WINDOW_OPENS -> context.getString(R.string.reminder_opens_title, name) to
                context.getString(R.string.reminder_opens_body, closes, renewal)
            ReminderKind.HALFWAY -> {
                val left = when (val time = window.timeLeft(vehicle.today)) {
                    is TimeLeft.Months -> res.getQuantityString(R.plurals.reminder_months, time.months, time.months)
                    is TimeLeft.Days -> res.getQuantityString(R.plurals.reminder_days, time.days, time.days)
                }
                context.getString(R.string.reminder_halfway_title, name) to
                    context.getString(if (vehicle.count == 0) R.string.reminder_halfway_none else R.string.reminder_halfway_one, left)
            }
            ReminderKind.CLOSING_SOON -> context.getString(R.string.reminder_closing_title, name) to when (vehicle.count) {
                0 -> context.getString(R.string.reminder_closing_none, closes, renewal)
                1 -> context.getString(R.string.reminder_closing_one, closes, renewal)
                else -> res.getQuantityString(R.plurals.reminder_closing_many, vehicle.count, closes, renewal, vehicle.count)
            }
            ReminderKind.TEST_DUE -> context.getString(R.string.reminder_due_title, name, TimeText.monthDay(window.testDueBy)) to
                context.getString(R.string.reminder_due_body)
        }
    }

    private companion object {
        const val TAG = "Waitlight"
        const val CHANNEL = "vehicle-reminders"
    }
}
