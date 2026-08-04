package com.studypin.app.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.studypin.app.MainActivity
import com.studypin.app.R

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LocationReminderManager.ACTION_REMINDER -> showReminder(context, intent)
            LocationReminderManager.ACTION_GEOFENCE_EVENT -> handleGeofenceEvent(context, intent)
        }
    }

    private fun handleGeofenceEvent(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        event.triggeringGeofences?.forEach { geofence ->
            val spotId = geofence.requestId.removePrefix("study_spot_")
            val spotName = context.getSharedPreferences("location_reminders", Context.MODE_PRIVATE)
                .getString("name_$spotId", "this study spot")
                ?: "this study spot"

            when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    LocationReminderManager.setEntered(context, spotId, true)
                    LocationReminderManager.cancelReminder(context, spotId)
                }

                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    if (LocationReminderManager.hasEntered(context, spotId)) {
                        LocationReminderManager.setEntered(context, spotId, false)
                        LocationReminderManager.scheduleReminder(context, spotId, spotName)
                    }
                }
            }
        }
    }

    private fun showReminder(context: Context, intent: Intent) {
        val spotId = intent.getStringExtra(LocationReminderManager.EXTRA_SPOT_ID) ?: return
        val spotName = intent.getStringExtra(LocationReminderManager.EXTRA_SPOT_NAME)
            ?: "your study spot"

        // If the user came back before the alarm fired, do not show a stale reminder.
        if (LocationReminderManager.hasEntered(context, spotId)) return

        LocationReminderManager.markReminderPending(context, spotId, spotName)
        createNotificationChannel(context)
        val openSpotIntent = Intent(context, com.studypin.app.LeaveSpotPromptActivity::class.java)
            .putExtra(LocationReminderManager.EXTRA_SPOT_ID, spotId)
            .putExtra(LocationReminderManager.EXTRA_SPOT_NAME, spotName)
            .putExtra(LocationReminderManager.EXTRA_SHOW_PROMPT, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            spotId.hashCode() and 0x7fffffff,
            openSpotIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            LocationReminderManager.notificationChannelId()
        )
            .setSmallIcon(R.drawable.ic_location)
            .setColor(ContextCompat.getColor(context, R.color.brand_main))
            .setContentTitle("Update availability")
            .setContentText("You left $spotName. Is its availability different now?")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("You left $spotName about 10 minutes ago. Open StudyPin to update its availability.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            NotificationManagerCompat.from(context).notify(
                spotId.hashCode() and 0x7fffffff,
                notification
            )
        } catch (_: SecurityException) {
            // Notification permission may be revoked between the check and notify().
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LocationReminderManager.notificationChannelId(),
                "Availability reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to update study-spot availability after leaving"
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
