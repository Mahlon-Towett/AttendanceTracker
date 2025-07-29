// Modified LoginActivity.java with device session checking
package org.smart.attendance_beta;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.smart.attendance_beta.notifications.AttendanceNotificationManager;
import org.smart.attendance_beta.utils.DateTimeUtils;
import org.smart.attendance_beta.utils.DeviceSecurityUtils;

public class LoginActivity extends AppCompatActivity {

    private EditText etPfNumber, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Device session management
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Initialize device ID
        deviceId = DeviceSecurityUtils.getDeviceId(this);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        initViews();
        setupClickListeners();

        // Check if user is already logged in (but not auto-login from splash)
        boolean autoLogin = getIntent().getBooleanExtra("auto_login", false);
        if (!autoLogin) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                // User is already logged in, check their role and device session
                checkUserRoleAndDeviceSession();
            }
        }
    }

    private void initViews() {
        etPfNumber = findViewById(R.id.et_pf_number);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvRegister = findViewById(R.id.tv_register);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginWithPfNumber();
            }
        });

        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegistrationActivity.class));
            }
        });
    }

    private void loginWithPfNumber() {
        String pfNumber = etPfNumber.getText().toString().trim().toUpperCase();
        String password = etPassword.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(pfNumber)) {
            etPfNumber.setError("PF Number is required");
            etPfNumber.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        // Show loading
        setLoading(true);

        // First, find the employee by PF Number
        db.collection("employees")
                .whereEqualTo("pfNumber", pfNumber)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        // Employee found, get their details
                        DocumentSnapshot employeeDoc = task.getResult().getDocuments().get(0);
                        String email = employeeDoc.getString("email");
                        String employeeDocId = employeeDoc.getId();

                        if (email != null) {
                            // Check device session before authenticating
                            checkDeviceSessionBeforeLogin(employeeDocId, email, password, employeeDoc);
                        } else {
                            setLoading(false);
                            Toast.makeText(LoginActivity.this,
                                    "Employee email not found. Please contact admin.",
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this,
                                "PF Number not found. Please check your PF Number or register first.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this,
                            "Error connecting to database: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Check if employee has an active session on another device before allowing login
     */
    private void checkDeviceSessionBeforeLogin(String employeeDocId, String email, String password, DocumentSnapshot employeeDoc) {
        String today = DateTimeUtils.getCurrentDate();

        // Check if employee has an active attendance session on another device
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", today)
                .whereEqualTo("sessionActive", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // No active session - can proceed with login
                            authenticateWithFirebase(email, password, employeeDocId, employeeDoc);
                        } else {
                            // Active session found - check if it's on this device
                            DocumentSnapshot activeSession = task.getResult().getDocuments().get(0);
                            String sessionDeviceId = activeSession.getString("deviceId");

                            if (deviceId.equals(sessionDeviceId)) {
                                // Same device - can proceed with login
                                authenticateWithFirebase(email, password, employeeDocId, employeeDoc);
                            } else {
                                // Different device - show device conflict dialog
                                setLoading(false);
                                showDeviceConflictDialog(activeSession, employeeDoc);
                            }
                        }
                    } else {
                        // Error checking session - allow login but log the error
                        Toast.makeText(this, "Warning: Could not verify device session", Toast.LENGTH_SHORT).show();
                        authenticateWithFirebase(email, password, employeeDocId, employeeDoc);
                    }
                });
    }

    /**
     * Show dialog when user tries to login while clocked in on another device
     */
    private void showDeviceConflictDialog(DocumentSnapshot activeSession, DocumentSnapshot employeeDoc) {
        String employeeName = employeeDoc.getString("name");
        String activeDeviceInfo = getActiveDeviceInfo(activeSession);
        String clockInTime = activeSession.getString("clockInTime");

        String message = "You are currently clocked in from another device:\n\n" +
                "Active Device: " + activeDeviceInfo + "\n" +
                "Clock In Time: " + (clockInTime != null ? DateTimeUtils.formatTimeForDisplay(clockInTime) : "Unknown") + "\n" +
                "Current Device: " + DeviceSecurityUtils.getDeviceManufacturer() + " " + DeviceSecurityUtils.getDeviceModel() + "\n\n" +
                "To prevent attendance fraud, you must clock out from your original device before logging in on a new device.\n\n" +
                "What would you like to do?";

        new AlertDialog.Builder(this)
                .setTitle("🚫 Device Session Active")
                .setMessage(message)
                .setPositiveButton("Contact Admin", (dialog, which) -> {
                    // Option to contact admin for help
                    showContactAdminDialog();
                })
                .setNegativeButton("Try Different Account", (dialog, which) -> {
                    // Clear form and let them try different account
                    etPfNumber.setText("");
                    etPassword.setText("");
                    etPfNumber.requestFocus();
                })
                .setNeutralButton("Cancel", null)
                .setCancelable(false)
                .show();
    }

    /**
     * Show contact admin dialog
     */
    private void showContactAdminDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📞 Contact Administrator")
                .setMessage("If you've lost your original device or need help resolving this issue, please contact your administrator.\n\n" +
                        "Provide them with:\n" +
                        "• Your PF Number\n" +
                        "• Current device information\n" +
                        "• Reason for device change\n\n" +
                        "They can resolve device conflicts from the admin panel.")
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Get display info for the active device
     */
    private String getActiveDeviceInfo(DocumentSnapshot session) {
        String deviceModel = session.getString("deviceModel");
        String deviceManufacturer = session.getString("deviceManufacturer");

        if (deviceModel != null && deviceManufacturer != null) {
            return deviceManufacturer + " " + deviceModel;
        } else {
            String deviceId = session.getString("deviceId");
            return "Unknown device" + (deviceId != null ? " (" + deviceId.substring(0, 8) + "...)" : "");
        }
    }

    private void authenticateWithFirebase(String email, String password, String employeeDocId, DocumentSnapshot employeeDoc) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        setLoading(false);

                        if (task.isSuccessful()) {
                            // Sign in success
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                // Store employee data immediately after successful login
                                storeEmployeeData(employeeDocId, employeeDoc);

                                // Small delay to ensure data is stored
                                new android.os.Handler().postDelayed(() -> {
                                    checkUserRoleAndRedirect(employeeDoc);
                                }, 500);
                            }
                        } else {
                            // Sign in failed
                            String errorMessage = "Login failed. Please check your password.";
                            if (task.getException() != null) {
                                String exception = task.getException().getMessage();
                                if (exception != null && exception.contains("password")) {
                                    errorMessage = "Incorrect password. Please try again.";
                                } else if (exception != null && exception.contains("user-not-found")) {
                                    errorMessage = "Account not activated. Please register first.";
                                }
                            }
                            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    /**
     * Store employee data immediately after login
     */
    private void storeEmployeeData(String employeeDocId, DocumentSnapshot employeeDoc) {
        try {
            // Store employee document ID and device info for session management
            getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("employee_doc_id", employeeDocId)
                    .putString("employee_name", employeeDoc.getString("name"))
                    .putString("employee_pf", employeeDoc.getString("pfNumber"))
                    .putString("employee_email", employeeDoc.getString("email"))
                    .putString("employee_department", employeeDoc.getString("department"))
                    .putString("employee_role", employeeDoc.getString("role"))
                    .putString("device_id", deviceId) // Store device ID for session tracking
                    .putLong("login_timestamp", System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            // If storing fails, at least store the essential employee doc ID and device ID
            getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                    .edit()
                    .putString("employee_doc_id", employeeDocId)
                    .putString("device_id", deviceId)
                    .apply();
        }
    }
    /**
     * Check user role and device session for already logged in users
     */
    private void checkUserRoleAndDeviceSession() {
        // Get stored employee doc ID
        String employeeDocId = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                .getString("employee_doc_id", null);

        if (employeeDocId != null) {
            // Check device session for already logged in user
            checkDeviceSessionForExistingUser(employeeDocId);
        } else {
            // No stored employee data, user needs to login
            Toast.makeText(this, "Please login to continue.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Check device session for user who is already logged in (from previous session)
     */
    private void checkDeviceSessionForExistingUser(String employeeDocId) {
        String today = DateTimeUtils.getCurrentDate();

        // Check if employee has an active attendance session on another device
        db.collection("attendance")
                .whereEqualTo("employeeDocId", employeeDocId)
                .whereEqualTo("date", today)
                .whereEqualTo("sessionActive", true)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            // No active session - can proceed to dashboard
                            loadEmployeeAndRedirect(employeeDocId);
                        } else {
                            // Active session found - check if it's on this device
                            DocumentSnapshot activeSession = task.getResult().getDocuments().get(0);
                            String sessionDeviceId = activeSession.getString("deviceId");

                            if (deviceId.equals(sessionDeviceId)) {
                                // Same device - can proceed to dashboard
                                loadEmployeeAndRedirect(employeeDocId);
                            } else {
                                // Different device - force logout and show message
                                forceLogoutDueToDeviceConflict(activeSession);
                            }
                        }
                    } else {
                        // Error checking session - allow access but log the error
                        loadEmployeeAndRedirect(employeeDocId);
                    }
                });
    }

    /**
     * Force logout when device conflict is detected for existing session
     */
    private void forceLogoutDueToDeviceConflict(DocumentSnapshot activeSession) {
        // Clear stored data
        clearStoredData();

        // Sign out from Firebase
        mAuth.signOut();

        String activeDeviceInfo = getActiveDeviceInfo(activeSession);
        String clockInTime = activeSession.getString("clockInTime");

        new AlertDialog.Builder(this)
                .setTitle("🔒 Session Terminated")
                .setMessage("Your session has been terminated because you are currently clocked in from another device:\n\n" +
                        "Active Device: " + activeDeviceInfo + "\n" +
                        "Clock In Time: " + (clockInTime != null ? DateTimeUtils.formatTimeForDisplay(clockInTime) : "Unknown") + "\n\n" +
                        "Please clock out from your original device before using this device.")
                .setPositiveButton("Login Again", (dialog, which) -> {
                    // Allow them to login manually
                    etPfNumber.requestFocus();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Load employee data and redirect to appropriate dashboard
     */
    private void loadEmployeeAndRedirect(String employeeDocId) {
        db.collection("employees").document(employeeDocId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            checkUserRoleAndRedirect(document);
                        } else {
                            // Employee document not found, clear data and force login
                            clearStoredData();
                            mAuth.signOut();
                            Toast.makeText(this, "Employee record not found. Please login again.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // Error loading employee data
                        Toast.makeText(this, "Error loading employee data. Please try again.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void checkUserRoleAndRedirect(DocumentSnapshot employeeDoc) {
        try {
            String role = employeeDoc.getString("role");
            Boolean isActive = employeeDoc.getBoolean("isActive");

            if (isActive != null && !isActive) {
                Toast.makeText(LoginActivity.this,
                        "Your account has been deactivated. Please contact admin.",
                        Toast.LENGTH_LONG).show();
                mAuth.signOut();
                clearStoredData();
                return;
            }

            if ("Admin".equalsIgnoreCase(role) || "Manager".equalsIgnoreCase(role)) {
                redirectToAdminDashboard();
            } else {
                redirectToEmployeeDashboard();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error processing employee data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void redirectToAdminDashboard() {
        // Admins don't need attendance notifications, but setup channel anyway
        AttendanceNotificationManager.setupNotificationChannel(this);

        Intent intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }
    private void redirectToEmployeeDashboard() {
        // Request notification permission first
        requestNotificationPermission();

        // Setup notifications for logged in employee
        setupNotificationsAfterLogin();

        Intent intent = new Intent(LoginActivity.this, EmployeeDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void clearStoredData() {
        getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnLogin.setEnabled(true);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check if user should be auto-logged in when coming from splash
        boolean autoLogin = getIntent().getBooleanExtra("auto_login", false);
        if (autoLogin) {
            // Coming from splash screen, check if user should be auto-logged in
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                setLoading(true);
                checkUserRoleAndDeviceSession();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Clear any previous error states
        etPfNumber.setError(null);
        etPassword.setError(null);

        // Stop loading if it was running
        setLoading(false);
    }

    /**
     * Handle device session validation for auto-login scenarios
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // If this is an auto-login request, check device session
        boolean autoLogin = intent.getBooleanExtra("auto_login", false);
        if (autoLogin) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                checkUserRoleAndDeviceSession();
            }
        }
    }

    /**
     * Override back button to prevent going back to splash
     */
    @Override
    public void onBackPressed() {
        // Exit app instead of going back to splash
        super.onBackPressed();
        finishAffinity();
    }

    /**
     * Handle the case where user logs out from dashboard and returns to login
     */
    public void handleLogoutReturn() {
        // Clear any stored session data
        clearStoredData();

        // Clear form fields
        etPfNumber.setText("");
        etPassword.setText("");

        // Focus on PF Number field
        etPfNumber.requestFocus();

        // Show message
        Toast.makeText(this, "Logged out successfully. Please login again.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Security check: Validate device hasn't been tampered with
     */
    private boolean validateDeviceSecurity() {
        try {
            // Basic device security checks
            String currentDeviceId = DeviceSecurityUtils.getDeviceId(this);

            // Check if device ID has changed (possible tampering)
            String storedDeviceId = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                    .getString("device_id", null);

            if (storedDeviceId != null && !storedDeviceId.equals(currentDeviceId)) {
                // Device ID changed - possible tampering or device change
                new AlertDialog.Builder(this)
                        .setTitle("🔒 Device Security Alert")
                        .setMessage("Device signature has changed. This could indicate:\n\n" +
                                "• Device has been reset\n" +
                                "• App was reinstalled\n" +
                                "• Security tampering\n\n" +
                                "For security, please login again.")
                        .setPositiveButton("Login Again", (dialog, which) -> {
                            clearStoredData();
                            mAuth.signOut();
                        })
                        .setCancelable(false)
                        .show();
                return false;
            }

            return true;
        } catch (Exception e) {
            // If validation fails, allow login but log the error
            return true;
        }
    }

    /**
     * Update stored device ID when it changes legitimately
     */
    private void updateStoredDeviceId() {
        getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                .edit()
                .putString("device_id", deviceId)
                .apply();
    }
    private void setupNotificationsAfterLogin() {
        try {
            // Setup notification channel
            AttendanceNotificationManager.setupNotificationChannel(this);

            // Schedule daily reminders
            AttendanceNotificationManager.scheduleDailyReminders(this);

            Log.d("LoginActivity", "Attendance notifications scheduled successfully");
        } catch (Exception e) {
            Log.e("LoginActivity", "Error setting up notifications: " + e.getMessage());
        }
    }
    // Add to LoginActivity after login
}