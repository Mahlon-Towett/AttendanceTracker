// FCMService.java - Firebase Cloud Messaging integration
package org.smart.attendance_beta.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.smart.attendance_beta.AttendanceActivity;
import org.smart.attendance_beta.R;

import java.util.HashMap;
import java.util.Map;

public class FCMService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";

    // Notification channels
    private static final String CHANNEL_REMINDERS = "attendance_reminders";
    private static final String CHANNEL_LATE_ALERTS = "late_alerts";
    private static final String CHANNEL_WEEKLY_SUMMARY = "weekly_summary";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());

        // Check if message contains a notification payload
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());

            String messageType = remoteMessage.getData().get("type");
            showNotification(remoteMessage, messageType);
        }

        // Handle data payload
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            handleDataMessage(remoteMessage.getData());
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "Refreshed token: " + token);
        sendTokenToServer(token);
    }

    /**
     * Send FCM token to server for storage
     */
    private void sendTokenToServer(String token) {
        String employeeDocId = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                .getString("employee_doc_id", null);

        if (employeeDocId == null) {
            Log.w(TAG, "No employee doc ID found, token not sent to server");
            return;
        }

        FirebaseFunctions functions = FirebaseFunctions.getInstance();

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("employeeId", employeeDocId);

        functions.getHttpsCallable("updateFCMToken")
                .call(data)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "FCM token successfully sent to server");
                    } else {
                        Log.e(TAG, "Failed to send FCM token to server", task.getException());
                    }
                });
    }

    /**
     * Create notification channels for different types of notifications
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);

            // Attendance Reminders Channel
            NotificationChannel remindersChannel = new NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Attendance Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            remindersChannel.setDescription("Clock-in and clock-out reminders");
            remindersChannel.enableVibration(true);
            remindersChannel.setVibrationPattern(new long[]{0, 250, 250, 250});
            notificationManager.createNotificationChannel(remindersChannel);

            // Late Alerts Channel
            NotificationChannel lateAlertsChannel = new NotificationChannel(
                    CHANNEL_LATE_ALERTS,
                    "Late Arrival Alerts",
                    NotificationManager.IMPORTANCE_UNSPECIFIED
            );
            lateAlertsChannel.setDescription("Alerts for late arrivals");
            lateAlertsChannel.enableVibration(true);
            lateAlertsChannel.setVibrationPattern(new long[]{0, 500, 200, 500});
            notificationManager.createNotificationChannel(lateAlertsChannel);

            // Weekly Summary Channel
            NotificationChannel weeklySummaryChannel = new NotificationChannel(
                    CHANNEL_WEEKLY_SUMMARY,
                    "Weekly Summary",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            weeklySummaryChannel.setDescription("Weekly attendance summaries");
            notificationManager.createNotificationChannel(weeklySummaryChannel);
        }
    }

    /**
     * Show notification based on message type
     */
    private void showNotification(RemoteMessage remoteMessage, String messageType) {
        String title = remoteMessage.getNotification().getTitle();
        String body = remoteMessage.getNotification().getBody();

        String channelId = getChannelIdForMessageType(messageType);
        int priority = getPriorityForMessageType(messageType);

        Intent intent = new Intent(this, AttendanceActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Add data from the message to the intent
        for (Map.Entry<String, String> entry : remoteMessage.getData().entrySet()) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(getNotificationIcon(messageType))
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(priority)
                .setContentIntent(pendingIntent)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body));

        // Add action buttons based on message type
        addNotificationActions(notificationBuilder, messageType, pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = generateNotificationId(messageType);
        notificationManager.notify(notificationId, notificationBuilder.build());
    }

    /**
     * Get appropriate channel ID for message type
     */
    private String getChannelIdForMessageType(String messageType) {
        if (messageType == null) return CHANNEL_REMINDERS;

        switch (messageType) {
            case "CLOCK_IN_REMINDER":
            case "CLOCK_OUT_REMINDER":
                return CHANNEL_REMINDERS;
            case "LATE_ARRIVAL_ALERT":
                return CHANNEL_LATE_ALERTS;
            case "WEEKLY_SUMMARY":
                return CHANNEL_WEEKLY_SUMMARY;
            default:
                return CHANNEL_REMINDERS;
        }
    }

    /**
     * Get notification priority based on message type
     */
    private int getPriorityForMessageType(String messageType) {
        if (messageType == null) return NotificationCompat.PRIORITY_HIGH;

        switch (messageType) {
            case "LATE_ARRIVAL_ALERT":
                return NotificationCompat.PRIORITY_MAX;
            case "CLOCK_IN_REMINDER":
            case "CLOCK_OUT_REMINDER":
                return NotificationCompat.PRIORITY_HIGH;
            case "WEEKLY_SUMMARY":
                return NotificationCompat.PRIORITY_DEFAULT;
            default:
                return NotificationCompat.PRIORITY_HIGH;
        }
    }

    /**
     * Get appropriate icon for notification type
     */
    private int getNotificationIcon(String messageType) {
        if (messageType == null) return R.drawable.ic_notification_default;

        switch (messageType) {
            case "CLOCK_IN_REMINDER":
                return R.drawable.ic_clock_in;
            case "CLOCK_OUT_REMINDER":
                return R.drawable.ic_clock_out;
            case "LATE_ARRIVAL_ALERT":
                return R.drawable.ic_warning;
            case "WEEKLY_SUMMARY":
                return R.drawable.ic_summary;
            default:
                return R.drawable.ic_notification_default;
        }
    }

    /**
     * Add action buttons to notifications
     */
    private void addNotificationActions(NotificationCompat.Builder builder, String messageType, PendingIntent defaultIntent) {
        if (messageType == null) return;

        switch (messageType) {
            case "CLOCK_IN_REMINDER":
                Intent clockInIntent = new Intent(this, AttendanceActivity.class);
                clockInIntent.putExtra("quick_action", "clock_in");
                clockInIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent clockInPendingIntent = PendingIntent.getActivity(
                        this, 1001, clockInIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                builder.addAction(R.drawable.ic_clock_in, "Clock In Now", clockInPendingIntent);
                break;

            case "CLOCK_OUT_REMINDER":
                Intent clockOutIntent = new Intent(this, AttendanceActivity.class);
                clockOutIntent.putExtra("quick_action", "clock_out");
                clockOutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent clockOutPendingIntent = PendingIntent.getActivity(
                        this, 1002, clockOutIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                builder.addAction(R.drawable.ic_clock_out, "Clock Out Now", clockOutPendingIntent);
                break;

            case "LATE_ARRIVAL_ALERT":
                Intent urgentClockInIntent = new Intent(this, AttendanceActivity.class);
                urgentClockInIntent.putExtra("quick_action", "urgent_clock_in");
                urgentClockInIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent urgentPendingIntent = PendingIntent.getActivity(
                        this, 1003, urgentClockInIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                builder.addAction(R.drawable.ic_urgent, "Clock In Immediately", urgentPendingIntent);
                break;
        }
    }

    /**
     * Generate unique notification ID based on message type
     */
    private int generateNotificationId(String messageType) {
        if (messageType == null) return 1000;

        switch (messageType) {
            case "CLOCK_IN_REMINDER":
                return 1001;
            case "CLOCK_OUT_REMINDER":
                return 1002;
            case "LATE_ARRIVAL_ALERT":
                return 1003;
            case "WEEKLY_SUMMARY":
                return 1004;
            default:
                return 1000;
        }
    }

    /**
     * Handle data-only messages
     */
    private void handleDataMessage(Map<String, String> data) {
        String messageType = data.get("type");

        if ("BACKGROUND_SYNC".equals(messageType)) {
            // Trigger background sync of attendance data
            syncAttendanceData();
        } else if ("FORCE_LOGOUT".equals(messageType)) {
            // Force logout for security reasons
            forceLogout();
        }
    }

    /**
     * Sync attendance data in background
     */
    private void syncAttendanceData() {
        // Implementation for background sync
        Log.d(TAG, "Background sync triggered by FCM");
    }

    /**
     * Force logout for security reasons
     */
    private void forceLogout() {
        getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        Intent intent = new Intent(this, org.smart.attendance_beta.LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    /**
     * Initialize FCM for the current employee
     */
    public static void initializeFCM(Context context) {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();
                    Log.d(TAG, "FCM Registration Token: " + token);

                    // Send token to server
                    sendTokenToServer(context, token);
                });
    }

    /**
     * Send token to server (static method for use in activities)
     */
    private static void sendTokenToServer(Context context, String token) {
        String employeeDocId = context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)
                .getString("employee_doc_id", null);

        if (employeeDocId == null) {
            Log.w(TAG, "No employee doc ID found, token not sent to server");
            return;
        }

        FirebaseFunctions functions = FirebaseFunctions.getInstance();

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("employeeId", employeeDocId);

        functions.getHttpsCallable("updateFCMToken")
                .call(data)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "FCM token successfully sent to server");
                    } else {
                        Log.e(TAG, "Failed to send FCM token to server", task.getException());
                    }
                });
    }
}

// Enhanced EmployeeDashboardActivity.java - Add weekly metrics and FCM initialization
// Add these methods to your existing EmployeeDashboardActivity.java:

/**
 * ENHANCED: Load comprehensive weekly statistics
 */


/**
 * Initialize FCM when dashboard loads
 */


/**
 * Handle notification quick actions
 */


/**
 * Handle quick actions from notifications
 */
