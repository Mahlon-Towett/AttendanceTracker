// DeviceSecurityUtils.java - Device fingerprinting and session management
package org.smart.attendance_beta.utils;

import android.content.Context;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import java.security.MessageDigest;
import android.os.Build;

public class DeviceSecurityUtils {
    private static final String TAG = "DeviceSecurityUtils";

    public interface DeviceValidationCallback {
        void onValidationComplete(boolean isValid, String message);
        void onError(String error);
    }

    /**
     * Generate unique device fingerprint
     */
    public static String generateDeviceFingerprint(Context context) {
        try {
            StringBuilder fingerprint = new StringBuilder();

            // Device identifiers
            String androidId = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);

            // Device info
            fingerprint.append(Build.MANUFACTURER)
                    .append(Build.MODEL)
                    .append(Build.DEVICE)
                    .append(androidId)
                    .append(Build.SERIAL); // Requires permission in newer Android versions

            // Create hash of fingerprint
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprint.toString().getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString().substring(0, 32); // First 32 chars
        } catch (Exception e) {
            // Fallback fingerprint
            return Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID) + Build.MODEL;
        }
    }

    /**
     * Check if employee can clock in from this device
     */
    public static void validateDeviceForClockIn(Context context, String employeeDocId,
                                                DeviceValidationCallback callback) {
        String currentDeviceId = generateDeviceFingerprint(context);
        String currentTime = DateTimeUtils.getCurrentDate();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Check if employee is already clocked in from another device today
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", currentTime)
                .whereEqualTo("clockOutTime", null) // Still clocked in
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        // Employee is already clocked in, check device
                        String recordedDeviceId = task.getResult().getDocuments().get(0)
                                .getString("deviceFingerprint");

                        if (recordedDeviceId != null && !recordedDeviceId.equals(currentDeviceId)) {
                            callback.onValidationComplete(false,
                                    "You are already clocked in from another device. Please use the same device to clock out.");
                        } else {
                            callback.onValidationComplete(true, "Device validated");
                        }
                    } else {
                        // No active clock-in found, device is valid
                        callback.onValidationComplete(true, "Device validated");
                    }
                })
                .addOnFailureListener(e -> callback.onError("Error validating device: " + e.getMessage()));
    }

    /**
     * Store device session info with attendance record
     */
    public static Map<String, Object> createDeviceSessionData(Context context) {
        Map<String, Object> deviceData = new HashMap<>();

        deviceData.put("deviceFingerprint", generateDeviceFingerprint(context));
        deviceData.put("deviceModel", Build.MODEL);
        deviceData.put("deviceManufacturer", Build.MANUFACTURER);
        deviceData.put("androidVersion", Build.VERSION.RELEASE);
        deviceData.put("appVersion", getAppVersion(context));
        deviceData.put("sessionStartTime", System.currentTimeMillis());

        return deviceData;
    }

    /**
     * Check for suspicious device switching patterns
     */
    public static void checkDeviceSwitchingPatterns(String employeeDocId,
                                                    DeviceValidationCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get last 7 days of attendance records
        long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);

        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereGreaterThan("clockInTimestamp", new com.google.firebase.Timestamp(sevenDaysAgo / 1000, 0))
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Map<String, Integer> deviceUsage = new HashMap<>();
                        int totalRecords = 0;

                        for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult()) {
                            String deviceId = doc.getString("deviceFingerprint");
                            if (deviceId != null) {
                                deviceUsage.put(deviceId, deviceUsage.getOrDefault(deviceId, 0) + 1);
                                totalRecords++;
                            }
                        }

                        // Flag if using more than 2 different devices in a week
                        if (deviceUsage.size() > 2 && totalRecords > 3) {
                            callback.onValidationComplete(false,
                                    "⚠️ Multiple devices detected. Please use consistent device for attendance.");
                        } else {
                            callback.onValidationComplete(true, "Device pattern normal");
                        }
                    }
                })
                .addOnFailureListener(e -> callback.onError("Error checking device patterns: " + e.getMessage()));
    }

    private static String getAppVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}
