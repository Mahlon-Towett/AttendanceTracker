// DeviceSessionManager.java - Manages device sessions and prevents multi-device abuse
package org.smart.attendance_beta.utils;

import android.content.Context;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

public class DeviceSessionManager {
    private static final String TAG = "DeviceSessionManager";
    private static final long SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000; // 24 hours

    public interface SessionValidationCallback {
        void onSessionValid(String sessionId);
        void onDeviceConflict(DeviceConflict conflict);
        void onSessionInvalid(String reason);
        void onError(String error);
    }

    public interface SessionCreationCallback {
        void onSessionCreated(String sessionId);
        void onDeviceConflict(DeviceConflict conflict);
        void onError(String error);
    }

    public interface SessionTerminationCallback {
        void onSessionTerminated();
        void onError(String error);
    }

    /**
     * Validate if current device can create or continue an attendance session
     */
    public static void validateDeviceSession(Context context,
                                             String employeeDocId,
                                             String date,
                                             SessionValidationCallback callback) {
        String currentDeviceId = DeviceSecurityUtils.getDeviceId(context);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Log.d(TAG, "Validating session for employee: " + employeeDocId + ", device: " + currentDeviceId.substring(0, 8));

        // Check for active sessions on any device for this employee and date
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", date)
                .whereEqualTo("sessionActive", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // No active session found - device is clear to proceed
                            callback.onSessionValid(null);
                        } else {
                            // Active session found - check if it's on the same device
                            DocumentSnapshot activeSession = task.getResult().getDocuments().get(0);
                            String sessionDeviceId = activeSession.getString("deviceId");
                            String sessionId = activeSession.getId();

                            if (currentDeviceId.equals(sessionDeviceId)) {
                                // Same device - session is valid
                                callback.onSessionValid(sessionId);
                            } else {
                                // Different device - conflict detected
                                DeviceConflict conflict = createDeviceConflict(
                                        activeSession, currentDeviceId, context);
                                callback.onDeviceConflict(conflict);
                            }
                        }
                    } else {
                        callback.onError("Failed to validate session: " + task.getException().getMessage());
                    }
                });
    }

    /**
     * Create a new attendance session with device tracking
     */
    public static void createAttendanceSession(Context context,
                                               String employeeDocId,
                                               String pfNumber,
                                               String employeeName,
                                               String date,
                                               double latitude,
                                               double longitude,
                                               SessionCreationCallback callback) {
        String deviceId = DeviceSecurityUtils.getDeviceId(context);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // First check if there's already an active session
        validateDeviceSession(context, employeeDocId, date, new SessionValidationCallback() {
            @Override
            public void onSessionValid(String existingSessionId) {
                if (existingSessionId != null) {
                    // Session already exists for this device
                    callback.onSessionCreated(existingSessionId);
                } else {
                    // Create new session
                    createNewSession(context, employeeDocId, pfNumber, employeeName,
                            date, latitude, longitude, callback);
                }
            }

            @Override
            public void onDeviceConflict(DeviceConflict conflict) {
                callback.onDeviceConflict(conflict);
            }

            @Override
            public void onSessionInvalid(String reason) {
                callback.onError("Session invalid: " + reason);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Create a new attendance session record
     */
    private static void createNewSession(Context context,
                                         String employeeDocId,
                                         String pfNumber,
                                         String employeeName,
                                         String date,
                                         double latitude,
                                         double longitude,
                                         SessionCreationCallback callback) {
        String deviceId = DeviceSecurityUtils.getDeviceId(context);
        DeviceSecurityUtils.DeviceFingerprint fingerprint =
                DeviceSecurityUtils.getDeviceFingerprint(context);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String currentTime = DateTimeUtils.getCurrentTime();
        Timestamp now = Timestamp.now();

        // Check if employee is late
        boolean isLate = DateTimeUtils.isLateArrival(currentTime, "08:00");
        int lateMinutes = isLate ? DateTimeUtils.calculateLateMinutes(currentTime, "08:00") : 0;

        Map<String, Object> sessionData = new HashMap<>();

        // Basic attendance data
        sessionData.put("employeeDocId", employeeDocId);
        sessionData.put("pfNumber", pfNumber);
        sessionData.put("employeeName", employeeName);
        sessionData.put("date", date);
        sessionData.put("clockInTime", currentTime);
        sessionData.put("clockInTimestamp", now);
        sessionData.put("clockInLatitude", latitude);
        sessionData.put("clockInLongitude", longitude);
        sessionData.put("locationName", "Company Office");
        sessionData.put("status", isLate ? "Late" : "Present");
        sessionData.put("totalHours", 0.0);
        sessionData.put("isLate", isLate);
        sessionData.put("lateMinutes", lateMinutes);
        sessionData.put("createdAt", now);

        // Device session management
        sessionData.put("deviceId", deviceId);
        sessionData.put("deviceModel", fingerprint.model);
        sessionData.put("deviceManufacturer", fingerprint.manufacturer);
        sessionData.put("deviceBrand", fingerprint.brand);
        sessionData.put("deviceOSVersion", fingerprint.osVersion);
        sessionData.put("deviceHardware", fingerprint.hardware);
        sessionData.put("sessionActive", true);
        sessionData.put("sessionStartTime", now);
        sessionData.put("lastHeartbeat", now);
        sessionData.put("heartbeatCount", 0);
        sessionData.put("hasDeviceConflict", false);

        // Security data
        DeviceSecurityUtils.SecurityRiskAssessment risk =
                DeviceSecurityUtils.getSecurityRisk(context);
        sessionData.put("securityRiskLevel", risk.riskLevel);
        sessionData.put("securityRiskScore", risk.riskScore);
        sessionData.put("securityRiskReasons", risk.riskReasons);
        sessionData.put("deviceRooted", DeviceSecurityUtils.isDeviceRooted());
        sessionData.put("developerModeEnabled", DeviceSecurityUtils.isDeveloperModeEnabled(context));
        sessionData.put("usbDebuggingEnabled", DeviceSecurityUtils.isUSBDebuggingEnabled(context));
        sessionData.put("isEmulator", DeviceSecurityUtils.isEmulator());

        db.collection("attendance")
                .add(sessionData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Session created successfully: " + documentReference.getId());
                    callback.onSessionCreated(documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create session", e);
                    callback.onError("Failed to create session: " + e.getMessage());
                });
    }

    /**
     * Update session heartbeat to show device is still active
     */
    public static void updateSessionHeartbeat(String sessionId) {
        if (sessionId == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastHeartbeat", Timestamp.now());
        updates.put("heartbeatCount", com.google.firebase.firestore.FieldValue.increment(1));

        db.collection("attendance").document(sessionId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Heartbeat updated for session: " + sessionId);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to update heartbeat for session: " + sessionId, e);
                });
    }

    /**
     * Terminate an active session
     */
    public static void terminateSession(String sessionId,
                                        double clockOutLatitude,
                                        double clockOutLongitude,
                                        String clockOutReason,
                                        SessionTerminationCallback callback) {
        if (sessionId == null) {
            callback.onError("Invalid session ID");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String currentTime = DateTimeUtils.getCurrentTime();
        Timestamp now = Timestamp.now();

        // First get session data to calculate hours worked
        db.collection("attendance").document(sessionId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        DocumentSnapshot session = task.getResult();
                        String clockInTime = session.getString("clockInTime");

                        double hoursWorked = 0;
                        if (clockInTime != null) {
                            hoursWorked = DateTimeUtils.calculateHoursWorked(clockInTime, currentTime);
                        }

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("clockOutTime", currentTime);
                        updates.put("clockOutTimestamp", now);
                        updates.put("clockOutLatitude", clockOutLatitude);
                        updates.put("clockOutLongitude", clockOutLongitude);
                        updates.put("totalHours", hoursWorked);
                        updates.put("sessionActive", false);
                        updates.put("sessionEndTime", now);

                        if (clockOutReason != null && !clockOutReason.isEmpty()) {
                            updates.put("clockOutReason", clockOutReason);
                            updates.put("isEarlyClockOut", true);
                        }

                        // Calculate session duration
                        Timestamp sessionStart = session.getTimestamp("sessionStartTime");
                        if (sessionStart != null) {
                            long sessionDuration = now.toDate().getTime() - sessionStart.toDate().getTime();
                            updates.put("sessionDurationMs", sessionDuration);
                        }

                        db.collection("attendance").document(sessionId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Session terminated successfully: " + sessionId);
                                    callback.onSessionTerminated();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to terminate session", e);
                                    callback.onError("Failed to terminate session: " + e.getMessage());
                                });
                    } else {
                        callback.onError("Session not found");
                    }
                });
    }

    /**
     * Force terminate a session (admin action)
     */
    public static void forceTerminateSession(String sessionId,
                                             String reason,
                                             String adminId,
                                             SessionTerminationCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Timestamp now = Timestamp.now();

        Map<String, Object> updates = new HashMap<>();
        updates.put("sessionActive", false);
        updates.put("sessionTerminatedBy", "ADMIN");
        updates.put("sessionTerminationReason", reason);
        updates.put("terminatedByAdminId", adminId);
        updates.put("forcedTerminationTime", now);

        db.collection("attendance").document(sessionId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Session force-terminated by admin: " + sessionId);
                    callback.onSessionTerminated();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to force-terminate session", e);
                    callback.onError("Failed to terminate session: " + e.getMessage());
                });
    }

    /**
     * Report device conflict to security alerts collection
     */
    public static void reportDeviceConflict(DeviceConflict conflict) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> conflictReport = new HashMap<>();
        conflictReport.put("type", "DEVICE_CONFLICT");
        conflictReport.put("severity", "HIGH");
        conflictReport.put("employeeDocId", conflict.employeeDocId);
        conflictReport.put("employeeName", conflict.employeeName);
        conflictReport.put("pfNumber", conflict.pfNumber);
        conflictReport.put("originalDeviceId", conflict.originalDeviceId);
        conflictReport.put("originalDeviceInfo", conflict.originalDeviceInfo);
        conflictReport.put("attemptingDeviceId", conflict.attemptingDeviceId);
        conflictReport.put("attemptingDeviceInfo", conflict.attemptingDeviceInfo);
        conflictReport.put("originalSessionId", conflict.originalSessionId);
        conflictReport.put("conflictTime", Timestamp.now());
        conflictReport.put("date", conflict.date);
        conflictReport.put("resolved", false);
        conflictReport.put("actionRequired", true);

        db.collection("security_alerts")
                .add(conflictReport)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Device conflict reported: " + documentReference.getId());

                    // Update original session with conflict info
                    Map<String, Object> sessionUpdate = new HashMap<>();
                    sessionUpdate.put("hasDeviceConflict", true);
                    sessionUpdate.put("conflictDeviceId", conflict.attemptingDeviceId);
                    sessionUpdate.put("conflictTime", Timestamp.now());
                    sessionUpdate.put("securityAlertId", documentReference.getId());

                    db.collection("attendance").document(conflict.originalSessionId)
                            .update(sessionUpdate);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to report device conflict", e);
                });
    }

    /**
     * Clean up expired sessions (run periodically)
     */
    public static void cleanupExpiredSessions() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Timestamp cutoffTime = new Timestamp(System.currentTimeMillis() - SESSION_TIMEOUT_MS, 0);

        db.collection("attendance")
                .whereEqualTo("sessionActive", true)
                .whereLessThan("lastHeartbeat", cutoffTime)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        WriteBatch batch = db.batch();
                        int expiredCount = 0;

                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            batch.update(doc.getReference(), "sessionActive", false);
                            batch.update(doc.getReference(), "sessionExpired", true);
                            batch.update(doc.getReference(), "sessionExpiredTime", Timestamp.now());
                            expiredCount++;
                        }

                        if (expiredCount > 0) {
                            int finalExpiredCount = expiredCount;
                            batch.commit()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Cleaned up " + finalExpiredCount + " expired sessions");
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to cleanup expired sessions", e);
                                    });
                        }
                    }
                });
    }

    /**
     * Create device conflict object
     */
    private static DeviceConflict createDeviceConflict(DocumentSnapshot activeSession,
                                                       String attemptingDeviceId,
                                                       Context context) {
        DeviceSecurityUtils.DeviceFingerprint attemptingDevice =
                DeviceSecurityUtils.getDeviceFingerprint(context);

        return new DeviceConflict(
                activeSession.getString("employeeDocId"),
                activeSession.getString("employeeName"),
                activeSession.getString("pfNumber"),
                activeSession.getString("deviceId"),
                activeSession.getString("deviceManufacturer") + " " + activeSession.getString("deviceModel"),
                attemptingDeviceId,
                attemptingDevice.manufacturer + " " + attemptingDevice.model,
                activeSession.getId(),
                activeSession.getString("date"),
                activeSession.getTimestamp("sessionStartTime")
        );
    }

    /**
     * Device conflict data class
     */
    public static class DeviceConflict {
        public final String employeeDocId;
        public final String employeeName;
        public final String pfNumber;
        public final String originalDeviceId;
        public final String originalDeviceInfo;
        public final String attemptingDeviceId;
        public final String attemptingDeviceInfo;
        public final String originalSessionId;
        public final String date;
        public final Timestamp originalSessionStart;

        public DeviceConflict(String employeeDocId, String employeeName, String pfNumber,
                              String originalDeviceId, String originalDeviceInfo,
                              String attemptingDeviceId, String attemptingDeviceInfo,
                              String originalSessionId, String date, Timestamp originalSessionStart) {
            this.employeeDocId = employeeDocId;
            this.employeeName = employeeName;
            this.pfNumber = pfNumber;
            this.originalDeviceId = originalDeviceId;
            this.originalDeviceInfo = originalDeviceInfo;
            this.attemptingDeviceId = attemptingDeviceId;
            this.attemptingDeviceInfo = attemptingDeviceInfo;
            this.originalSessionId = originalSessionId;
            this.date = date;
            this.originalSessionStart = originalSessionStart;
        }

        public String getFormattedMessage() {
            return String.format(
                    "Device Conflict Detected!\n\n" +
                            "Employee: %s (%s)\n" +
                            "Original Device: %s\n" +
                            "Attempting Device: %s\n\n" +
                            "Only one device can be used per employee per day.\n" +
                            "Please contact your administrator if this is a legitimate device change.",
                    employeeName, pfNumber,
                    originalDeviceInfo,
                    attemptingDeviceInfo
            );
        }
    }
}