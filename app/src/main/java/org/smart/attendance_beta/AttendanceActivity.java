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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.smart.attendance_beta.models.AttendanceRecord;
import org.smart.attendance_beta.utils.DateTimeUtils;
import org.smart.attendance_beta.utils.LocationUtils;

import java.util.HashMap;
import java.util.Map;

public class AttendanceActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI Components
    private TextView tvCurrentTime, tvLocationStatus, tvDistanceFromOffice;
    private TextView tvTodayStatus, tvClockInTime, tvClockOutTime, tvHoursWorked;
    private Button btnClockIn, btnClockOut;
    private ProgressBar progressBar;
    private CardView cvLocationInfo, cvAttendanceInfo;

    // Firebase
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;

    // Location and Employee Data
    private double companyLatitude = -1.2921; // Default Nairobi coordinates
    private double companyLongitude = 36.8219;
    private int companyRadius = 200; // 200 meters
    private String employeeDocId;
    private String pfNumber;
    private String employeeName;
    private boolean isClockedIn = false;
    private String todayAttendanceDocId = null;
    private String workStartTime = "08:00"; // Normal work start time
    private String workEndTime = "17:00";   // Normal work end time

    // Location updates
    private Handler locationUpdateHandler = new Handler();
    private Runnable locationUpdateRunnable;
    private Handler timeUpdateHandler = new Handler();
    private Runnable timeUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Attendance");

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Get employee data from intent or SharedPreferences
        getEmployeeData();

        // Initialize views
        initViews();

        // Load data
        loadCompanyLocation();
        loadTodayAttendance();

        // Start updates
        startTimeUpdates();
        startLocationUpdates();

        // Setup click listeners
        setupClickListeners();
    }

    private void getEmployeeData() {
        // Try to get from intent first
        employeeDocId = getIntent().getStringExtra("employee_doc_id");
        pfNumber = getIntent().getStringExtra("pf_number");
        employeeName = getIntent().getStringExtra("employee_name");

        // Fallback to SharedPreferences
        if (employeeDocId == null) {
            employeeDocId = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                    .getString("employee_doc_id", null);
        }

        // If still null, load from Firestore
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
        btnClockIn = findViewById(R.id.btn_clock_in);
        btnClockOut = findViewById(R.id.btn_clock_out);
        progressBar = findViewById(R.id.progress_bar);
        cvLocationInfo = findViewById(R.id.cv_location_info);
        cvAttendanceInfo = findViewById(R.id.cv_attendance_info);

        // Set default values
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
                            companyLatitude = document.getDouble("latitude");
                            companyLongitude = document.getDouble("longitude");
                            companyRadius = document.getLong("radius").intValue();
                            workStartTime = document.getString("startTime");
                            workEndTime = document.getString("endTime");
                        }
                    }
                });
    }

    private void loadTodayAttendance() {
        if (employeeDocId == null) return;

        String today = DateTimeUtils.getCurrentDate();

        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", today)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot attendance = task.getResult().getDocuments().get(0);
                        todayAttendanceDocId = attendance.getId();

                        String clockInTime = attendance.getString("clockInTime");
                        String clockOutTime = attendance.getString("clockOutTime");

                        if (clockInTime != null) {
                            tvClockInTime.setText(DateTimeUtils.formatTimeForDisplay(clockInTime));
                            isClockedIn = clockOutTime == null;

                            if (isClockedIn) {
                                tvTodayStatus.setText("Clocked In ✅");
                                btnClockIn.setVisibility(View.GONE);
                                btnClockOut.setVisibility(View.VISIBLE);
                            } else {
                                tvTodayStatus.setText("Work Complete ✅");
                                tvClockOutTime.setText(DateTimeUtils.formatTimeForDisplay(clockOutTime));

                                // Calculate and display hours worked
                                double hours = DateTimeUtils.calculateHoursWorked(clockInTime, clockOutTime);
                                tvHoursWorked.setText(DateTimeUtils.formatHoursWorked(hours));

                                btnClockIn.setVisibility(View.GONE);
                                btnClockOut.setVisibility(View.GONE);
                            }
                        }
                    } else {
                        // No attendance record for today
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

                // Update hours worked if clocked in
                if (isClockedIn && !tvClockInTime.getText().toString().equals("--:--")) {
                    String clockInTime = tvClockInTime.getText().toString() + ":00"; // Add seconds
                    String currentTime = DateTimeUtils.getCurrentTime();
                    double hours = DateTimeUtils.calculateHoursWorked(clockInTime, currentTime);
                    tvHoursWorked.setText(DateTimeUtils.formatHoursWorked(hours) + " (ongoing)");
                }

                timeUpdateHandler.postDelayed(this, 1000); // Update every second
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
                locationUpdateHandler.postDelayed(this, 5000); // Update every 5 seconds
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
                        btnClockIn.setEnabled(false);
                        btnClockOut.setEnabled(false);
                    }
                })
                .addOnFailureListener(this, e -> {
                    tvLocationStatus.setText("Location error");
                    tvDistanceFromOffice.setText("Please check GPS");
                    btnClockIn.setEnabled(false);
                    btnClockOut.setEnabled(false);
                });
    }

    private void updateLocationUI(double distance) {
        // Update distance display
        tvDistanceFromOffice.setText(LocationUtils.formatDistance(distance) + " from office");

        // Update status and card color
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

    private void updateButtonStates() {
        // Get current location to check if in range
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
                            // Already clocked in, enable clock out if in range
                            btnClockOut.setEnabled(inRange);
                            btnClockIn.setEnabled(false);
                        } else if (todayAttendanceDocId == null) {
                            // Not clocked in today, enable clock in if in range
                            btnClockIn.setEnabled(inRange);
                            btnClockOut.setEnabled(false);
                        } else {
                            // Already completed for today
                            btnClockIn.setEnabled(false);
                            btnClockOut.setEnabled(false);
                        }
                    }
                });
    }

    private void setupClickListeners() {
        btnClockIn.setOnClickListener(v -> clockIn());
        btnClockOut.setOnClickListener(v -> {
            // Check if it's early clock out
            if (isEarlyClockOut()) {
                showEarlyClockOutDialog();
            } else {
                clockOut(null); // Normal clock out, no reason needed
            }
        });

        cvLocationInfo.setOnClickListener(v -> {
            updateLocation();
            Toast.makeText(this, "Location refreshed", Toast.LENGTH_SHORT).show();
        });
    }

    private boolean isEarlyClockOut() {
        String currentTime = DateTimeUtils.getCurrentTime();
        try {
            // Parse times for comparison
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date current = timeFormat.parse(currentTime);
            java.util.Date workEnd = timeFormat.parse(workEndTime + ":00");

            return current.before(workEnd);
        } catch (java.text.ParseException e) {
            return false; // If parsing fails, assume normal clock out
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

    private void performClockIn(double latitude, double longitude) {
        String today = DateTimeUtils.getCurrentDate();

        AttendanceRecord record = new AttendanceRecord(
                employeeDocId, pfNumber, employeeName, today,
                latitude, longitude, "Company Office"
        );

        // Check if employee is late
        String currentTime = DateTimeUtils.getCurrentTime();
        boolean isLate = DateTimeUtils.isLateArrival(currentTime, workStartTime);
        int lateMinutes = isLate ? DateTimeUtils.calculateLateMinutes(currentTime, workStartTime) : 0;

        // Convert to map for Firestore
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

        db.collection("attendance")
                .add(attendanceData)
                .addOnSuccessListener(documentReference -> {
                    setLoading(false);
                    todayAttendanceDocId = documentReference.getId();
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

    private void clockOut(String earlyClockOutReason) {
        if (!LocationUtils.hasLocationPermissions(this) || todayAttendanceDocId == null) {
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
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Location error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void performClockOut(double latitude, double longitude, String earlyClockOutReason) {
        String currentTime = DateTimeUtils.getCurrentTime();

        // Calculate hours worked
        String clockInTime = tvClockInTime.getText().toString() + ":00"; // Add seconds
        double hoursWorked = DateTimeUtils.calculateHoursWorked(clockInTime, currentTime);

        Map<String, Object> updates = new HashMap<>();
        updates.put("clockOutTime", currentTime);
        updates.put("clockOutTimestamp", com.google.firebase.Timestamp.now());
        updates.put("clockOutLatitude", latitude);
        updates.put("clockOutLongitude", longitude);
        updates.put("totalHours", hoursWorked);

        // Add early clock out reason if provided
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
}