package org.smart.attendance_beta;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etPfNumber, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        initViews();
        setupClickListeners();

        // Check if user is already logged in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            checkUserRoleAndRedirect(currentUser.getUid());
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

                        if (email != null) {
                            // Authenticate with Firebase Auth using generated email
                            authenticateWithFirebase(email, password, employeeDoc.getId());
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
                });
    }

    private void authenticateWithFirebase(String email, String password, String employeeDocId) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        setLoading(false);

                        if (task.isSuccessful()) {
                            // Sign in success
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                checkUserRoleAndRedirect(employeeDocId);
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

    private void checkUserRoleAndRedirect(String employeeDocId) {
        db.collection("employees").document(employeeDocId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                String role = document.getString("role");
                                Boolean isActive = document.getBoolean("isActive");

                                if (isActive != null && !isActive) {
                                    Toast.makeText(LoginActivity.this,
                                            "Your account has been deactivated. Please contact admin.",
                                            Toast.LENGTH_LONG).show();
                                    mAuth.signOut();
                                    return;
                                }

                                // Store employee doc ID for future use
                                getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                                        .edit()
                                        .putString("employee_doc_id", employeeDocId)
                                        .apply();

                                if ("Admin".equalsIgnoreCase(role) || "Manager".equalsIgnoreCase(role)) {
                                    redirectToAdminDashboard();
                                } else {
                                    redirectToEmployeeDashboard();
                                }
                            } else {
                                Toast.makeText(LoginActivity.this,
                                        "Employee data not found. Please contact admin.",
                                        Toast.LENGTH_LONG).show();
                                mAuth.signOut();
                            }
                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "Error fetching employee data: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void redirectToAdminDashboard() {
        Intent intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void redirectToEmployeeDashboard() {
        Intent intent = new Intent(LoginActivity.this, EmployeeDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
        // Check if user is signed in and update UI accordingly
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String employeeDocId = getSharedPreferences("attendance_prefs", MODE_PRIVATE)
                    .getString("employee_doc_id", null);
            if (employeeDocId != null) {
                checkUserRoleAndRedirect(employeeDocId);
            }
        }
    }
}