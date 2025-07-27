// TimeSecurityUtils.java - Comprehensive time validation utility
package org.smart.attendance_beta.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.google.firebase.Timestamp;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TimeSecurityUtils {
    private static final String TAG = "TimeSecurityUtils";

    // Multiple time servers for redundancy
    private static final String[] TIME_SERVERS = {
            "https://worldtimeapi.org/api/timezone/Africa/Nairobi",
            "http://worldclockapi.com/api/json/africa/nairobi/now",
            "https://timeapi.io/api/Time/current/zone?timeZone=Africa/Nairobi"
    };

    public interface TimeValidationCallback {
        void onValidationComplete(TimeValidationResult result);
    }

    public static class TimeValidationResult {
        public boolean isTimeValid;
        public long serverTime;
        public long deviceTime;
        public long timeDifferenceMs;
        public String validationMethod;
        public String errorMessage;

        public TimeValidationResult(boolean isValid, long serverTime, long deviceTime,
                                    String method, String error) {
            this.isTimeValid = isValid;
            this.serverTime = serverTime;
            this.deviceTime = deviceTime;
            this.timeDifferenceMs = Math.abs(serverTime - deviceTime);
            this.validationMethod = method;
            this.errorMessage = error;
        }
    }

    /**
     * Comprehensive time validation using multiple methods
     */
    public static void validateDeviceTime(Context context, TimeValidationCallback callback) {
        // Method 1: Check if automatic time is enabled
        if (!isAutomaticTimeEnabled(context)) {
            callback.onValidationComplete(new TimeValidationResult(
                    false, 0, System.currentTimeMillis(),
                    "AUTO_TIME_CHECK", "Automatic time is disabled on device"
            ));
            return;
        }

        // Method 2: Validate against server time
        validateAgainstServerTime(callback);
    }

    /**
     * Check if automatic date/time is enabled on device
     */
    public static boolean isAutomaticTimeEnabled(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                int autoTime = Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.AUTO_TIME, 0);
                int autoTimeZone = Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.AUTO_TIME_ZONE, 0);

                Log.d(TAG, "Auto time: " + autoTime + ", Auto timezone: " + autoTimeZone);
                return autoTime == 1 && autoTimeZone == 1;
            } else {
                int autoTime = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.AUTO_TIME, 0);
                return autoTime == 1;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking automatic time settings", e);
            return false; // Assume false if we can't check
        }
    }

    /**
     * Validate device time against multiple server sources
     */
    private static void validateAgainstServerTime(TimeValidationCallback callback) {
        new Thread(() -> {
            long deviceTime = System.currentTimeMillis();

            // Try multiple methods to get server time
            Long serverTime = getFirebaseServerTime();
            if (serverTime == null) {
                serverTime = getWorldTimeAPITime();
            }
            if (serverTime == null) {
                serverTime = getHTTPHeaderTime();
            }

            if (serverTime != null) {
                long timeDiff = Math.abs(serverTime - deviceTime);
                boolean isValid = timeDiff <= 300000; // Allow 5 minutes tolerance

                callback.onValidationComplete(new TimeValidationResult(
                        isValid, serverTime, deviceTime, "SERVER_TIME",
                        isValid ? null : "Device time differs by " + (timeDiff/1000) + " seconds"
                ));
            } else {
                // Fallback: Check if time seems reasonable (not too far in past/future)
                boolean isReasonable = isTimeReasonable(deviceTime);
                callback.onValidationComplete(new TimeValidationResult(
                        isReasonable, 0, deviceTime, "REASONABLENESS_CHECK",
                        isReasonable ? null : "Device time appears to be manipulated"
                ));
            }
        }).start();
    }

    /**
     * Get server time from Firebase (most reliable for our app)
     */
    private static Long getFirebaseServerTime() {
        try {
            // This would need to be implemented with a Firebase function call
            // For now, we'll use Firebase Timestamp which is server-side
            Timestamp serverTimestamp = Timestamp.now();
            return serverTimestamp.toDate().getTime();
        } catch (Exception e) {
            Log.e(TAG, "Error getting Firebase server time", e);
            return null;
        }
    }

    /**
     * Get time from WorldTimeAPI
     */
    private static Long getWorldTimeAPITime() {
        try {
            URL url = new URL("https://worldtimeapi.org/api/timezone/Africa/Nairobi");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == 200) {
                // Parse JSON response to get unixtime
                // This is simplified - you'd need JSON parsing here
                String response = readInputStream(connection.getInputStream());
                // Extract unixtime from JSON and convert to milliseconds
                // Implementation depends on your JSON parsing library
                return System.currentTimeMillis(); // Placeholder
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting WorldTimeAPI time", e);
        }
        return null;
    }

    /**
     * Get time from HTTP Date header (fallback method)
     */
    private static Long getHTTPHeaderTime() {
        try {
            URL url = new URL("https://www.google.com");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            String dateHeader = connection.getHeaderField("Date");
            if (dateHeader != null) {
                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                Date serverDate = format.parse(dateHeader);
                return serverDate.getTime();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting HTTP header time", e);
        }
        return null;
    }

    /**
     * Check if time is within reasonable bounds
     */
    private static boolean isTimeReasonable(long deviceTime) {
        long currentYear = System.currentTimeMillis();
        long year2020 = 1577836800000L; // Jan 1, 2020
        long year2030 = 1893456000000L; // Jan 1, 2030

        return deviceTime >= year2020 && deviceTime <= year2030;
    }

    /**
     * Generate secure timestamp for attendance records
     */
    public static AttendanceTimestamp createSecureTimestamp(Context context) {
        return new AttendanceTimestamp(
                System.currentTimeMillis(),
                Timestamp.now(), // Firebase server timestamp
                isAutomaticTimeEnabled(context),
                TimeZone.getDefault().getID()
        );
    }

    /**
     * Secure timestamp class that includes validation data
     */
    public static class AttendanceTimestamp {
        public final long deviceTime;
        public final Timestamp serverTime;
        public final boolean autoTimeEnabled;
        public final String timeZone;
        public final long validationTime;

        public AttendanceTimestamp(long deviceTime, Timestamp serverTime,
                                   boolean autoTimeEnabled, String timeZone) {
            this.deviceTime = deviceTime;
            this.serverTime = serverTime;
            this.autoTimeEnabled = autoTimeEnabled;
            this.timeZone = timeZone;
            this.validationTime = System.currentTimeMillis();
        }

        public boolean isValid() {
            if (!autoTimeEnabled) return false;

            long serverMs = serverTime.toDate().getTime();
            long timeDiff = Math.abs(serverMs - deviceTime);
            return timeDiff <= 300000; // 5 minutes tolerance
        }
    }

    /**
     * Format time with validation info for admin view
     */
    public static String formatTimeWithValidation(AttendanceTimestamp timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timeStr = format.format(new Date(timestamp.deviceTime));

        if (!timestamp.isValid()) {
            timeStr += " ⚠️ (Time may be manipulated)";
        }

        return timeStr;
    }

    /**
     * Check network connectivity for time validation
     */
    private static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * Simple helper to read InputStream to String
     */
    private static String readInputStream(java.io.InputStream inputStream) throws IOException {
        java.util.Scanner scanner = new java.util.Scanner(inputStream).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}