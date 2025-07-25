package org.smart.attendance_beta;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class RegistrationActivity extends AppCompatActivity {

    private EditText etFullName, etEmployeeId, etEmail, etPhone, etPassword, etConfirmPassword;
    private Spinner spDepartment;
    private Button btnRegister;
    private TextView tvLogin;
    private CheckBox cbTerms;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String[] departments = {
            "Select Department",
            "Engineering",
            "Marketing",
            "Sales",
            "Human Resources",
            "Finance",
            "Operations",
            "Customer Service",
            "IT Support",
            "Administration"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        initViews();
        setupDepartmentSpinner();
        setupClickListeners();
    }

    private void initViews() {
        etFullName = findViewById(R.id.et_full_name);
        etEmployeeId = findViewById(R.id.et_employee_id);
        etEmail = findViewById(R.id.et_email);
        etPhone = findViewById(R.id.et_phone);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        spDepartment = findViewById(R.id.sp_department);
        btnRegister = findViewById(R.id.btn_register);
        tvLogin = findViewById(R.id.tv_login);
        cbTerms = findViewById(R.id.cb_terms);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void setupDepartmentSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, departments);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDepartment.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });

        tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RegistrationActivity.this, LoginActivity.class));
                finish();
            }
        });
    }

    private void registerUser() {
        // Get input values
        String fullName = etFullName.getText().toString().trim();
        String employeeId = etEmployeeId.getText().toString().trim().toUpperCase();
        String email = etEmail.getText().toString().trim().toLowerCase();
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();
        String department = spDepartment.getSelectedItem().toString();

        // Validation
        if (!validateInputs(fullName, employeeId, email, phone, password, confirmPassword, department)) {
            return;
        }

        // Check if terms are accepted
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Please accept the Terms of Service", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading
        setLoading(true);

        // Check if employee ID already exists
        checkEmployeeIdExists(employeeId, email, password, fullName, phone, department);
    }

    private boolean validateInputs(String fullName, String employeeId, String email,
                                   String phone, String password, String confirmPassword,
                                   String department) {

        // Clear previous errors
        clearErrors();

        boolean isValid = true;

        // Full Name validation
        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            isValid = false;
        } else if (fullName.length() < 2) {
            etFullName.setError("Full name must be at least 2 characters");
            etFullName.requestFocus();
            isValid = false;
        }

        // Employee ID validation
        if (TextUtils.isEmpty(employeeId)) {
            etEmployeeId.setError("Employee ID is required");
            if (isValid) etEmployeeId.requestFocus();
            isValid = false;
        } else if (employeeId.length() < 3) {
            etEmployeeId.setError("Employee ID must be at least 3 characters");
            if (isValid) etEmployeeId.requestFocus();
            isValid = false;
        }

        // Email validation
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            if (isValid) etEmail.requestFocus();
            isValid = false;
        } else if (!isValidEmail(email)) {
            etEmail.setError("Please enter a valid email address");
            if (isValid) etEmail.requestFocus();
            isValid = false;
        }

        // Phone validation
        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Phone number is required");
            if (isValid) etPhone.requestFocus();
            isValid = false;
        } else if (!isValidPhone(phone)) {
            etPhone.setError("Please enter a valid phone number");
            if (isValid) etPhone.requestFocus();
            isValid = false;
        }

        // Department validation
        if (department.equals("Select Department")) {
            Toast.makeText(this, "Please select a department", Toast.LENGTH_SHORT).show();
            isValid = false;
        }

        // Password validation
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            if (isValid) etPassword.requestFocus();
            isValid = false;
        } else if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            if (isValid) etPassword.requestFocus();
            isValid = false;
        } else if (!isStrongPassword(password)) {
            etPassword.setError("Password must contain letters and numbers");
            if (isValid) etPassword.requestFocus();
            isValid = false;
        }

        // Confirm password validation
        if (TextUtils.isEmpty(confirmPassword)) {
            etConfirmPassword.setError("Please confirm your password");
            if (isValid) etConfirmPassword.requestFocus();
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            if (isValid) etConfirmPassword.requestFocus();
            isValid = false;
        }

        return isValid;
    }

    private void clearErrors() {
        etFullName.setError(null);
        etEmployeeId.setError(null);
        etEmail.setError(null);
        etPhone.setError(null);
        etPassword.setError(null);
        etConfirmPassword.setError(null);
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidPhone(String phone) {
        // Remove spaces, hyphens, and parentheses
        String cleanPhone = phone.replaceAll("[\\s\\-\\(\\)]", "");
        // Check if it's a valid phone number (10-15 digits, optionally starting with +)
        return Pattern.matches("^(\\+)?[0-9]{10,15}$", cleanPhone);
    }

    private boolean isStrongPassword(String password) {
        // Check if password contains at least one letter and one number
        return Pattern.matches(".*[a-zA-Z].*", password) &&
                Pattern.matches(".*[0-9].*", password);
    }

    private void checkEmployeeIdExists(String employeeId, String email, String password,
                                       String fullName, String phone, String department) {
        db.collection("users")
                .whereEqualTo("employeeId", employeeId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (!task.getResult().isEmpty()) {
                                // Employee ID already exists
                                setLoading(false);
                                etEmployeeId.setError("Employee ID already exists");
                                etEmployeeId.requestFocus();
                                Toast.makeText(RegistrationActivity.this,
                                        "Employee ID already exists. Please use a different ID.",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                // Employee ID is unique, check email
                                checkEmailExists(employeeId, email, password, fullName, phone, department);
                            }
                        } else {
                            setLoading(false);
                            Toast.makeText(RegistrationActivity.this,
                                    "Error checking employee ID: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void checkEmailExists(String employeeId, String email, String password,
                                  String fullName, String phone, String department) {
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            if (!task.getResult().isEmpty()) {
                                // Email already exists
                                setLoading(false);
                                etEmail.setError("Email already registered");
                                etEmail.requestFocus();
                                Toast.makeText(RegistrationActivity.this,
                                        "Email already registered. Please use a different email or try logging in.",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                // Email is unique, proceed with registration
                                createUserAccount(email, password, fullName, employeeId, phone, department);
                            }
                        } else {
                            setLoading(false);
                            Toast.makeText(RegistrationActivity.this,
                                    "Error checking email: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void createUserAccount(String email, String password, String fullName,
                                   String employeeId, String phone, String department) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Registration success
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                // Send verification email first
                                sendVerificationEmail(user, fullName, employeeId, email, phone, department);
                            }
                        } else {
                            // Registration failed
                            setLoading(false);
                            String errorMessage = "Registration failed";
                            if (task.getException() != null) {
                                errorMessage = task.getException().getMessage();
                            }
                            Toast.makeText(RegistrationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void sendVerificationEmail(FirebaseUser user, String fullName, String employeeId,
                                       String email, String phone, String department) {
        user.sendEmailVerification()
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            // Email sent successfully, now save user data
                            saveUserDataToFirestore(user.getUid(), fullName, employeeId,
                                    email, phone, department);
                        } else {
                            setLoading(false);
                            Toast.makeText(RegistrationActivity.this,
                                    "Failed to send verification email: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void saveUserDataToFirestore(String userId, String fullName, String employeeId,
                                         String email, String phone, String department) {
        // Create user data map
        Map<String, Object> userData = new HashMap<>();
        userData.put("employeeId", employeeId);
        userData.put("fullName", fullName);
        userData.put("email", email);
        userData.put("phone", phone);
        userData.put("department", department);
        userData.put("role", "employee"); // Default role
        userData.put("isActive", true);
        userData.put("createdAt", com.google.firebase.Timestamp.now());
        userData.put("loginMethod", "email");
        userData.put("profileCompleted", true);

        // Save to Firestore
        db.collection("users").document(userId)
                .set(userData)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        setLoading(false);

                        // Sign out user temporarily until they verify email
                        mAuth.signOut();

                        showSuccessMessage(email);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        setLoading(false);

                        Toast.makeText(RegistrationActivity.this,
                                "Failed to save user data: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showSuccessMessage(String email) {
        Toast.makeText(this,
                "Registration successful! Please check your email (" + email + ") and verify your account before logging in.",
                Toast.LENGTH_LONG).show();

        // Redirect to login after a short delay
        etEmail.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(RegistrationActivity.this, LoginActivity.class);
                intent.putExtra("registered_email", email);
                startActivity(intent);
                finish();
            }
        }, 2000);
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnRegister.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            btnRegister.setEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(RegistrationActivity.this, LoginActivity.class));
        finish();
    }
}