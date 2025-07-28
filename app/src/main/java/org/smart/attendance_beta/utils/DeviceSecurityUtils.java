// DeviceSecurityUtils.java - Complete device identification and security system
package org.smart.attendance_beta.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class DeviceSecurityUtils {
    private static final String TAG = "DeviceSecurityUtils";
    private static final String PREF_DEVICE_ID = "device_unique_id";
    private static final String PREF_DEVICE_FINGERPRINT = "device_fingerprint";

    /**
     * Get a unique device identifier using multiple device characteristics
     * This creates a persistent identifier that survives app reinstalls
     */
    public static String getDeviceId(Context context) {
        // First check if we have a stored device ID
        String storedId = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
                .getString(PREF_DEVICE_ID, null);

        if (storedId != null && !storedId.isEmpty()) {
            return storedId;
        }

        // Generate new device ID based on multiple device characteristics
        String deviceId = generateDeviceId(context);

        // Store the generated ID for future use
        context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_DEVICE_ID, deviceId)
                .apply();

        Log.d(TAG, "Generated new device ID: " + deviceId.substring(0, 8) + "...");
        return deviceId;
    }

    /**
     * Generate device ID using multiple device characteristics
     */
    @SuppressLint("HardwareIds")
    private static String generateDeviceId(Context context) {
        StringBuilder deviceInfo = new StringBuilder();

        try {
            // Android ID (changes on factory reset)
            String androidId = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.equals("9774d56d682e549c")) { // Known invalid ID
                deviceInfo.append(androidId);
            }

            // Build information
            deviceInfo.append(Build.MANUFACTURER);
            deviceInfo.append(Build.MODEL);
            deviceInfo.append(Build.DEVICE);
            deviceInfo.append(Build.PRODUCT);
            deviceInfo.append(Build.BRAND);

            // Hardware serial (if available and permitted)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    deviceInfo.append(Build.getSerial());
                } catch (SecurityException e) {
                    // No permission to read serial, use Build.SERIAL fallback
                    deviceInfo.append(Build.SERIAL);
                }
            } else {
                deviceInfo.append(Build.SERIAL);
            }

            // Display metrics as additional entropy
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            deviceInfo.append(metrics.widthPixels);
            deviceInfo.append(metrics.heightPixels);
            deviceInfo.append(metrics.densityDpi);

            // CPU info
            deviceInfo.append(Build.HARDWARE);
            deviceInfo.append(Build.BOARD);

        } catch (Exception e) {
            Log.e(TAG, "Error generating device ID", e);
            // Fallback to random UUID if all else fails
            return UUID.randomUUID().toString();
        }

        // Hash the combined device information
        return hashString(deviceInfo.toString());
    }

    /**
     * Hash a string using SHA-256
     */
    private static String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 not available", e);
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Get device model for display purposes
     */
    public static String getDeviceModel() {
        return Build.MODEL;
    }

    /**
     * Get device manufacturer
     */
    public static String getDeviceManufacturer() {
        return Build.MANUFACTURER;
    }

    /**
     * Get device OS version
     */
    public static String getDeviceOSVersion() {
        return Build.VERSION.RELEASE;
    }

    /**
     * Get device brand
     */
    public static String getDeviceBrand() {
        return Build.BRAND;
    }

    /**
     * Get comprehensive device fingerprint for security analysis
     */
    public static DeviceFingerprint getDeviceFingerprint(Context context) {
        return new DeviceFingerprint(
                getDeviceId(context),
                getDeviceModel(),
                getDeviceManufacturer(),
                getDeviceBrand(),
                getDeviceOSVersion(),
                Build.HARDWARE,
                Build.PRODUCT,
                System.currentTimeMillis()
        );
    }

    /**
     * Validate if device characteristics match stored fingerprint
     */
    public static boolean validateDeviceFingerprint(Context context, DeviceFingerprint storedFingerprint) {
        if (storedFingerprint == null) return false;

        DeviceFingerprint currentFingerprint = getDeviceFingerprint(context);

        // Check critical device characteristics
        return storedFingerprint.deviceId.equals(currentFingerprint.deviceId) &&
                storedFingerprint.model.equals(currentFingerprint.model) &&
                storedFingerprint.manufacturer.equals(currentFingerprint.manufacturer) &&
                storedFingerprint.hardware.equals(currentFingerprint.hardware);
    }

    /**
     * Check if device appears to be rooted (basic check)
     */
    public static boolean isDeviceRooted() {
        try {
            // Check for common root binaries
            String[] rootPaths = {
                    "/system/app/Superuser.apk",
                    "/sbin/su",
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/data/local/xbin/su",
                    "/data/local/bin/su",
                    "/system/sd/xbin/su",
                    "/system/bin/failsafe/su",
                    "/data/local/su"
            };

            for (String path : rootPaths) {
                if (new java.io.File(path).exists()) {
                    return true;
                }
            }

            // Check for root management apps
            String[] rootApps = {
                    "com.noshufou.android.su",
                    "com.thirdparty.superuser",
                    "eu.chainfire.supersu",
                    "com.koushikdutta.superuser",
                    "com.zachspong.temprootremovejb",
                    "com.ramdroid.appquarantine"
            };

            android.content.pm.PackageManager pm = null;
            if (pm != null) {
                for (String app : rootApps) {
                    try {
                        pm.getPackageInfo(app, 0);
                        return true;
                    } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                        // App not found, continue checking
                    }
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "Error checking root status", e);
        }

        return false;
    }

    /**
     * Check if device is in developer mode
     */
    public static boolean isDeveloperModeEnabled(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
    }

    /**
     * Check if USB debugging is enabled
     */
    public static boolean isUSBDebuggingEnabled(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.ADB_ENABLED, 0) == 1;
    }

    /**
     * Get security risk assessment for the device
     */
    public static SecurityRiskAssessment getSecurityRisk(Context context) {
        boolean isRooted = isDeviceRooted();
        boolean isDeveloperMode = isDeveloperModeEnabled(context);
        boolean isUSBDebugging = isUSBDebuggingEnabled(context);
        boolean isEmulator = isEmulator();

        int riskScore = 0;
        StringBuilder riskReasons = new StringBuilder();

        if (isRooted) {
            riskScore += 30;
            riskReasons.append("Device is rooted; ");
        }
        if (isDeveloperMode) {
            riskScore += 15;
            riskReasons.append("Developer mode enabled; ");
        }
        if (isUSBDebugging) {
            riskScore += 20;
            riskReasons.append("USB debugging enabled; ");
        }
        if (isEmulator) {
            riskScore += 25;
            riskReasons.append("Running on emulator; ");
        }

        String riskLevel;
        if (riskScore >= 50) {
            riskLevel = "HIGH";
        } else if (riskScore >= 25) {
            riskLevel = "MEDIUM";
        } else if (riskScore > 0) {
            riskLevel = "LOW";
        } else {
            riskLevel = "MINIMAL";
        }

        return new SecurityRiskAssessment(riskLevel, riskScore, riskReasons.toString());
    }

    /**
     * Check if running on emulator
     */
    public static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk".equals(Build.PRODUCT);
    }

    /**
     * Device fingerprint data class
     */
    public static class DeviceFingerprint {
        public final String deviceId;
        public final String model;
        public final String manufacturer;
        public final String brand;
        public final String osVersion;
        public final String hardware;
        public final String product;
        public final long timestamp;

        public DeviceFingerprint(String deviceId, String model, String manufacturer,
                                 String brand, String osVersion, String hardware,
                                 String product, long timestamp) {
            this.deviceId = deviceId;
            this.model = model;
            this.manufacturer = manufacturer;
            this.brand = brand;
            this.osVersion = osVersion;
            this.hardware = hardware;
            this.product = product;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("%s %s (%s) - %s", manufacturer, model, brand, deviceId.substring(0, 8));
        }
    }

    /**
     * Security risk assessment data class
     */
    public static class SecurityRiskAssessment {
        public final String riskLevel;
        public final int riskScore;
        public final String riskReasons;

        public SecurityRiskAssessment(String riskLevel, int riskScore, String riskReasons) {
            this.riskLevel = riskLevel;
            this.riskScore = riskScore;
            this.riskReasons = riskReasons;
        }

        public boolean isHighRisk() {
            return "HIGH".equals(riskLevel);
        }

        public boolean shouldBlockAccess() {
            return riskScore >= 50; // Block high-risk devices
        }
    }
}