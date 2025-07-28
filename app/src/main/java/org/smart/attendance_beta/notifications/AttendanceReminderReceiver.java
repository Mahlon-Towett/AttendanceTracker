package org.smart.attendance_beta.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.smart.attendance_beta.utils.DateTimeUtils;

import java.util.Calendar;

public class AttendanceReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "AttendanceReminder";
    private FirebaseFirestore db;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received alarm: " + action);

        // Skip notifications on weekends
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            Log.d(TAG, "Skipping notification - weekend");
            scheduleNextNotification(context, action);
            return;
        }

        // Check if user is logged in
        SharedPreferences prefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE);
        String employeeDocId = prefs.getString("employee_doc_id", null);

        if (employeeDocId == null) {
            Log.d(TAG, "No logged in user - skipping notification");
            scheduleNextNotification(context, action);
            return;
        }

        db = FirebaseFirestore.getInstance();

        switch (action) {
            case "CLOCK_IN_REMINDER":
                handleClockInReminder(context, employeeDocId);
                break;
            case "LATE_ALERT":
                handleLateAlert(context, employeeDocId);
                break;
            case "CLOCK_OUT_REMINDER":
                handleClockOutReminder(context, employeeDocId);
                break;
            default:
                Log.w(TAG, "Unknown action: " + action);
        }

        // Schedule next notification for tomorrow
        scheduleNextNotification(context, action);
    }

    private void handleClockInReminder(Context context, String employeeDocId) {
        Log.d(TAG, "Processing clock-in reminder");

        String today = DateTimeUtils.getCurrentDate();

        // Check if employee has already clocked in today
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", today)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // Employee hasn't clocked in yet
                            Log.d(TAG, "Employee hasn't clocked in - showing reminder");
                            AttendanceNotificationManager.showClockInNotification(context);
                        } else {
                            Log.d(TAG, "Employee already clocked in - skipping reminder");
                        }
                    } else {
                        Log.e(TAG, "Error checking attendance: " + task.getException());
                        // Show notification anyway if we can't check
                        AttendanceNotificationManager.showClockInNotification(context);
                    }
                });
    }

    private void handleLateAlert(Context context, String employeeDocId) {
        Log.d(TAG, "Processing late alert");

        String today = DateTimeUtils.getCurrentDate();

        // Check if employee has clocked in today
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", today)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // Employee is late - hasn't clocked in by 8:15 AM
                            Log.d(TAG, "Employee is late - showing alert");
                            AttendanceNotificationManager.showLateAlert(context);
                        } else {
                            Log.d(TAG, "Employee has clocked in - not late");
                        }
                    } else {
                        Log.e(TAG, "Error checking late status: " + task.getException());
                    }
                });
    }

    private void handleClockOutReminder(Context context, String employeeDocId) {
        Log.d(TAG, "Processing clock-out reminder");

        String today = DateTimeUtils.getCurrentDate();

        // Check if employee is still clocked in (sessionActive = true)
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", today)
                .whereEqualTo("sessionActive", true)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (!task.getResult().isEmpty()) {
                            // Employee is still clocked in
                            DocumentSnapshot attendance = task.getResult().getDocuments().get(0);
                            String clockInTime = attendance.getString("clockInTime");

                            // Calculate hours worked so far
                            String currentTime = DateTimeUtils.getCurrentTime();
                            double hours = DateTimeUtils.calculateHoursWorked(clockInTime, currentTime);
                            String hoursWorked = DateTimeUtils.formatHoursWorked(hours);

                            Log.d(TAG, "Employee still clocked in - showing reminder. Hours worked: " + hoursWorked);
                            AttendanceNotificationManager.showClockOutNotification(context);
                        } else {
                            Log.d(TAG, "Employee already clocked out or not clocked in today");
                        }
                    } else {
                        Log.e(TAG, "Error checking clock-out status: " + task.getException());
                    }
                });
    }

    private void scheduleNextNotification(Context context, String action) {
        // Schedule the same notification for next weekday
        Calendar nextDay = Calendar.getInstance();
        nextDay.add(Calendar.DAY_OF_MONTH, 1);

        // Skip to Monday if next day is weekend
        int dayOfWeek = nextDay.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.SATURDAY) {
            nextDay.add(Calendar.DAY_OF_MONTH, 2); // Skip to Monday
        } else if (dayOfWeek == Calendar.SUNDAY) {
            nextDay.add(Calendar.DAY_OF_MONTH, 1); // Skip to Monday
        }

        // Set the appropriate time based on action
        switch (action) {
            case "CLOCK_IN_REMINDER":
                nextDay.set(Calendar.HOUR_OF_DAY, 8);
                nextDay.set(Calendar.MINUTE, 0);
                break;
            case "LATE_ALERT":
                nextDay.set(Calendar.HOUR_OF_DAY, 8);
                nextDay.set(Calendar.MINUTE, 15);
                break;
            case "CLOCK_OUT_REMINDER":
                nextDay.set(Calendar.HOUR_OF_DAY, 17);
                nextDay.set(Calendar.MINUTE, 0);
                break;
        }
        nextDay.set(Calendar.SECOND, 0);

        // Schedule next notification
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent nextIntent = new Intent(context, AttendanceReminderReceiver.class);
        nextIntent.setAction(action);

        int requestCode = getRequestCodeForAction(action);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                context, requestCode, nextIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
        );

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    nextDay.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    nextDay.getTimeInMillis(),
                    pendingIntent
            );
        }

        Log.d(TAG, "Next " + action + " scheduled for: " + nextDay.getTime());
    }

    private int getRequestCodeForAction(String action) {
        switch (action) {
            case "CLOCK_IN_REMINDER":
                return 1001;
            case "LATE_ALERT":
                return 1003;
            case "CLOCK_OUT_REMINDER":
                return 1002;
            default:
                return 1000;
        }
    }
}