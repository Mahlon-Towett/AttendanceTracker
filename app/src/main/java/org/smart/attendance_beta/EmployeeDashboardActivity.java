package org.smart.attendance_beta;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import org.smart.attendance_beta.utils.DateTimeUtils;
import org.smart.attendance_beta.utils.LocationUtils;

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

        // Load data
        loadUserData();
        loadCompanyLocation();

        // Request location permissions
        requestLocationPermissions();

        // Setup click listeners
        setupClickListeners();
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
        // Get current user's UID
        String currentUserId = mAuth.getCurrentUser().getUid();

        // Query employees collection to find user by Firebase UID or use stored employeeDocId
        if (employeeDocId != null) {
            // Use stored employee document ID
            loadEmployeeByDocId(employeeDocId);
        } else {
            // Fallback: try to find by Firebase UID (in case of Google sign-in users)
            db.collection("employees")
                    .whereEqualTo("email", mAuth.getCurrentUser().getEmail())
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
                    });
        }
    }

    private void loadEmployeeByDocId(String docId) {
        db.collection("employees").document(docId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            loadEmployeeData(document);
                        } else {
                            showError("Employee record not found.");
                        }
                    } else {
                        showError("Error loading employee data: " + task.getException().getMessage());
                    }
                });
    }

    private void loadEmployeeData(DocumentSnapshot employeeDoc) {
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
                            companyLatitude = document.getDouble("latitude");
                            companyLongitude = document.getDouble("longitude");
                            companyRadius = document.getLong("radius").intValue();
                        }
                        // Start location updates after getting company location
                        startLocationUpdates();
                    } else {
                        Toast.makeText(this, "Error loading company location", Toast.LENGTH_SHORT).show();
                        // Use default location and start updates anyway
                        startLocationUpdates();
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
                });
    }

    private void loadWeeklyStats() {
        if (employeeDocId == null) return;

        // Get this week's attendance records
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(7)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();

                        double totalWeeklyHours = 0;
                        int attendanceDays = 0;

                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Double hoursWorked = doc.getDouble("totalHours");
                            if (hoursWorked != null && hoursWorked > 0) {
                                totalWeeklyHours += hoursWorked;
                                attendanceDays++;
                            }
                        }

                        tvWeeklyHours.setText(DateTimeUtils.formatHoursWorked(totalWeeklyHours));
                        tvAttendanceStreak.setText(attendanceDays + " days");
                    }
                });
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
        // Refresh data when returning to dashboard
        loadTodayAttendance();
        loadWeeklyStats();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationUpdateHandler != null && locationUpdateRunnable != null) {
            locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
        }
    }
}