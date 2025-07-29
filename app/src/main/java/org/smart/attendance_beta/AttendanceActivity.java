package org.smart.attendance_beta;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
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
    private static final String TAG = "AttendanceActivity";

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

    // ✅ ENHANCED: Multiple office support
    private java.util.List<OfficeLocation> officeLocations = new java.util.ArrayList<>();
    private OfficeLocation currentOffice = null;
    private boolean isAtAnyOffice = false;

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
        loadAllOfficeLocations(); // ✅ ENHANCED: Load all offices instead of just one
        loadTodayAttendance();

        // Start updates
        startTimeUpdates();
        startLocationUpdates();

        // Setup click listeners
        setupClickListeners();

        // ✅ ENHANCED: Immediate location refresh on activity start
        refreshLocationImmediately();
        // Make status bar transparent and extend content behind it

    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("Attendance");
            }
        }
    }

    // ✅ NEW METHOD: Immediate location refresh
    private void refreshLocationImmediately() {
        Log.d(TAG, "🎯 Refreshing location immediately on activity start");

        if (!LocationUtils.hasLocationPermissions(this)) {
            Log.w(TAG, "📍 Location permissions not granted, requesting...");
            requestLocationPermissions();
            return;
        }

        // Show loading state
        if (tvLocationStatus != null) {
            tvLocationStatus.setText("🔄 Getting current location...");
        }
        if (tvDistanceFromOffice != null) {
            tvDistanceFromOffice.setText("📍 Locating...");
        }

        // Get fresh location immediately
        updateLocation();

        // Also get a high-accuracy location update
        fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY,
                null
        ).addOnSuccessListener(location -> {
            if (location != null) {
                Log.d(TAG, "📍 High-accuracy location obtained immediately");
                OfficeDetectionResult result = detectOfficeLocation(location);

                updateLocationUI(result);
                updateButtonStates();

                // Show success toast with office information
                String toastMessage;
                if (result.isAtOffice && result.currentOffice != null) {
                    toastMessage = "✅ At " + result.currentOffice.name + " (" +
                            LocationUtils.formatDistance(result.currentDistance) + ")";
                } else if (result.closestOffice != null) {
                    toastMessage = "🚫 Outside work area (" +
                            LocationUtils.formatDistance(result.closestDistance) + " from " +
                            result.closestOffice.name + ")";
                } else {
                    toastMessage = "❌ No office locations available";
                }
                Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();
            } else {
                Log.w(TAG, "📍 Could not get immediate location");
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "📍 Error getting immediate location: " + e.getMessage());
        });
    }

    /**
     * Load all office locations from Firestore
     */
    private void loadAllOfficeLocations() {
        Log.d(TAG, "📍 Loading all office locations...");

        db.collection("locations")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        officeLocations.clear();

                        for (DocumentSnapshot document : task.getResult()) {
                            OfficeLocation office = createOfficeFromDocument(document);
                            if (office != null) {
                                officeLocations.add(office);
                                Log.d(TAG, "📍 Loaded office: " + office.name + " at " +
                                        office.latitude + ", " + office.longitude + " (radius: " + office.radius + "m)");
                            }
                        }

                        if (officeLocations.isEmpty()) {
                            Log.w(TAG, "⚠️ No office locations found, using default");
                            addDefaultOffice();
                        }

                        Log.d(TAG, "📍 Total offices loaded: " + officeLocations.size());

                        // Refresh location immediately after loading offices
                        refreshLocationImmediately();
                    } else {
                        Log.e(TAG, "❌ Error loading office locations: " + task.getException().getMessage());
                        Toast.makeText(this, "Error loading office locations", Toast.LENGTH_SHORT).show();
                        addDefaultOffice();
                        refreshLocationImmediately();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load office locations: " + e.getMessage());
                    Toast.makeText(this, "Failed to load office locations", Toast.LENGTH_SHORT).show();
                    addDefaultOffice();
                    refreshLocationImmediately();
                });
    }

    /**
     * Create OfficeLocation object from Firestore document
     */
    private OfficeLocation createOfficeFromDocument(DocumentSnapshot document) {
        try {
            String docId = document.getId();
            String name = document.getString("name");
            Double lat = document.getDouble("latitude");
            Double lng = document.getDouble("longitude");
            Long radius = document.getLong("radius");

            if (lat != null && lng != null) {
                OfficeLocation office = new OfficeLocation();
                office.id = docId;
                office.name = name != null ? name : formatOfficeName(docId);
                office.latitude = lat;
                office.longitude = lng;
                office.radius = radius != null ? radius.intValue() : 200;
                return office;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error parsing office document " + document.getId() + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Format office name from document ID
     */
    private String formatOfficeName(String docId) {
        switch (docId) {
            case "company-main":
                return "Main Office";
            case "town-campus":
                return "Town Campus";
            default:
                // Convert kebab-case to Title Case
                String[] words = docId.replace("-", " ").replace("_", " ").split(" ");
                StringBuilder result = new StringBuilder();
                for (String word : words) {
                    if (word.length() > 0) {
                        result.append(Character.toUpperCase(word.charAt(0)));
                        if (word.length() > 1) {
                            result.append(word.substring(1).toLowerCase());
                        }
                        result.append(" ");
                    }
                }
                return result.toString().trim();
        }
    }

    /**
     * Add default office if none loaded
     */
    private void addDefaultOffice() {
        OfficeLocation defaultOffice = new OfficeLocation();
        defaultOffice.id = "company-main";
        defaultOffice.name = "Main Office";
        defaultOffice.latitude = companyLatitude;
        defaultOffice.longitude = companyLongitude;
        defaultOffice.radius = companyRadius;
        officeLocations.add(defaultOffice);
    }

    /**
     * Detect which office (if any) the user is currently at
     */
    private OfficeDetectionResult detectOfficeLocation(Location userLocation) {
        OfficeDetectionResult result = new OfficeDetectionResult();
        result.isAtOffice = false;
        result.closestOffice = null;
        result.closestDistance = Double.MAX_VALUE;

        for (OfficeLocation office : officeLocations) {
            double distance = LocationUtils.calculateDistance(
                    userLocation.getLatitude(), userLocation.getLongitude(),
                    office.latitude, office.longitude
            );

            // Check if user is within this office's radius
            if (distance <= office.radius) {
                result.isAtOffice = true;
                result.currentOffice = office;
                result.currentDistance = distance;

                // Update global state
                currentOffice = office;
                isAtAnyOffice = true;

                Log.d(TAG, "✅ User is at " + office.name + " (distance: " + String.format("%.0f", distance) + "m)");
                return result;
            }

            // Track closest office even if not within radius
            if (distance < result.closestDistance) {
                result.closestDistance = distance;
                result.closestOffice = office;
            }
        }

        // User is not at any office
        currentOffice = null;
        isAtAnyOffice = false;
        result.currentDistance = result.closestDistance;

        Log.d(TAG, "🚫 User not at any office. Closest: " +
                (result.closestOffice != null ? result.closestOffice.name : "None") +
                " (" + String.format("%.0f", result.closestDistance) + "m)");

        return result;
    }

    private void updateLocationUI(OfficeDetectionResult result) {
        if (result.isAtOffice && result.currentOffice != null) {
            // ✅ User is at an office
            tvLocationStatus.setText("✅ At " + result.currentOffice.name);
            tvDistanceFromOffice.setText(LocationUtils.formatDistance(result.currentDistance) + " from " + result.currentOffice.name);
            cvLocationInfo.setCardBackgroundColor(getResources().getColor(R.color.green_50));

        } else if (result.closestOffice != null) {
            // ❌ User is not at any office, show closest
            tvLocationStatus.setText("🚫 Outside office area");
            tvDistanceFromOffice.setText(LocationUtils.formatDistance(result.closestDistance) + " from " + result.closestOffice.name);

            // Color based on distance from closest office
            if (result.closestDistance <= result.closestOffice.radius + 100) {
                cvLocationInfo.setCardBackgroundColor(getResources().getColor(R.color.orange_50));
            } else {
                cvLocationInfo.setCardBackgroundColor(getResources().getColor(R.color.red_50));
            }
        } else {
            // ❌ No offices available
            tvLocationStatus.setText("❌ No office locations available");
            tvDistanceFromOffice.setText("Contact administrator");
            cvLocationInfo.setCardBackgroundColor(getResources().getColor(R.color.red_50));
        }
    }

    private void updateLocation() {
        if (!LocationUtils.hasLocationPermissions(this)) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        // ✅ ENHANCED: Check against all office locations
                        OfficeDetectionResult result = detectOfficeLocation(location);

                        Log.d(TAG, "📍 Location updated - " + result.toString());
                        updateLocationUI(result);
                        updateButtonStates();
                    } else {
                        Log.w(TAG, "📍 No location available");
                        tvLocationStatus.setText("Unable to get location");
                        tvDistanceFromOffice.setText("Check GPS settings");
                        updateButtonStates();
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "📍 Location error: " + e.getMessage());
                    tvLocationStatus.setText("Location error");
                    tvDistanceFromOffice.setText("Please check GPS");
                    updateButtonStates();
                });
    }

    /**
     * Enhanced button state validation for multiple offices
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

        // ✅ ENHANCED: Use multiple office detection
        if (isClockedIn) {
            btnClockOut.setEnabled(isAtAnyOffice);
            btnClockIn.setEnabled(false);
        } else {
            btnClockIn.setEnabled(isAtAnyOffice);
            btnClockOut.setEnabled(false);
        }
    }

    // ✅ NEW: Data classes for multi-office support

    /**
     * Office location data class
     */
    private static class OfficeLocation {
        public String id;
        public String name;
        public double latitude;
        public double longitude;
        public int radius;

        @Override
        public String toString() {
            return name + " (" + latitude + ", " + longitude + ", " + radius + "m)";
        }
    }

    /**
     * Office detection result class
     */
    private static class OfficeDetectionResult {
        public boolean isAtOffice;
        public OfficeLocation currentOffice;  // Office user is currently at
        public double currentDistance;        // Distance to current office
        public OfficeLocation closestOffice;  // Closest office if not at any
        public double closestDistance;        // Distance to closest office

        @Override
        public String toString() {
            if (isAtOffice && currentOffice != null) {
                return "At " + currentOffice.name + " (" + String.format("%.0f", currentDistance) + "m)";
            } else if (closestOffice != null) {
                return "Not at office. Closest: " + closestOffice.name + " (" + String.format("%.0f", closestDistance) + "m)";
            } else {
                return "No office locations available";
            }
        }
    }

    // === FIRST HALF ENDS HERE ===
    // Ready for second half with device validation, clock in/out methods, etc.

