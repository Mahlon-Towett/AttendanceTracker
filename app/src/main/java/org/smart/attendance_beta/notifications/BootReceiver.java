package org.smart.attendance_beta.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Boot receiver triggered: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(action)) {

            // Check if user is logged in
            SharedPreferences prefs = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE);
            String employeeDocId = prefs.getString("employee_doc_id", null);

            if (employeeDocId != null) {
                Log.d(TAG, "User is logged in - rescheduling attendance notifications");

                // Setup notification channel
                AttendanceNotificationManager.setupNotificationChannel(context);

                // Reschedule all daily reminders
                AttendanceNotificationManager.scheduleDailyReminders(context);

                Log.d(TAG, "Attendance notifications rescheduled successfully");
            } else {
                Log.d(TAG, "No user logged in - notifications not scheduled");
            }
        }
    }
}