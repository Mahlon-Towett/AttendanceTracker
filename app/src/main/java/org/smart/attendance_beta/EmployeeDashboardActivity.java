package org.smart.attendance_beta;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.smart.attendance_beta.notifications.AttendanceNotificationManager;
import org.smart.attendance_beta.utils.DateTimeUtils;
import org.smart.attendance_beta.utils.LocationUtils;
import org.smart.attendance_beta.utils.WeeklyAttendanceUtils;

import java.text.DecimalFormat;

public class EmployeeDashboardActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI Components
    private TextView tvWelcome, tvEmployeeId, tvDepartment;
    private TextView tvLocationStatus, tvDistanceFromOffice, tvClockInTime, tvClockOutTime;
    private TextView tvHoursWorked, tvWeeklyHours, tvAttendanceStreak;
    private CardView cvAttendance, cvLocationStatus, cvWeeklyStats;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;

    // Location and Employee Data
    private double companyLatitude = -1.2921; // Default Nairobi coordinates
    private double companyLongitude = 36.8219;
    private int companyRadius = 200; // 200 meters
    private String employeeDocId;
    private String pfNumber;
    private String employeeName;
    private String department;

    // Update Handler
    private Handler locationUpdateHandler = new Handler();
    private Runnable locationUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_dashboard);
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Get stored employee data
        employeeDocId = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                .getString("employee_doc_id", null);

        // Initialize views
        initViews();

        // FIXED: Check if user is authenticated before proceeding
        if (mAuth.getCurrentUser() == null) {
            // User not authenticated, redirect to login
            Toast.makeText(this, "Authentication required. Please login again.", Toast.LENGTH_LONG).show();
            redirectToLogin();
            return;
        }

        // Load data
        loadUserData();
        loadCompanyLocation();

        // Request location permissions
        requestLocationPermissions();

        // Setup click listeners
        setupClickListeners();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(EmployeeDashboardActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initViews() {
        tvWelcome = findViewById(R.id.tv_welcome);
        tvEmployeeId = findViewById(R.id.tv_employee_id);
        tvDepartment = findViewById(R.id.tv_department);

        tvLocationStatus = findViewById(R.id.tv_location_status);
        tvDistanceFromOffice = findViewById(R.id.tv_distance_from_office);
        tvClockInTime = findViewById(R.id.tv_clock_in_time);
        tvClockOutTime = findViewById(R.id.tv_clock_out_time);
        tvHoursWorked = findViewById(R.id.tv_hours_worked);
        tvWeeklyHours = findViewById(R.id.tv_weekly_hours);
        tvAttendanceStreak = findViewById(R.id.tv_attendance_streak);

        cvAttendance = findViewById(R.id.cv_attendance);
        cvLocationStatus = findViewById(R.id.cv_location_status);
        cvWeeklyStats = findViewById(R.id.cv_weekly_stats);

        // Set default values
        setDefaultValues();
    }

    private void setDefaultValues() {
        String greeting = DateTimeUtils.getTimeBasedGreeting();
        tvWelcome.setText(greeting + "!");
        tvEmployeeId.setText("Loading...");
        tvDepartment.setText("Loading...");
        tvLocationStatus.setText("Checking location...");
        tvDistanceFromOffice.setText("--");
        tvClockInTime.setText("--:--");
        tvClockOutTime.setText("--:--");
        tvHoursWorked.setText("0h 0m");
        tvWeeklyHours.setText("0h 0m");
        tvAttendanceStreak.setText("0 days");
    }

    private void loadUserData() {
        // FIXED: Add null check for current user
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showError("User not authenticated. Please login again.");
            redirectToLogin();
            return;
        }

        // Get current user's UID safely
        String currentUserId = currentUser.getUid();
        if (currentUserId == null) {
            showError("Unable to get user ID. Please login again.");
            redirectToLogin();
            return;
        }

        // Query employees collection to find user by Firebase UID or use stored employeeDocId
        if (employeeDocId != null) {
            // Use stored employee document ID
            loadEmployeeByDocId(employeeDocId);
        } else {
            // Fallback: try to find by Firebase UID (in case of Google sign-in users)
            String userEmail = currentUser.getEmail();
            if (userEmail != null) {
                db.collection("employees")
                        .whereEqualTo("email", userEmail)
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && !task.getResult().isEmpty()) {
                                DocumentSnapshot employeeDoc = task.getResult().getDocuments().get(0);
                                employeeDocId = employeeDoc.getId();

                                // Store for future use
                                getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                                        .edit()
                                        .putString("employee_doc_id", employeeDocId)
                                        .apply();

                                loadEmployeeData(employeeDoc);
                            } else {
                                showError("Employee data not found. Please contact HR.");
                            }
                        })
                        .addOnFailureListener(e -> {
                            showError("Error loading employee data: " + e.getMessage());
                        });
            } else {
                showError("User email not found. Please login again.");
                redirectToLogin();
            }
        }
    }

    private void loadEmployeeByDocId(String docId) {
        if (docId == null || docId.isEmpty()) {
            showError("Invalid employee ID. Please login again.");
            redirectToLogin();
            return;
        }

        db.collection("employees").document(docId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            loadEmployeeData(document);
                        } else {
                            showError("Employee record not found.");
                            // Clear stored employee ID if record doesn't exist
                            getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                                    .edit()
                                    .remove("employee_doc_id")
                                    .apply();
                            redirectToLogin();
                        }
                    } else {
                        showError("Error loading employee data: " + task.getException().getMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    showError("Failed to load employee data: " + e.getMessage());
                });
    }

    private void loadEmployeeData(DocumentSnapshot employeeDoc) {
        try {
            employeeName = employeeDoc.getString("name");
            pfNumber = employeeDoc.getString("pfNumber");
            department = employeeDoc.getString("department");

            // Update UI with employee data
            if (employeeName != null) {
                String firstName = getFirstName(employeeName);
                String greeting = DateTimeUtils.getTimeBasedGreeting();
                tvWelcome.setText(greeting + ", " + firstName + "!");
            }

            if (pfNumber != null) {
                tvEmployeeId.setText("PF: " + pfNumber);
            }

            if (department != null) {
                tvDepartment.setText(department);
            }

            // Load attendance data after getting employee info
            loadTodayAttendance();
            loadWeeklyStats();
        } catch (Exception e) {
            showError("Error processing employee data: " + e.getMessage());
        }
    }

    private String getFirstName(String fullName) {
        if (fullName != null && fullName.contains(" ")) {
            return fullName.split(" ")[0];
        }
        return fullName != null ? fullName : "Employee";
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        // Set error state in UI
        tvWelcome.setText("Error loading profile");
        tvEmployeeId.setText("Error");
        tvDepartment.setText("Contact HR");
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
                        // Start location updates after getting company location
                        startLocationUpdates();
                    } else {
                        Toast.makeText(this, "Error loading company location", Toast.LENGTH_SHORT).show();
                        // Use default location and start updates anyway
                        startLocationUpdates();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load company location", Toast.LENGTH_SHORT).show();
                    startLocationUpdates();
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

                        String clockInTime = attendance.getString("clockInTime");
                        String clockOutTime = attendance.getString("clockOutTime");

                        if (clockInTime != null) {
                            tvClockInTime.setText(DateTimeUtils.formatTimeForDisplay(clockInTime));
                        }

                        if (clockOutTime != null) {
                            tvClockOutTime.setText(DateTimeUtils.formatTimeForDisplay(clockOutTime));

                            // Calculate hours worked
                            double hours = DateTimeUtils.calculateHoursWorked(clockInTime, clockOutTime);
                            tvHoursWorked.setText(DateTimeUtils.formatHoursWorked(hours));
                        } else if (clockInTime != null) {
                            // Still clocked in, calculate current hours
                            String currentTime = DateTimeUtils.getCurrentTime();
                            double hours = DateTimeUtils.calculateHoursWorked(clockInTime, currentTime);
                            tvHoursWorked.setText(DateTimeUtils.formatHoursWorked(hours) + " (ongoing)");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Silently handle failure for attendance data
                    // No need to show error as this is optional data
                });
    }

    private void loadWeeklyStats() {
        if (employeeDocId == null) return;

        // Load weekly stats with comprehensive metrics
        WeeklyAttendanceUtils.loadWeeklyStats(employeeDocId, new WeeklyAttendanceUtils.WeeklyStatsCallback() {
            @Override
            public void onStatsLoaded(WeeklyAttendanceUtils.WeeklyStats stats) {
                updateWeeklyStatsUI(stats);

                // Load attendance trend for additional insights
                WeeklyAttendanceUtils.getAttendanceTrend(employeeDocId, new WeeklyAttendanceUtils.TrendCallback() {
                    @Override
                    public void onTrendCalculated(WeeklyAttendanceUtils.AttendanceTrend trend) {
                        updateTrendUI(stats, trend);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e("WeeklyStats", "Error loading trend: " + error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e("WeeklyStats", "Error loading weekly stats: " + error);
                // Show fallback data
                showFallbackWeeklyStats();
            }
        });
    }

    /**
     * Update UI with comprehensive weekly statistics
     */
    private void updateWeeklyStatsUI(WeeklyAttendanceUtils.WeeklyStats stats) {
        // Update total hours
        if (tvWeeklyHours != null) {
            tvWeeklyHours.setText(WeeklyAttendanceUtils.formatHoursWorked(stats.totalHours));
        }

        // Update attendance streak/days
        if (tvAttendanceStreak != null) {
            tvAttendanceStreak.setText(stats.daysPresent + "/5 days");
        }

        // Update weekly stats card with detailed information
        updateWeeklyStatsCard(stats);
    }

    /**
     * Update weekly stats card with detailed metrics
     */
    private void updateWeeklyStatsCard(WeeklyAttendanceUtils.WeeklyStats stats) {
        // Find or create detailed stats views
        TextView tvWeekRange = findViewById(R.id.tv_week_range);
        TextView tvAttendancePercentage = findViewById(R.id.tv_attendance_percentage);
        TextView tvPerformanceBadge = findViewById(R.id.tv_performance_badge);
        TextView tvWeeklyDetails = findViewById(R.id.tv_weekly_details);

        if (tvWeekRange != null) {
            tvWeekRange.setText("Week of " + stats.weekRange);
        }

        if (tvAttendancePercentage != null) {
            tvAttendancePercentage.setText(String.format("%.0f%%", stats.attendancePercentage));

            // Color code based on performance
            int color;
            if (stats.attendancePercentage >= 100) {
                color = getResources().getColor(R.color.green_600);
            } else if (stats.attendancePercentage >= 80) {
                color = getResources().getColor(R.color.blue_600);
            } else if (stats.attendancePercentage >= 60) {
                color = getResources().getColor(R.color.orange_600);
            } else {
                color = getResources().getColor(R.color.red_600);
            }
            tvAttendancePercentage.setTextColor(color);
        }

        if (tvPerformanceBadge != null) {
            tvPerformanceBadge.setText(WeeklyAttendanceUtils.getPerformanceBadge(stats));
        }

        if (tvWeeklyDetails != null) {
            String details = String.format(
                    "Average: %s/day • Late: %d days • Total: %s",
                    WeeklyAttendanceUtils.formatHoursWorked(stats.averageHours),
                    stats.daysLate,
                    WeeklyAttendanceUtils.formatHoursWorked(stats.totalHours)
            );
            tvWeeklyDetails.setText(details);
        }
    }

    /**
     * Update UI with attendance trend information
     */
    private void updateTrendUI(WeeklyAttendanceUtils.WeeklyStats stats, WeeklyAttendanceUtils.AttendanceTrend trend) {
        // Find trend views
        TextView tvTrendMessage = findViewById(R.id.tv_trend_message);
        TextView tvMotivationalMessage = findViewById(R.id.tv_motivational_message);

        if (tvTrendMessage != null) {
            tvTrendMessage.setText(trend.message);

            // Color code based on trend
            int color;
            switch (trend.direction) {
                case "improving":
                    color = getResources().getColor(R.color.green_600);
                    break;
                case "declining":
                    color = getResources().getColor(R.color.red_600);
                    break;
                default:
                    color = getResources().getColor(R.color.blue_600);
                    break;
            }
            tvTrendMessage.setTextColor(color);
        }

        if (tvMotivationalMessage != null) {
            String motivationalMessage = WeeklyAttendanceUtils.getMotivationalMessage(stats, trend);
            tvMotivationalMessage.setText(motivationalMessage);
        }
    }

    /**
     * Show fallback weekly stats when data loading fails
     */
    private void showFallbackWeeklyStats() {
        if (tvWeeklyHours != null) {
            tvWeeklyHours.setText("--");
        }
        if (tvAttendanceStreak != null) {
            tvAttendanceStreak.setText("-- days");
        }
    }
    private void requestLocationPermissions() {
        if (!LocationUtils.hasLocationPermissions(this)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                tvLocationStatus.setText("Location permission denied");
                tvDistanceFromOffice.setText("Enable location to track attendance");
            }
        }
    }

    private void startLocationUpdates() {
        if (!LocationUtils.hasLocationPermissions(this)) {
            return;
        }

        locationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateLocation();
                locationUpdateHandler.postDelayed(this, 10000); // Update every 10 seconds
            }
        };

        locationUpdateHandler.post(locationUpdateRunnable);
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
                    } else {
                        tvLocationStatus.setText("Unable to get location");
                        tvDistanceFromOffice.setText("Check GPS settings");
                    }
                })
                .addOnFailureListener(this, e -> {
                    tvLocationStatus.setText("Location error");
                    tvDistanceFromOffice.setText("Please check GPS");
                });
    }

    private void updateLocationUI(double distance) {
        DecimalFormat df = new DecimalFormat("#.#");

        // Update distance display
        tvDistanceFromOffice.setText(LocationUtils.formatDistance(distance) + " from office");

        // Update status
        String status = LocationUtils.getLocationStatus(distance, companyRadius);
        tvLocationStatus.setText(status);

        // Update card background color based on location
        if (distance <= companyRadius) {
            cvLocationStatus.setCardBackgroundColor(getResources().getColor(R.color.green_50));
        } else if (distance <= companyRadius + 100) {
            cvLocationStatus.setCardBackgroundColor(getResources().getColor(R.color.orange_50));
        } else {
            cvLocationStatus.setCardBackgroundColor(getResources().getColor(R.color.red_50));
        }
    }

    private void setupClickListeners() {
        cvAttendance.setOnClickListener(v -> {
            Intent intent = new Intent(EmployeeDashboardActivity.this, AttendanceActivity.class);
            // Pass employee data to attendance activity
            intent.putExtra("employee_doc_id", employeeDocId);
            intent.putExtra("pf_number", pfNumber);
            intent.putExtra("employee_name", employeeName);
            startActivity(intent);
        });

        cvLocationStatus.setOnClickListener(v -> {
            if (!LocationUtils.hasLocationPermissions(this)) {
                requestLocationPermissions();
            } else {
                updateLocation();
                Toast.makeText(this, "Location updated", Toast.LENGTH_SHORT).show();
            }
        });

        cvWeeklyStats.setOnClickListener(v -> {
            Toast.makeText(this, "Weekly reports feature coming soon!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.employee_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_profile) {
            Toast.makeText(this, "Profile feature coming soon!", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            showLogoutDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Cancel all scheduled notifications before logout
                    AttendanceNotificationManager.cancelAllReminders(this);

                    // Clear stored data
                    getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();

                    mAuth.signOut();
                    Intent intent = new Intent(EmployeeDashboardActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // FIXED: Check authentication state on resume
        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
            return;
        }

        // Refresh data when returning to dashboard
        loadTodayAttendance();
        loadWeeklyStats();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // FIXED: Check authentication state on start
        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
            return;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationUpdateHandler != null && locationUpdateRunnable != null) {
            locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
        }
    }
}