// === FIRST HALF ENDS HERE ===
// Ready for second half with device validation, clock in/out methods, etc.
// === SECOND HALF CONTINUES FROM PART 1 ===

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
        String officeName = session.getString("officeName");

        String deviceInfo = "Unknown device";
        if (deviceModel != null && deviceManufacturer != null) {
            deviceInfo = deviceManufacturer + " " + deviceModel;
        }

        String additionalInfo = "";
        if (clockInTime != null) {
            additionalInfo += " (clocked in at " + DateTimeUtils.formatTimeForDisplay(clockInTime);
            if (officeName != null) {
                additionalInfo += " from " + officeName;
            }
            additionalInfo += ")";
        }

        return deviceInfo + additionalInfo;
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
                        String officeName = attendance.getString("officeName");
                        Boolean sessionActive = attendance.getBoolean("sessionActive");

                        if (clockInTime != null) {
                            tvClockInTime.setText(DateTimeUtils.formatTimeForDisplay(clockInTime));

                            if (sessionActive != null && sessionActive && clockOutTime == null) {
                                isClockedIn = true;
                                String statusText = officeName != null ?
                                        "Clocked In at " + officeName + " ✅" : "Clocked In ✅";
                                tvTodayStatus.setText(statusText);
                                btnClockIn.setVisibility(View.GONE);
                                btnClockOut.setVisibility(View.VISIBLE);
                            } else if (clockOutTime != null) {
                                isClockedIn = false;
                                String statusText = officeName != null ?
                                        "Work Complete at " + officeName + " ✅" : "Work Complete ✅";
                                tvTodayStatus.setText(statusText);
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
                locationUpdateHandler.postDelayed(this, 10000);  // Continue periodic updates every 10 seconds
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
                Log.d(TAG, "✅ Location permission granted, starting location updates");
                startLocationUpdates();
                // ✅ ENHANCED: Immediate refresh after permission granted
                refreshLocationImmediately();
            } else {
                Log.w(TAG, "❌ Location permission denied");
                tvLocationStatus.setText("Location permission required");
                tvDistanceFromOffice.setText("Enable location to use attendance");
            }
        }
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
            Log.d(TAG, "🔄 Manual location refresh requested");
            refreshLocationImmediately();
            Toast.makeText(this, "📍 Location refreshed", Toast.LENGTH_SHORT).show();
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
     * Clock in with device session management and multi-office support
     */
    private void clockIn() {
        if (!LocationUtils.hasLocationPermissions(this)) {
            requestLocationPermissions();
            return;
        }

        if (!isAtAnyOffice || currentOffice == null) {
            Toast.makeText(this, "You must be at an office location to clock in", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        OfficeDetectionResult result = detectOfficeLocation(location);

                        if (result.isAtOffice && result.currentOffice != null) {
                            performClockIn(location.getLatitude(), location.getLongitude(), result.currentOffice);
                        } else {
                            setLoading(false);
                            String message = result.closestOffice != null ?
                                    "You're too far from " + result.closestOffice.name + " to clock in" :
                                    "You're not at any office location";
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
     * Perform clock in with device tracking and office information
     */
    private void performClockIn(double latitude, double longitude, OfficeLocation office) {
        String today = DateTimeUtils.getCurrentDate();
        String currentTime = DateTimeUtils.getCurrentTime();

        // Check if employee is late
        boolean isLate = DateTimeUtils.isLateArrival(currentTime, workStartTime);
        int lateMinutes = isLate ? DateTimeUtils.calculateLateMinutes(currentTime, workStartTime) : 0;

        // Create attendance record with device and office information
        Map<String, Object> attendanceData = new HashMap<>();
        attendanceData.put("employeeDocId", employeeDocId);
        attendanceData.put("pfNumber", pfNumber);
        attendanceData.put("employeeName", employeeName);
        attendanceData.put("date", today);
        attendanceData.put("clockInTime", currentTime);
        attendanceData.put("clockInTimestamp", com.google.firebase.Timestamp.now());
        attendanceData.put("clockInLatitude", latitude);
        attendanceData.put("clockInLongitude", longitude);

        // ✅ ENHANCED: Store office information
        attendanceData.put("officeId", office.id);
        attendanceData.put("officeName", office.name);
        attendanceData.put("locationName", office.name);

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
                        tvTodayStatus.setText("Clocked In at " + office.name + " (Late) ⚠️");
                        Toast.makeText(this, "Clocked in at " + office.name + "! You are " + lateMinutes + " minutes late.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        tvTodayStatus.setText("Clocked In at " + office.name + " ✅");
                        Toast.makeText(this, "Successfully clocked in at " + office.name + "!", Toast.LENGTH_SHORT).show();
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
     * Clock out with session termination and multi-office support
     */
    private void clockOut(String earlyClockOutReason) {
        if (!LocationUtils.hasLocationPermissions(this) || todayAttendanceDocId == null) {
            Toast.makeText(this, "Cannot clock out: No active session", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isAtAnyOffice) {
            Toast.makeText(this, "You must be at an office location to clock out", Toast.LENGTH_LONG).show();
            return;
        }

        setLoading(true);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        OfficeDetectionResult result = detectOfficeLocation(location);

                        if (result.isAtOffice && result.currentOffice != null) {
                            performClockOut(location.getLatitude(), location.getLongitude(), earlyClockOutReason, result.currentOffice);
                        } else {
                            setLoading(false);
                            String message = result.closestOffice != null ?
                                    "You're too far from " + result.closestOffice.name + " to clock out" :
                                    "You're not at any office location";
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
     * Perform clock out with session termination and office information
     */
    private void performClockOut(double latitude, double longitude, String earlyClockOutReason, OfficeLocation office) {
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

        // ✅ ENHANCED: Store clock-out office information
        updates.put("clockOutOfficeId", office.id);
        updates.put("clockOutOfficeName", office.name);

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
                        tvTodayStatus.setText("Early Clock Out from " + office.name + " ⚠️");
                        Toast.makeText(this, "Clocked out early from " + office.name + "! Total hours: " +
                                        DateTimeUtils.formatHoursWorked(hoursWorked) + ". Reason logged for admin review.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        tvTodayStatus.setText("Work Complete at " + office.name + " ✅");
                        Toast.makeText(this, "Successfully clocked out from " + office.name + "! Total hours: " +
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
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "🔄 Activity started - refreshing location automatically");

        // ✅ ENHANCED: Auto-refresh location on activity start
        if (LocationUtils.hasLocationPermissions(this)) {
            refreshLocationImmediately();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🔄 Activity resumed - refreshing validations and location");

        // Re-validate device session when app resumes
        checkDeviceSession();
        validateDeviceTime();

        // ✅ ENHANCED: Auto-refresh location on activity resume
        if (LocationUtils.hasLocationPermissions(this)) {
            refreshLocationImmediately();
        }
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

} // End of AttendanceActivity class