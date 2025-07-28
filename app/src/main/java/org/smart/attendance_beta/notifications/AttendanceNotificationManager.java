package org.smart.attendance_beta.notifications;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.smart.attendance_beta.AttendanceActivity;
import org.smart.attendance_beta.R;

import java.util.Calendar;

public class AttendanceNotificationManager {
    private static final String TAG = "AttendanceNotifications";
    private static final String CHANNEL_ID = "attendance_reminders";
    private static final int CLOCK_IN_ALARM_ID = 1001;
    private static final int CLOCK_OUT_ALARM_ID = 1002;
    private static final int LATE_ALARM_ID = 1003;

    public static void setupNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Attendance Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Reminders to clock in and clock out");
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void scheduleDailyReminders(Context context) {
        Log.d(TAG, "Scheduling daily attendance reminders");

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Schedule Clock-In reminder for 8:00 AM
        scheduleClockInReminder(context, alarmManager);

        // Schedule Late arrival alert for 8:15 AM
        scheduleLateAlert(context, alarmManager);

        // Schedule Clock-Out reminder for 5:00 PM
        scheduleClockOutReminder(context, alarmManager);
    }

    private static void scheduleClockInReminder(Context context, AlarmManager alarmManager) {
        Intent intent = new Intent(context, AttendanceReminderReceiver.class);
        intent.setAction("CLOCK_IN_REMINDER");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, CLOCK_IN_ALARM_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set for 8:00 AM
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 26);
        calendar.set(Calendar.SECOND, 0);

        // If it's already past 8 AM today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Schedule repeating alarm for weekdays only
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }

        Log.d(TAG, "Clock-in reminder scheduled for: " + calendar.getTime());
    }

    private static void scheduleLateAlert(Context context, AlarmManager alarmManager) {
        Intent intent = new Intent(context, AttendanceReminderReceiver.class);
        intent.setAction("LATE_ALERT");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, LATE_ALARM_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set for 8:15 AM
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 8);
        calendar.set(Calendar.MINUTE, 15);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }

        Log.d(TAG, "Late alert scheduled for: " + calendar.getTime());
    }

    private static void scheduleClockOutReminder(Context context, AlarmManager alarmManager) {
        Intent intent = new Intent(context, AttendanceReminderReceiver.class);
        intent.setAction("CLOCK_OUT_REMINDER");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, CLOCK_OUT_ALARM_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set for 5:00 PM
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 17);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }

        Log.d(TAG, "Clock-out reminder scheduled for: " + calendar.getTime());
    }

    public static void showClockInNotification(Context context) {
        Intent intent = new Intent(context, AttendanceActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle("⏰ Time to Clock In!")
                .setContentText("Good morning! Don't forget to clock in when you arrive at the office.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 200, 500});

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(1001, builder.build());
    }

    public static void showLateAlert(Context context) {
        Intent intent = new Intent(context, AttendanceActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle("⚠️ Late Arrival Alert")
                .setContentText("You are late for work. Please clock in as soon as you arrive.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 300, 100, 300, 100, 300});

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(1003, builder.build());
    }

    public static void showClockOutNotification(Context context) {
        Intent intent = new Intent(context, AttendanceActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle("🕐 Time to Clock Out!")
                .setContentText("It's 5:00 PM. Don't forget to clock out before leaving the office.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 200, 500});

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(1002, builder.build());
    }

    public static void cancelAllReminders(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Cancel all scheduled alarms
        Intent intent = new Intent(context, AttendanceReminderReceiver.class);

        PendingIntent clockInPendingIntent = PendingIntent.getBroadcast(
                context, CLOCK_IN_ALARM_ID, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent clockOutPendingIntent = PendingIntent.getBroadcast(
                context, CLOCK_OUT_ALARM_ID, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent latePendingIntent = PendingIntent.getBroadcast(
                context, LATE_ALARM_ID, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (clockInPendingIntent != null) alarmManager.cancel(clockInPendingIntent);
        if (clockOutPendingIntent != null) alarmManager.cancel(clockOutPendingIntent);
        if (latePendingIntent != null) alarmManager.cancel(latePendingIntent);

        Log.d(TAG, "All attendance reminders cancelled");
    }
}