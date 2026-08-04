package com.studypin.app.location

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.studypin.app.BuildConfig
import com.studypin.app.data.OccupancyRepository
import com.studypin.app.model.StudySpot

/** Registers an exit geofence for a spot and schedules its reminder. */
object LocationReminderManager {
    const val ACTION_GEOFENCE_EVENT = "com.studypin.app.location.GEOFENCE_EVENT"
    const val ACTION_REMINDER = "com.studypin.app.location.REMINDER"
    const val EXTRA_SPOT_ID = "spot_id"
    const val EXTRA_SPOT_NAME = "spot_name"
    const val EXTRA_SHOW_PROMPT = "show_leave_prompt"

    // The project proposal calls for reminding users after they have been away for about 5 minutes.
    // Debug builds use a much shorter delay so testers don't have to wait 5 minutes per iteration.
    val REMINDER_DELAY_MILLIS: Long = if (BuildConfig.DEBUG) 10 * 1000L else 5 * 60 * 1000L

    const val GEOFENCE_RADIUS_METERS = 10f

    private const val PREFS_NAME = "location_reminders"
    private const val KEY_ENTERED_PREFIX = "entered_"
    private const val KEY_TRACKING_PREFIX = "tracking_"
    private const val KEY_PENDING_SPOT_ID = "pending_spot_id"
    private const val KEY_PENDING_SPOT_NAME = "pending_spot_name"
    private const val NOTIFICATION_CHANNEL_ID = "availability_reminders_high"
    private const val NOTIFICATION_CHANNEL_NAME = "Availability reminders"

    fun geofenceId(spotId: String): String = "study_spot_$spotId"

    fun hasFineLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    fun startTracking(
        context: Context,
        spot: StudySpot,
        onSuccess: () -> Unit,
        onFailure: (Exception?) -> Unit
    ) {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocationPermission || !hasBackgroundLocationPermission) {
            onFailure(SecurityException("Location permission is required for geofence reminders"))
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(geofenceId(spot.id))
            .setCircularRegion(spot.latitude, spot.longitude, GEOFENCE_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .setNotificationResponsiveness(1_000)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        try {
            LocationServices.getGeofencingClient(context)
                .addGeofences(request, geofencePendingIntent(context))
                .addOnSuccessListener {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString("name_${spot.id}", spot.name)
                        .putBoolean(KEY_TRACKING_PREFIX + spot.id, true)
                        .apply()
                    onSuccess()
                }
                .addOnFailureListener { exception -> onFailure(exception) }
        } catch (securityException: SecurityException) {
            onFailure(securityException)
        }
    }

    fun stopTracking(context: Context, spotId: String) {
        val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
        geofencingClient.removeGeofences(listOf(geofenceId(spotId)))
        cancelReminder(context, spotId)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ENTERED_PREFIX + spotId)
            .remove(KEY_TRACKING_PREFIX + spotId)
            .remove("name_$spotId")
            .apply()
    }

    /** Ends a visit: removes the geofence/reminder AND checks the user out of Firestore, if signed in. */
    fun endVisit(context: Context, spotId: String, userId: String?) =
        run {
            stopTracking(context, spotId)
            userId?.let { OccupancyRepository.checkOut(spotId, it) }
        }

    fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction(ACTION_GEOFENCE_EVENT)
        // GeofencingClient requires a mutable PendingIntent on Android 12/API 31+.
        val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
        )
    }

    fun scheduleReminder(context: Context, spotId: String, spotName: String) {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .setData(android.net.Uri.parse("studypin://reminder/$spotId"))
            .putExtra(EXTRA_SPOT_ID, spotId)
            .putExtra(EXTRA_SPOT_NAME, spotName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(spotId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + REMINDER_DELAY_MILLIS,
            pendingIntent
        )
    }

    fun cancelReminder(context: Context, spotId: String) {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .setData(android.net.Uri.parse("studypin://reminder/$spotId"))
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeFor(spotId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    fun setEntered(context: Context, spotId: String, entered: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENTERED_PREFIX + spotId, entered)
            .apply()
    }

    fun hasEntered(context: Context, spotId: String): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENTERED_PREFIX + spotId, false)

    fun isTracking(context: Context, spotId: String): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TRACKING_PREFIX + spotId, false)

    fun markReminderPending(context: Context, spotId: String, spotName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_SPOT_ID, spotId)
            .putString(KEY_PENDING_SPOT_NAME, spotName)
            .apply()
    }

    fun pendingReminder(context: Context): Pair<String, String>? {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val spotId = preferences.getString(KEY_PENDING_SPOT_ID, null) ?: return null
        val spotName = preferences.getString(KEY_PENDING_SPOT_NAME, "this study spot")
            ?: "this study spot"
        return spotId to spotName
    }

    fun clearPendingReminder(context: Context, spotId: String) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.getString(KEY_PENDING_SPOT_ID, null) == spotId) {
            preferences.edit()
                .remove(KEY_PENDING_SPOT_ID)
                .remove(KEY_PENDING_SPOT_NAME)
                .apply()
        }
    }

    fun notificationChannelId(): String = NOTIFICATION_CHANNEL_ID

    private fun requestCodeFor(spotId: String): Int =
        spotId.hashCode() and 0x7fffffff
}
