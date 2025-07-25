package org.smart.attendance_beta;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.smart.attendance_beta.utils.FirebaseUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AdminDashboardActivity extends AppCompatActivity {

    private TextView tvTotalEmployees, tvPresentToday, tvLateArrivals, tvAvgHours;
    private CardView cvTotalEmployees, cvPresentToday, cvLateArrivals, cvAvgHours;
    private TextView tvWelcomeAdmin, tvLastUpdate;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setTitle("Admin Dashboard");
        }

        // Initialize views
        initViews();

        // Load admin data
        loadAdminProfile();
        loadDashboardData();
    }

    private void initViews() {
        // Welcome text
        tvWelcomeAdmin = findViewById(R.id.tv_welcome_admin);
        tvLastUpdate = findViewById(R.id.tv_last_update);

        // Statistics cards
        tvTotalEmployees = findViewById(R.id.tv_total_employees);
        tvPresentToday = findViewById(R.id.tv_present_today);
        tvLateArrivals = findViewById(R.id.tv_late_arrivals);
        tvAvgHours = findViewById(R.id.tv_avg_hours);

        // Card views for click listeners
        cvTotalEmployees = findViewById(R.id.cv_total_employees);
        cvPresentToday = findViewById(R.id.cv_present_today);
        cvLateArrivals = findViewById(R.id.cv_late_arrivals);
        cvAvgHours = findViewById(R.id.cv_avg_hours);

        // Setup click listeners for stats cards
        setupStatsCardListeners();

        // Set default values
        setDefaultValues();
    }

    private void setDefaultValues() {
        if (tvWelcomeAdmin != null) {
            tvWelcomeAdmin.setText("Welcome, Admin!");
        }
        if (tvLastUpdate != null) {
            String currentTime = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date());
            tvLastUpdate.setText("Last updated: " + currentTime);
        }
        if (tvTotalEmployees != null) {
            tvTotalEmployees.setText("0");
        }
        if (tvPresentToday != null) {
            tvPresentToday.setText("0");
        }
        if (tvLateArrivals != null) {
            tvLateArrivals.setText("0");
        }
        if (tvAvgHours != null) {
            tvAvgHours.setText("0.0");
        }
    }

    private void loadAdminProfile() {
        FirebaseUtils.getCurrentUserData(new FirebaseUtils.UserDataCallback() {
            @Override
            public void onUserDataRetrieved(com.google.firebase.firestore.DocumentSnapshot userDocument) {
                String fullName = userDocument.getString("fullName");
                if (tvWelcomeAdmin != null && fullName != null) {
                    tvWelcomeAdmin.setText("Welcome, " + fullName + "!");
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Error loading admin profile: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupStatsCardListeners() {
        if (cvTotalEmployees != null) {
            cvTotalEmployees.setOnClickListener(v ->
                    Toast.makeText(this, "Total Employees: View all employees", Toast.LENGTH_SHORT).show());
        }

        if (cvPresentToday != null) {
            cvPresentToday.setOnClickListener(v ->
                    Toast.makeText(this, "Present Today: View present employees", Toast.LENGTH_SHORT).show());
        }

        if (cvLateArrivals != null) {
            cvLateArrivals.setOnClickListener(v ->
                    Toast.makeText(this, "Late Arrivals: View late employees", Toast.LENGTH_SHORT).show());
        }

        if (cvAvgHours != null) {
            cvAvgHours.setOnClickListener(v ->
                    Toast.makeText(this, "Average Hours: View hours report", Toast.LENGTH_SHORT).show());
        }
    }

    private void loadDashboardData() {
        // Load total employees count
        db.collection("users")
                .whereEqualTo("isActive", true)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            int totalEmployees = task.getResult().size();
                            if (tvTotalEmployees != null) {
                                tvTotalEmployees.setText(String.valueOf(totalEmployees));
                            }

                            // Update last update time
                            if (tvLastUpdate != null) {
                                String currentTime = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date());
                                tvLastUpdate.setText("Last updated: " + currentTime);
                            }
                        } else {
                            Toast.makeText(AdminDashboardActivity.this,
                                    "Error loading employee count: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        // Get today's date for attendance queries
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // For now, set placeholder values for attendance data
        // TODO: Implement actual attendance queries when attendance tracking is built
        if (tvPresentToday != null) {
            tvPresentToday.setText("0");
        }
        if (tvLateArrivals != null) {
            tvLateArrivals.setText("0");
        }
        if (tvAvgHours != null) {
            tvAvgHours.setText("0.0");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.admin_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_reports) {
            Toast.makeText(this, "Reports feature coming soon!", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_settings) {
            Toast.makeText(this, "Settings feature coming soon!", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            showLogoutDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // Click handlers for quick action buttons
    public void onManageEmployeesClick(android.view.View view) {
        Toast.makeText(this, "Employee management feature coming soon!", Toast.LENGTH_SHORT).show();
    }

    public void onSetupLocationsClick(android.view.View view) {
        Toast.makeText(this, "Location setup feature coming soon!", Toast.LENGTH_SHORT).show();
    }

    public void onGenerateReportsClick(android.view.View view) {
        Toast.makeText(this, "Reports feature coming soon!", Toast.LENGTH_SHORT).show();
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    mAuth.signOut();
                    Intent intent = new Intent(AdminDashboardActivity.this, LoginActivity.class);
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
        loadDashboardData();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Verify user is still admin
        FirebaseUtils.getCurrentUserRole(new FirebaseUtils.UserRoleCallback() {
            @Override
            public void onRoleRetrieved(String role, boolean isActive) {
                if (!"admin".equals(role) || !isActive) {
                    // User is no longer admin or deactivated
                    Toast.makeText(AdminDashboardActivity.this,
                            "Access denied. Redirecting to login.", Toast.LENGTH_LONG).show();
                    mAuth.signOut();
                    Intent intent = new Intent(AdminDashboardActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Error checking permissions: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}