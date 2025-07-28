// Modified AttendanceActivity.java with simple device session management
package org.smart.attendance_beta;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.smart.attendance_beta.utils.DateTimeUtils;
import org.smart.attendance_beta.utils.DeviceSecurityUtils;
import org.smart.attendance_beta.utils.LocationUtils;
import org.smart.attendance_beta.utils.TimeSecurityUtils;

import java.text.DecimalFormat;
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

    // Device Security
    private boolean isTimeValid = false;
    private boolean isDeviceValid = false;
    private String deviceId;
    private TimeSecurityUtils.TimeValidationResult lastTimeValidation;

    // Handlers
    private Handler locationUpdateHandler = new Handler();
    private Runnable locationUpdateRunnable;
    private Handler timeUpdateHandler = new Handler();
    private Runnable timeUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        // Initialize device ID
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
        checkDeviceSession();

        // Load data
        loadCompanyLocation();
        loadTodayAttendance();

        // Start updates
        startTimeUpdates();
        startLocationUpdates();

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
     * Check if this device can be used for attendance (prevent multi-device abuse)
     */
    private void checkDeviceSession() {
        if (employeeDocId == null) return;

        String today = DateTimeUtils.getCurrentDate();
        updateDeviceValidationUI(false, "Validating device session...");

        // Check if employee has an active attendance session on another device
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", today)
                .whereEqualTo("sessionActive", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // No active session - device is valid
                            isDeviceValid = true;
                            updateDeviceValidationUI(true, "Device authorized ✅");
                        } else {
                            // Active session found - check if it's on this device
                            DocumentSnapshot activeSession = task.getResult().getDocuments().get(0);
                            String sessionDeviceId = activeSession.getString("deviceId");

                            if (deviceId.equals(sessionDeviceId)) {
                                // Same device - valid
                                isDeviceValid = true;
                                todayAttendanceDocId = activeSession.getId();
                                isClockedIn = true;
                                updateDeviceValidationUI(true, "Device session active ✅");
                            } else {
                                // Different device - block access
                                isDeviceValid = false;
                                String activeDeviceInfo = getActiveDeviceInfo(activeSession);
                                updateDeviceValidationUI(false, "Another device is active ❌");
                                showDeviceConflictDialog(activeDeviceInfo, activeSession);
                            }
                        }
                        updateButtonStates();
                    } else {
                        isDeviceValid = false;
                        updateDeviceValidationUI(false, "Device validation failed ❌");
                        updateButtonStates();
                        Toast.makeText(this, "Error checking device session: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Get display info for the active device
     */
    private String getActiveDeviceInfo(DocumentSnapshot session) {
        String deviceModel = session.getString("deviceModel");
        String deviceManufacturer = session.getString("deviceManufacturer");
        String clockInTime = session.getString("clockInTime");

        if (deviceModel != null && deviceManufacturer != null) {
            return deviceManufacturer + " " + deviceModel +
                    (clockInTime != null ? " (clocked in at " + DateTimeUtils.formatTimeForDisplay(clockInTime) + ")" : "");
        } else {
            return "Unknown device" +
                    (clockInTime != null ? " (clocked in at " + DateTimeUtils.formatTimeForDisplay(clockInTime) + ")" : "");
        }
    }

    /**
     * Show dialog when device conflict is detected
     */
    private void showDeviceConflictDialog(String activeDeviceInfo, DocumentSnapshot activeSession) {
        new AlertDialog.Builder(this)
                .setTitle("🚫 Device Already Active")
                .setMessage("You are already clocked in from another device:\n\n" +
                        "Active Device: " + activeDeviceInfo + "\n" +
                        "Current Device: " + DeviceSecurityUtils.getDeviceModel() + "\n\n" +
                        "To prevent attendance fraud, only one device can be used per day. " +
                        "Please clock out from your original device first.")
                .setPositiveButton("OK", (dialog, which) -> {
                    finish(); // Close this activity
                })
                .setCancelable(false)
                .show();
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
     * Validate device time against server time
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
        tvDeviceStatus = findViewById(R.id.tv_device_status);
        btnClockIn = findViewById(R.id.btn_clock_in);
        btnClockOut = findViewById(R.id.btn_clock_out);
        progressBar = findViewById(R.id.progress_bar);
        cvLocationInfo = findViewById(R.id.cv_location_info);
        cvAttendanceInfo = findViewById(R.id.cv_attendance_info);
        cvTimeValidation = findViewById(R.id.cv_time_validation);
        cvDeviceValidation = findViewById(R.id.cv_device_validation);

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

    private void loadCompanyLocation() {
        db.collection("locations").document("company-main")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            Double lat = document.getDouble("latitude");
                            Double lng = document.getDouble("longitude");
                            Long radius = document.getLong("radius");

                            if (lat != null) companyLatitude = lat;
                            if (lng != null) companyLongitude = lng;
                            if (radius != null) companyRadius = radius.intValue();
                        }
                    }
                });
    }
    private void loadTodayAttendance() {
        if (employeeDocId == null) return;

        String today = DateTimeUtils.getCurrentDate();

        // Load attendance for this specific device only
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", today)
                .whereEqualTo("deviceId", deviceId) // Only this device
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot attendance = task.getResult().getDocuments().get(0);
                        todayAttendanceDocId = attendance.getId();

                        String clockInTime = attendance.getString("clockInTime");
                        String clockOutTime = attendance.getString("clockOutTime");
                        Boolean sessionActive = attendance.getBoolean("sessionActive");

                        if (clockInTime != null) {
                            tvClockInTime.setText(DateTimeUtils.formatTimeForDisplay(clockInTime));

                            if (sessionActive != null && sessionActive && clockOutTime == null) {
                                isClockedIn = true;
                                tvTodayStatus.setText("Clocked In ✅");
                                btnClockIn.setVisibility(View.GONE);
                                btnClockOut.setVisibility(View.VISIBLE);
                            } else if (clockOutTime != null) {
                                isClockedIn = false;
                                tvTodayStatus.setText("Work Complete ✅");
                                tvClockOutTime.setText(DateTimeUtils.formatTimeForDisplay(clockOutTime));

                                double hours = DateTimeUtils.calculateHoursWorked(clockInTime, clockOutTime);
                                tvHoursWorked.setText(DateTimeUtils.formatHoursWorked(hours));

                                btnClockIn.setVisibility(View.GONE);
                                btnClockOut.setVisibility(View.GONE);
                            }
                        }
                    } else {
                        tvTodayStatus.setText("Ready to Clock In");
                        btnClockIn.setVisibility(View.VISIBLE);
                        btnClockOut.setVisibility(View.GONE);
                    }
                    updateButtonStates();
                });
    }

    private void startTimeUpdates() {
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                tvCurrentTime.setText(DateTimeUtils.getCurrentDisplayTime());

                if (isClockedIn && !tvClockInTime.getText().toString().equals("--:--")) {
                    String clockInTime = tvClockInTime.getText().toString() + ":00";
                    String currentTime = DateTimeUtils.getCurrentTime();
                    double hours = DateTimeUtils.calculateHoursWorked(clockInTime, currentTime);
                    tvHoursWorked.setText(DateTimeUtils.formatHoursWorked(hours) + " (ongoing)");
                }

                timeUpdateHandler.postDelayed(this, 1000);
            }
        };
        timeUpdateHandler.post(timeUpdateRunnable);
    }

    private void startLocationUpdates() {
        if (!LocationUtils.hasLocationPermissions(this)) {
            requestLocationPermissions();
            return;
        }

        locationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateLocation();
                locationUpdateHandler.postDelayed(this, 10000);
            }
        };
        locationUpdateHandler.post(locationUpdateRunnable);
    }

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                tvLocationStatus.setText("Location permission required");
                tvDistanceFromOffice.setText("Enable location to use attendance");
            }
        }
    }

    private void updateLocation() {
        if (!LocationUtils.hasLocationPermissions(this)) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        double distance = LocationUtils.calculateDistance(
                                location.getLatitude(), location.getLongitude(),
                                companyLatitude, companyLongitude
                        );

                        updateLocationUI(distance);
                        updateButtonStates();
                    } else {
                        tvLocationStatus.setText("Unable to get location");
                        tvDistanceFromOffice.setText("Check GPS settings");
                        updateButtonStates();
                    }
                })
                .addOnFailureListener(this, e -> {
                    tvLocationStatus.setText("Location error");
                    tvDistanceFromOffice.setText("Please check GPS");
                    updateButtonStates();
                });
    }

    private void updateLocationUI(double distance) {
        DecimalFormat df = new DecimalFormat("#.#");
        tvDistanceFromOffice.setText(LocationUtils.formatDistance(distance) + " from office");

        String status = LocationUtils.getLocationStatus(distance, companyRadius);
        tvLocationStatus.setText(status);

        if (distance <= companyRadius) {
            cvLocationInfo.setCardBackgroundColor(getResources().getColor(R.color.green_50));
        } else if (distance <= companyRadius + 100) {
            cvLocationInfo.setCardBackgroundColor(getResources().getColor(R.color.orange_50));
        } else {
            cvLocationInfo.setCardBackgroundColor(getResources().getColor(R.color.red_50));
        }
    }

    /**
     * Enhanced button state validation including device validation
     */
    private void updateButtonStates() {
        // SECURITY CHECK: Disable buttons if time or device is not valid
        if (!isTimeValid || !isDeviceValid) {
            btnClockIn.setEnabled(false);
            btnClockOut.setEnabled(false);
            return;
        }

        if (!LocationUtils.hasLocationPermissions(this)) {
            btnClockIn.setEnabled(false);
            btnClockOut.setEnabled(false);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        double distance = LocationUtils.calculateDistance(
                                location.getLatitude(), location.getLongitude(),
                                companyLatitude, companyLongitude
                        );

                        boolean inRange = distance <= companyRadius;

                        if (isClockedIn) {
                            btnClockOut.setEnabled(inRange);
                            btnClockIn.setEnabled(false);
                        } else {
                            btnClockIn.setEnabled(inRange);
                            btnClockOut.setEnabled(false);
                        }
                    } else {
                        btnClockIn.setEnabled(false);
                        btnClockOut.setEnabled(false);
                    }
                })
                .addOnFailureListener(e -> {
                    btnClockIn.setEnabled(false);
                    btnClockOut.setEnabled(false);
                });
    }

    private void setupClickListeners() {
        btnClockIn.setOnClickListener(v -> {
            if (!isTimeValid || !isDeviceValid) {
                Toast.makeText(this, "Security validation failed. Cannot clock in.", Toast.LENGTH_SHORT).show();
                return;
            }
            clockIn();
        });

        btnClockOut.setOnClickListener(v -> {
            if (!isTimeValid || !isDeviceValid) {
                Toast.makeText(this, "Security validation failed. Cannot clock out.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isEarlyClockOut()) {
                showEarlyClockOutDialog();
            } else {
                clockOut(null);
            }
        });

        cvLocationInfo.setOnClickListener(v -> {
            updateLocation();
            Toast.makeText(this, "Location updated", Toast.LENGTH_SHORT).show();
        });

        if (cvTimeValidation != null) {
            cvTimeValidation.setOnClickListener(v -> {
                validateDeviceTime();
                Toast.makeText(this, "Time validation refreshed", Toast.LENGTH_SHORT).show();
            });
        }

        if (cvDeviceValidation != null) {
            cvDeviceValidation.setOnClickListener(v -> {
                checkDeviceSession();
                Toast.makeText(this, "Device validation refreshed", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private boolean isEarlyClockOut() {
        String currentTime = DateTimeUtils.getCurrentTime();
        try {
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date current = timeFormat.parse(currentTime);
            java.util.Date workEnd = timeFormat.parse(workEndTime + ":00");

            return current.before(workEnd);
        } catch (java.text.ParseException e) {
            return false;
        }
    }

    private void showEarlyClockOutDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_early_clockout, null);
        EditText etReason = dialogView.findViewById(R.id.et_reason);

        new AlertDialog.Builder(this)
                .setTitle("Early Clock Out")
                .setMessage("You're clocking out before " + workEndTime + ". Please provide a reason:")
                .setView(dialogView)
                .setPositiveButton("Clock Out", (dialog, which) -> {
                    String reason = etReason.getText().toString().trim();
                    if (TextUtils.isEmpty(reason)) {
                        Toast.makeText(this, "Please provide a reason for early clock out", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    clockOut(reason);
                })
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    /**
     * Clock in with device session management
     */
    private void clockIn() {
        if (!LocationUtils.hasLocationPermissions(this)) {
            requestLocationPermissions();
            return;
        }

        setLoading(true);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        double distance = LocationUtils.calculateDistance(
                                location.getLatitude(), location.getLongitude(),
                                companyLatitude, companyLongitude
                        );

                        if (distance <= companyRadius) {
                            performClockIn(location.getLatitude(), location.getLongitude());
                        } else {
                            setLoading(false);
                            Toast.makeText(this, "You're too far from the office to clock in",
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        setLoading(false);
                        Toast.makeText(this, "Unable to get location. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Location error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Perform clock in with device tracking
     */
    private void performClockIn(double latitude, double longitude) {
        String today = DateTimeUtils.getCurrentDate();
        String currentTime = DateTimeUtils.getCurrentTime();

        // Check if employee is late
        boolean isLate = DateTimeUtils.isLateArrival(currentTime, workStartTime);
        int lateMinutes = isLate ? DateTimeUtils.calculateLateMinutes(currentTime, workStartTime) : 0;

        // Create attendance record with device information
        Map<String, Object> attendanceData = new HashMap<>();
        attendanceData.put("employeeDocId", employeeDocId);
        attendanceData.put("pfNumber", pfNumber);
        attendanceData.put("employeeName", employeeName);
        attendanceData.put("date", today);
        attendanceData.put("clockInTime", currentTime);
        attendanceData.put("clockInTimestamp", com.google.firebase.Timestamp.now());
        attendanceData.put("clockInLatitude", latitude);
        attendanceData.put("clockInLongitude", longitude);
        attendanceData.put("locationName", "Company Office");
        attendanceData.put("status", isLate ? "Late" : "Present");
        attendanceData.put("totalHours", 0.0);
        attendanceData.put("isLate", isLate);
        attendanceData.put("lateMinutes", lateMinutes);
        attendanceData.put("createdAt", com.google.firebase.Timestamp.now());

        // Device session management
        attendanceData.put("deviceId", deviceId);
        attendanceData.put("deviceModel", DeviceSecurityUtils.getDeviceModel());
        attendanceData.put("deviceManufacturer", DeviceSecurityUtils.getDeviceManufacturer());
        attendanceData.put("sessionActive", true);
        attendanceData.put("sessionStartTime", com.google.firebase.Timestamp.now());

        // Time security validation
        if (lastTimeValidation != null) {
            attendanceData.put("timeValidationMethod", lastTimeValidation.validationMethod);
            attendanceData.put("timeDifferenceMs", lastTimeValidation.timeDifferenceMs);
            attendanceData.put("autoTimeEnabled", TimeSecurityUtils.isAutomaticTimeEnabled(this));
        }

        db.collection("attendance")
                .add(attendanceData)
                .addOnSuccessListener(documentReference -> {
                    setLoading(false);
                    todayAttendanceDocId = documentReference.getId();
                    isClockedIn = true;

                    tvClockInTime.setText(DateTimeUtils.formatTimeForDisplay(currentTime));

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
     * Clock out with session termination
     */
    private void clockOut(String earlyClockOutReason) {
        if (!LocationUtils.hasLocationPermissions(this) || todayAttendanceDocId == null) {
            Toast.makeText(this, "Cannot clock out: No active session", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        double distance = LocationUtils.calculateDistance(
                                location.getLatitude(), location.getLongitude(),
                                companyLatitude, companyLongitude
                        );

                        if (distance <= companyRadius) {
                            performClockOut(location.getLatitude(), location.getLongitude(), earlyClockOutReason);
                        } else {
                            setLoading(false);
                            Toast.makeText(this, "You're too far from the office to clock out",
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        setLoading(false);
                        Toast.makeText(this, "Unable to get location. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Location error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Perform clock out with session termination
     */
    private void performClockOut(double latitude, double longitude, String earlyClockOutReason) {
        String currentTime = DateTimeUtils.getCurrentTime();

        // Get clock in time to calculate hours worked
        String clockInTime = tvClockInTime.getText().toString() + ":00";
        double hoursWorked = DateTimeUtils.calculateHoursWorked(clockInTime, currentTime);

        Map<String, Object> updates = new HashMap<>();
        updates.put("clockOutTime", currentTime);
        updates.put("clockOutTimestamp", com.google.firebase.Timestamp.now());
        updates.put("clockOutLatitude", latitude);
        updates.put("clockOutLongitude", longitude);
        updates.put("totalHours", hoursWorked);
        updates.put("sessionActive", false); // Terminate session
        updates.put("sessionEndTime", com.google.firebase.Timestamp.now());

        if (earlyClockOutReason != null && !earlyClockOutReason.isEmpty()) {
            updates.put("earlyClockOutReason", earlyClockOutReason);
            updates.put("isEarlyClockOut", true);
            updates.put("earlyClockOutTime", workEndTime);
        }

        db.collection("attendance").document(todayAttendanceDocId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    isClockedIn = false;

                    tvClockOutTime.setText(DateTimeUtils.formatTimeForDisplay(currentTime));
                    tvHoursWorked.setText(DateTimeUtils.formatHoursWorked(hoursWorked));

                    if (earlyClockOutReason != null) {
                        tvTodayStatus.setText("Early Clock Out ⚠️");
                        Toast.makeText(this, "Clocked out early! Total hours: " +
                                        DateTimeUtils.formatHoursWorked(hoursWorked) + ". Reason logged for admin review.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        tvTodayStatus.setText("Work Complete ✅");
                        Toast.makeText(this, "Successfully clocked out! Total hours: " +
                                DateTimeUtils.formatHoursWorked(hoursWorked), Toast.LENGTH_LONG).show();
                    }

                    btnClockIn.setVisibility(View.GONE);
                    btnClockOut.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Failed to clock out: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnClockIn.setEnabled(false);
            btnClockOut.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            updateButtonStates();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-validate device session when app resumes
        checkDeviceSession();
        validateDeviceTime();
    }
}