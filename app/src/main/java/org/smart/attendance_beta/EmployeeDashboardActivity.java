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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.smart.attendance_beta.utils.FirebaseUtils;

public class EmployeeDashboardActivity extends AppCompatActivity {

    private TextView tvWelcome, tvEmployeeId, tvDepartment;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_dashboard);

        mAuth = FirebaseAuth.getInstance();

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setTitle("Employee Dashboard");
        }

        // Initialize views
        initViews();
        loadUserData();
    }

    private void initViews() {
        tvWelcome = findViewById(R.id.tv_welcome);
        tvEmployeeId = findViewById(R.id.tv_employee_id);
        tvDepartment = findViewById(R.id.tv_department);

        // Set default text if views exist
        if (tvWelcome != null) {
            tvWelcome.setText("Welcome to Employee Dashboard!");
        }
        if (tvEmployeeId != null) {
            tvEmployeeId.setText("Loading...");
        }
        if (tvDepartment != null) {
            tvDepartment.setText("Loading...");
        }
    }

    private void loadUserData() {
        FirebaseUtils.getCurrentUserData(new FirebaseUtils.UserDataCallback() {
            @Override
            public void onUserDataRetrieved(com.google.firebase.firestore.DocumentSnapshot userDocument) {
                String fullName = userDocument.getString("fullName");
                String employeeId = userDocument.getString("employeeId");
                String department = userDocument.getString("department");

                if (tvWelcome != null) {
                    tvWelcome.setText("Welcome, " + (fullName != null ? fullName : "Employee") + "!");
                }
                if (tvEmployeeId != null) {
                    tvEmployeeId.setText("ID: " + (employeeId != null ? employeeId : "N/A"));
                }
                if (tvDepartment != null) {
                    tvDepartment.setText("Department: " + (department != null ? department : "N/A"));
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(EmployeeDashboardActivity.this,
                        "Error loading user data: " + error, Toast.LENGTH_SHORT).show();
            }
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
            Toast.makeText(this, "Profile clicked", Toast.LENGTH_SHORT).show();
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
}