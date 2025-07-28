// Enhanced AttendanceActivity.java with device security and session management
package org.smart.attendance_beta;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import org.smart.attendance_beta.models.AttendanceRecord;
import org.smart.attendance_beta.utils.DateTimeUtils;
import org.smart.attendance_beta.utils.LocationUtils;
import org.smart.attendance_beta.utils.TimeSecurityUtils;
import org.smart.attendance_beta.utils.DeviceSecurityUtils;

import java.util.HashMap;
import java.util.Map;

public class AttendanceActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI Components
    private TextView tvCurrentTime, tvLocationStatus, tvDistanceFromOffice;
    private TextView tvTodayStatus, tvClockInTime, tvClockOutTime, tvHoursWorked;
    private TextView tvTimeValidationStatus, tvDeviceStatus;
    private Button btnClockIn, btnClockOut;
    private ProgressBar progressBar;
    private CardView cvLocationInfo, cvAttendanceInfo, cvTimeValidation, cvDeviceValidation;

    // Firebase
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;

    // Location and Employee Data
    private double companyLatitude = -1.2921;
    private double companyLongitude = 36.8219;
    private int companyRadius = 200;
    private String employeeDocId;
    private String pfNumber;
    private String employeeName;
    private boolean isClockedIn = false;
    private String todayAttendanceDocId = null;
    private String workStartTime = "08:00";
    private String workEndTime = "17:00";

    // Security
    private boolean isTimeValid = false;
    private boolean isDeviceValid = false;
    private String deviceId;
    private String activeSessionId = null;
    private TimeSecurityUtils.TimeValidationResult lastTimeValidation;

    // Handlers
    private Handler locationUpdateHandler = new Handler();
    private Runnable locationUpdateRunnable;
    private Handler timeUpdateHandler = new Handler();
    private Runnable timeUpdateRunnable;
    private Handler sessionValidationHandler = new Handler();
    private Runnable sessionValidationRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        // Initialize security
        deviceId = DeviceSecurityUtils.getDeviceId(this);

        // Setup toolbar
        setupToolbar();

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Get employee data
        getEmployeeData();

        // Initialize views
        initViews();

        // Security validations
        validateDeviceTime();
        validateDeviceSession();

        // Load data
        loadCompanyLocation();
        loadTodayAttendance();

        // Start updates
        startTimeUpdates();
        startLocationUpdates();
        startSessionValidation();

        // Setup click listeners
        setupClickListeners();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("⏰ Attendance");
            }
        }
    }

    /**
     * SECURITY: Validate device session to prevent multi-device abuse
     */
    private void validateDeviceSession() {
        if (employeeDocId == null) return;

        String today = DateTimeUtils.getCurrentDate();

        // Check if employee has an active session on a different device
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", today)
                .whereEqualTo("sessionActive", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot activeSession = task.getResult().getDocuments().get(0);
                        String sessionDeviceId = activeSession.getString("deviceId");
                        String sessionId = activeSession.getId();

                        if (sessionDeviceId != null && !sessionDeviceId.equals(deviceId)) {
                            // Another device has an active session
                            handleDeviceConflict(sessionId, sessionDeviceId);
                        } else if (sessionDeviceId != null && sessionDeviceId.equals(deviceId)) {
                            // This device has the active session
                            activeSessionId = sessionId;
                            isDeviceValid = true;
                            updateDeviceValidationUI(true, "Device authorized ✅");
                        } else {
                            // No device conflict, but check if this device can create a session
                            isDeviceValid = true;
                            updateDeviceValidationUI(true, "Ready to clock in ✅");
                        }
                    } else {
                        // No active session found
                        isDeviceValid = true;
                        updateDeviceValidationUI(true, "Ready to clock in ✅");
                    }
                    updateButtonStates();
                });
    }

    /**
     * Handle device conflict when employee tries to use different device
     */
    private void handleDeviceConflict(String conflictSessionId, String conflictDeviceId) {
        isDeviceValid = false;
        updateDeviceValidationUI(false, "Another device is active ❌");

        new AlertDialog.Builder(this)
                .setTitle("🚫 Device Conflict Detected")
                .setMessage("You are already clocked in from another device today.\n\n" +
                        "Active Device ID: " + conflictDeviceId.substring(0, 8) + "...\n" +
                        "Current Device ID: " + deviceId.substring(0, 8) + "...\n\n" +
                        "To prevent attendance fraud:\n" +
                        "• Only one device can be used per day\n" +
                        "• Clock out from the original device first\n" +
                        "• Contact admin if you lost your device")
                .setPositiveButton("Contact Admin", (dialog, which) -> {
                    // Create admin notification about device conflict
                    reportDeviceConflict(conflictSessionId, conflictDeviceId);
                    Toast.makeText(this, "Admin has been notified about device conflict",
                            Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Exit", (dialog, which) -> {
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Report device conflict to admin
     */
    private void reportDeviceConflict(String conflictSessionId, String conflictDeviceId) {
        Map<String, Object> conflictReport = new HashMap<>();
        conflictReport.put("type", "DEVICE_CONFLICT");
        conflictReport.put("employeeDocId", employeeDocId);
        conflictReport.put("employeeName", employeeName);
        conflictReport.put("pfNumber", pfNumber);
        conflictReport.put("originalDeviceId", conflictDeviceId);
        conflictReport.put("attemptedDeviceId", deviceId);
        conflictReport.put("originalSessionId", conflictSessionId);
        conflictReport.put("attemptTime", com.google.firebase.Timestamp.now());
        conflictReport.put("date", DateTimeUtils.getCurrentDate());
        conflictReport.put("resolved", false);
        conflictReport.put("severity", "HIGH");

        db.collection("security_alerts")
                .add(conflictReport)
                .addOnSuccessListener(documentReference -> {
                    // Also update the original session with conflict info
                    Map<String, Object> sessionUpdate = new HashMap<>();
                    sessionUpdate.put("hasDeviceConflict", true);
                    sessionUpdate.put("conflictDeviceId", deviceId);
                    sessionUpdate.put("conflictTime", com.google.firebase.Timestamp.now());
                    sessionUpdate.put("securityAlertId", documentReference.getId());

                    db.collection("attendance").document(conflictSessionId)
                            .update(sessionUpdate);
                });
    }

    /**
     * Start periodic session validation to detect session hijacking
     */
    private void startSessionValidation() {
        sessionValidationRunnable = new Runnable() {
            @Override
            public void run() {
                if (activeSessionId != null && isClockedIn) {
                    validateActiveSession();
                }
                sessionValidationHandler.postDelayed(this, 30000); // Every 30 seconds
            }
        };
        sessionValidationHandler.post(sessionValidationRunnable);
    }

    /**
     * Validate that the current session is still valid
     */
    private void validateActiveSession() {
        if (activeSessionId == null) return;

        db.collection("attendance").document(activeSessionId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot session = task.getResult();
                        if (session.exists()) {
                            Boolean sessionActive = session.getBoolean("sessionActive");
                            String sessionDeviceId = session.getString("deviceId");
                            Boolean hasConflict = session.getBoolean("hasDeviceConflict");

                            if (sessionActive == null || !sessionActive ||
                                    !deviceId.equals(sessionDeviceId) ||
                                    (hasConflict != null && hasConflict)) {

                                // Session has been invalidated
                                handleSessionInvalidated();
                            } else {
                                // Update session heartbeat
                                updateSessionHeartbeat();
                            }
                        } else {
                            handleSessionInvalidated();
                        }
                    }
                });
    }

    /**
     * Handle when session is invalidated (possibly by admin or device conflict)
     */
    private void handleSessionInvalidated() {
        isClockedIn = false;
        isDeviceValid = false;
        activeSessionId = null;

        updateDeviceValidationUI(false, "Session invalidated ❌");
        updateButtonStates();

        new AlertDialog.Builder(this)
                .setTitle("⚠️ Session Invalidated")
                .setMessage("Your attendance session has been invalidated. This may be due to:\n\n" +
                        "• Admin intervention\n" +
                        "• Security violation detected\n" +
                        "• System maintenance\n\n" +
                        "Please contact your supervisor.")
                .setPositiveButton("OK", (dialog, which) -> {
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Update session heartbeat to show device is still active
     */
    private void updateSessionHeartbeat() {
        if (activeSessionId == null) return;

        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("lastHeartbeat", com.google.firebase.Timestamp.now());
        heartbeat.put("heartbeatDeviceId", deviceId);

        db.collection("attendance").document(activeSessionId)
                .update(heartbeat);
    }

    /**
     * Update device validation UI
     */
    private void updateDeviceValidationUI(boolean isValid, String message) {
        if (tvDeviceStatus == null) return;

        tvDeviceStatus.setText(message);
        if (isValid) {
            tvDeviceStatus.setTextColor(getResources().getColor(R.color.green_600));
            if (cvDeviceValidation != null) {
                cvDeviceValidation.setCardBackgroundColor(getResources().getColor(R.color.green_50));
            }
        } else {
            tvDeviceStatus.setTextColor(getResources().getColor(R.color.red_600));
            if (cvDeviceValidation != null) {
                cvDeviceValidation.setCardBackgroundColor(getResources().getColor(R.color.red_50));
            }
        }
    }

    /**
     * SECURITY: Validate device time against server time
     */
    private void validateDeviceTime() {
        TimeSecurityUtils.validateDeviceTime(this, new TimeSecurityUtils.TimeValidationCallback() {
            @Override
            public void onValidationComplete(TimeSecurityUtils.TimeValidationResult result) {
                runOnUiThread(() -> {
                    lastTimeValidation = result;
                    isTimeValid = result.isTimeValid;
                    updateTimeValidationUI(result);
                    updateButtonStates();
                });
            }
        });
    }

    /**
     * Update UI based on time validation result
     */
    private void updateTimeValidationUI(TimeSecurityUtils.TimeValidationResult result) {
        if (tvTimeValidationStatus == null) return;

        if (result.isTimeValid) {
            tvTimeValidationStatus.setText("✅ Device time verified");
            tvTimeValidationStatus.setTextColor(getResources().getColor(R.color.green_600));
            if (cvTimeValidation != null) {
                cvTimeValidation.setCardBackgroundColor(getResources().getColor(R.color.green_50));
            }
        } else {
            tvTimeValidationStatus.setText("⚠️ Time validation failed: " + result.errorMessage);
            tvTimeValidationStatus.setTextColor(getResources().getColor(R.color.red_600));
            if (cvTimeValidation != null) {
                cvTimeValidation.setCardBackgroundColor(getResources().getColor(R.color.red_50));
            }
        }
    }

    private void getEmployeeData() {
        employeeDocId = getIntent().getStringExtra("employee_doc_id");
        pfNumber = getIntent().getStringExtra("pf_number");
        employeeName = getIntent().getStringExtra("employee_name");

        if (employeeDocId == null) {
            employeeDocId = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                    .getString("employee_doc_id", null);
        }

        if (employeeDocId != null && (pfNumber == null || employeeName == null)) {
            loadEmployeeDetails();
        }
    }

    private void loadEmployeeDetails() {
        db.collection("employees").document(employeeDocId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            if (pfNumber == null) pfNumber = document.getString("pfNumber");
                            if (employeeName == null) employeeName = document.getString("name");
                        }
                    }
                });
    }

    private void initViews() {
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvLocationStatus = findViewById(R.id.tv_location_status);
        tvDistanceFromOffice = findViewById(R.id.tv_distance_from_office);
        tvTodayStatus = findViewById(R.id.tv_today_status);
        tvClockInTime = findViewById(R.id.tv_clock_in_time);
        tvClockOutTime = findViewById(R.id.tv_clock_out_time);
        tvHoursWorked = findViewById(R.id.tv_hours_worked);
        tvTimeValidationStatus = findViewById(R.id.tv_time_validation_status);
        tvDeviceStatus = findViewById(R.id.tv_device_status); // Add this to layout
        btnClockIn = findViewById(R.id.btn_clock_in);
        btnClockOut = findViewById(R.id.btn_clock_out);
        progressBar = findViewById(R.id.progress_bar);
        cvLocationInfo = findViewById(R.id.cv_location_info);
        cvAttendanceInfo = findViewById(R.id.cv_attendance_info);
        cvTimeValidation = findViewById(R.id.cv_time_validation);
        cvDeviceValidation = findViewById(R.id.cv_device_validation); // Add this to layout

        setDefaultValues();
    }

    private void setDefaultValues() {
        tvCurrentTime.setText(DateTimeUtils.getCurrentDisplayTime());
        tvLocationStatus.setText("Checking location...");
        tvDistanceFromOffice.setText("--");
        tvTodayStatus.setText("Not clocked in");
        tvClockInTime.setText("--:--");
        tvClockOutTime.setText("--:--");
        tvHoursWorked.setText("0h 0m");

        if (tvTimeValidationStatus != null) {
            tvTimeValidationStatus.setText("Validating device time...");
        }
        if (tvDeviceStatus != null) {
            tvDeviceStatus.setText("Validating device session...");
        }

        btnClockIn.setEnabled(false);
        btnClockOut.setEnabled(false);
        btnClockOut.setVisibility(View.GONE);
    }

    // ... (continue with other methods like loadCompanyLocation, loadTodayAttendance, etc.)
    // The key changes are in clock in/out methods:

    /**
     * SECURITY: Enhanced clock-in with device session management
     */
    private void performSecureClockIn(double latitude, double longitude) {
        String today = DateTimeUtils.getCurrentDate();

        // SECURITY: Create secure timestamp with validation data
        TimeSecurityUtils.AttendanceTimestamp secureTimestamp =
                TimeSecurityUtils.createSecureTimestamp(this);

        if (!secureTimestamp.isValid()) {
            setLoading(false);
            Toast.makeText(this, "Clock-in denied: Time validation failed", Toast.LENGTH_LONG).show();
            return;
        }

        // SECURITY: Check for existing active sessions one more time
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", today)
                .whereEqualTo("sessionActive", true)
                .whereNotEqualTo("deviceId", deviceId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        setLoading(false);
                        Toast.makeText(this, "Cannot clock in: Another device session is active",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Proceed with clock-in
                    createAttendanceRecord(today, latitude, longitude, secureTimestamp);
                });
    }

    private void createAttendanceRecord(String today, double latitude, double longitude,
                                        TimeSecurityUtils.AttendanceTimestamp secureTimestamp) {

        AttendanceRecord record = new AttendanceRecord(
                employeeDocId, pfNumber, employeeName, today,
                latitude, longitude, "Company Office"
        );

        // Check if employee is late
        String currentTime = DateTimeUtils.getCurrentTime();
        boolean isLate = DateTimeUtils.isLateArrival(currentTime, workStartTime);
        int lateMinutes = isLate ? DateTimeUtils.calculateLateMinutes(currentTime, workStartTime) : 0;

        // SECURITY: Enhanced attendance data with device and time validation
        Map<String, Object> attendanceData = new HashMap<>();
        attendanceData.put("employeeDocId", record.getEmployeeDocId());
        attendanceData.put("pfNumber", record.getPfNumber());
        attendanceData.put("employeeName", record.getEmployeeName());
        attendanceData.put("date", record.getDate());
        attendanceData.put("clockInTime", record.getClockInTime());
        attendanceData.put("clockInTimestamp", record.getClockInTimestamp());
        attendanceData.put("clockInLatitude", record.getClockInLatitude());
        attendanceData.put("clockInLongitude", record.getClockInLongitude());
        attendanceData.put("locationName", record.getLocationName());
        attendanceData.put("status", isLate ? "Late" : "Present");
        attendanceData.put("totalHours", record.getTotalHours());
        attendanceData.put("isLate", isLate);
        attendanceData.put("lateMinutes", lateMinutes);
        attendanceData.put("createdAt", record.getCreatedAt());

        // SECURITY: Add device session management
        attendanceData.put("deviceId", deviceId);
        attendanceData.put("deviceModel", DeviceSecurityUtils.getDeviceModel());
        attendanceData.put("deviceManufacturer", DeviceSecurityUtils.getDeviceManufacturer());
        attendanceData.put("sessionActive", true);
        attendanceData.put("sessionStartTime", com.google.firebase.Timestamp.now());
        attendanceData.put("lastHeartbeat", com.google.firebase.Timestamp.now());
        attendanceData.put("hasDeviceConflict", false);

        // SECURITY: Add time validation data
        attendanceData.put("deviceTime", secureTimestamp.deviceTime);
        attendanceData.put("serverTime", secureTimestamp.serverTime);
        attendanceData.put("timeZone", secureTimestamp.timeZone);
        attendanceData.put("autoTimeEnabled", secureTimestamp.autoTimeEnabled);
        attendanceData.put("timeValidationMethod", lastTimeValidation != null ? lastTimeValidation.validationMethod : "UNKNOWN");
        attendanceData.put("timeDifferenceMs", lastTimeValidation != null ? lastTimeValidation.timeDifferenceMs : 0);

        db.collection("attendance")
                .add(attendanceData)
                .addOnSuccessListener(documentReference -> {
                    setLoading(false);
                    todayAttendanceDocId = documentReference.getId();
                    activeSessionId = documentReference.getId();
                    isClockedIn = true;

                    tvClockInTime.setText(DateTimeUtils.formatTimeForDisplay(record.getClockInTime()));

                    if (isLate) {
                        tvTodayStatus.setText("Clocked In (Late) ⚠️");
                        Toast.makeText(this, "Clocked in successfully! You are " + lateMinutes + " minutes late.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        tvTodayStatus.setText("Clocked In ✅");
                        Toast.makeText(this, "Successfully clocked in!", Toast.LENGTH_SHORT).show();
                    }

                    btnClockIn.setVisibility(View.GONE);
                    btnClockOut.setVisibility(View.VISIBLE);
                    updateButtonStates();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Failed to clock in: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    /**
     * SECURITY: Enhanced clock-out with session termination
     */
    private void performSecureClockOut(double latitude, double longitude, String earlyClockOutReason) {
        // ... existing clock-out logic ...

        // SECURITY: Add session termination to updates
        Map<String, Object> updates = new HashMap<>();
        updates.put("clockOutTime", DateTimeUtils.getCurrentTime());
        updates.put("clockOutTimestamp", com.google.firebase.Timestamp.now());
        updates.put("sessionActive", false);
        updates.put("sessionEndTime", com.google.firebase.Timestamp.now());
        updates.put("sessionDuration", System.currentTimeMillis() - (lastTimeValidation != null ? lastTimeValidation.deviceTime : 0));

        // ... rest of clock-out updates ...

        db.collection("attendance").document(todayAttendanceDocId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    isClockedIn = false;
                    activeSessionId = null;

                    // ... rest of success handling ...
                });
    }

    /**
     * SECURITY: Enhanced button state validation including device validation
     */
    private void updateButtonStates() {
        // SECURITY CHECK: Disable buttons if time or device is not valid
        if (!isTimeValid || !isDeviceValid) {
            btnClockIn.setEnabled(false);
            btnClockOut.setEnabled(false);
            return;
        }

        // ... rest of existing button state logic ...
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationUpdateHandler != null && locationUpdateRunnable != null) {
            locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
        }
        if (timeUpdateHandler != null && timeUpdateRunnable != null) {
            timeUpdateHandler.removeCallbacks(timeUpdateRunnable);
        }
        if (sessionValidationHandler != null && sessionValidationRunnable != null) {
            sessionValidationHandler.removeCallbacks(sessionValidationRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // SECURITY: Re-validate everything when app resumes
        validateDeviceTime();
        validateDeviceSession();
    }
}