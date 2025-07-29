package org.smart.attendance_beta;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_NEW_USER = 2000; // 2 seconds for new users only
    private static final int SPLASH_DELAY_LOGGED_IN = 500;  // 0.5 seconds for logged-in users
    private static final String TAG = "SplashActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        TextView tvAppName = findViewById(R.id.tv_app_name);
        TextView tvTagline = findViewById(R.id.tv_tagline);

        if (tvAppName != null) {
            tvAppName.setText("TimeTracker Pro");
        }
        if (tvTagline != null) {
            tvTagline.setText("Smart Attendance Management");
        }

        // ✅ FIXED: Immediate authentication check for logged-in users
        checkAuthenticationImmediately();
    }

    private void checkAuthenticationImmediately() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            // ✅ User is already logged in - redirect immediately with minimal delay
            Log.d(TAG, "🔄 User already logged in, redirecting immediately");

            new Handler().postDelayed(() -> {
                checkUserRoleAndRedirectDirectly(currentUser);
            }, SPLASH_DELAY_LOGGED_IN); // Very short delay for smooth transition

        } else {
            // ✅ User not logged in - show splash for 2 seconds then go to login
            Log.d(TAG, "👤 No user logged in, showing splash then login");

            new Handler().postDelayed(() -> {
                redirectToLogin();
            }, SPLASH_DELAY_NEW_USER);
        }
    }

    /**
     * Check user role and redirect directly to appropriate dashboard
     */
    private void checkUserRoleAndRedirectDirectly(FirebaseUser user) {
        String userEmail = user.getEmail();

        if (userEmail == null) {
            Log.e(TAG, "❌ User email is null, redirecting to login");
            redirectToLogin();
            return;
        }

        Log.d(TAG, "🔍 Checking role for user: " + userEmail);

        // Check if user is in employees collection
        db.collection("employees")
                .whereEqualTo("email", userEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(employeeQuery -> {
                    if (!employeeQuery.isEmpty()) {
                        // User found in employees collection
                        DocumentSnapshot employeeDoc = employeeQuery.getDocuments().get(0);
                        String role = employeeDoc.getString("role");
                        String employeeDocId = employeeDoc.getId();

                        // ✅ Store employee data for session
                        storeEmployeeData(employeeDocId, employeeDoc);

                        if ("admin".equalsIgnoreCase(role)) {
                            Log.d(TAG, "👑 Admin user detected, redirecting to AdminDashboard");
                            redirectToAdminDashboard();
                        } else {
                            Log.d(TAG, "👤 Employee user detected, redirecting to EmployeeDashboard");
                            redirectToEmployeeDashboard();
                        }
                    } else {
                        // User not found in employees collection, check if admin
                        checkIfUserIsAdmin(user);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error checking employee role: " + e.getMessage());
                    // Fallback to login on error
                    Toast.makeText(this, "Error checking user role. Please login again.", Toast.LENGTH_SHORT).show();
                    redirectToLogin();
                });
    }

    /**
     * Check if user is admin in users collection (fallback)
     */
    private void checkIfUserIsAdmin(FirebaseUser user) {
        db.collection("users")
                .whereEqualTo("email", user.getEmail())
                .limit(1)
                .get()
                .addOnSuccessListener(userQuery -> {
                    if (!userQuery.isEmpty()) {
                        DocumentSnapshot userDoc = userQuery.getDocuments().get(0);
                        String role = userDoc.getString("role");

                        if ("admin".equalsIgnoreCase(role)) {
                            Log.d(TAG, "👑 Admin found in users collection, redirecting to AdminDashboard");
                            redirectToAdminDashboard();
                        } else {
                            Log.d(TAG, "👤 Regular user found, redirecting to EmployeeDashboard");
                            redirectToEmployeeDashboard();
                        }
                    } else {
                        Log.w(TAG, "⚠️ User not found in any collection, redirecting to login");
                        Toast.makeText(this, "User profile not found. Please contact administrator.", Toast.LENGTH_LONG).show();
                        redirectToLogin();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error checking admin role: " + e.getMessage());
                    redirectToLogin();
                });
    }

    /**
     * Store employee data for session management
     */
    private void storeEmployeeData(String employeeDocId, DocumentSnapshot employeeDoc) {
        try {
            getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("employee_doc_id", employeeDocId)
                    .putString("employee_name", employeeDoc.getString("name"))
                    .putString("employee_pf", employeeDoc.getString("pfNumber"))
                    .putString("employee_email", employeeDoc.getString("email"))
                    .putString("employee_department", employeeDoc.getString("department"))
                    .putString("employee_role", employeeDoc.getString("role"))
                    .putLong("login_timestamp", System.currentTimeMillis())
                    .apply();

            Log.d(TAG, "💾 Employee data stored for session");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error storing employee data: " + e.getMessage());
            // Store at least the essential data
            getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("employee_doc_id", employeeDocId)
                    .apply();
        }
    }

    /**
     * Redirect to Admin Dashboard
     */
    private void redirectToAdminDashboard() {
        Intent intent = new Intent(SplashActivity.this, AdminDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Redirect to Employee Dashboard
     */
    private void redirectToEmployeeDashboard() {
        Intent intent = new Intent(SplashActivity.this, EmployeeDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Redirect to Login Activity
     */
    private void redirectToLogin() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}