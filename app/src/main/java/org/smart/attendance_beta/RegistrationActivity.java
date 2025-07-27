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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class RegistrationActivity extends AppCompatActivity {

    private EditText etPfNumber, etPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView tvLogin, tvEmployeeInfo;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize views
        initViews();
        setupClickListeners();
    }

    private void initViews() {
        etPfNumber = findViewById(R.id.et_pf_number);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        tvLogin = findViewById(R.id.tv_login);
        tvEmployeeInfo = findViewById(R.id.tv_employee_info);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void setupClickListeners() {
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setupPassword();
            }
        });

        tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RegistrationActivity.this, LoginActivity.class));
                finish();
            }
        });

        // Check employee info when PF number is entered
        etPfNumber.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    String pfNumber = etPfNumber.getText().toString().trim();
                    if (!TextUtils.isEmpty(pfNumber)) {
                        checkEmployeeExists(pfNumber);
                    }
                }
            }
        });
    }

    private void checkEmployeeExists(String pfNumber) {
        db.collection("employees")
                .whereEqualTo("pfNumber", pfNumber.toUpperCase())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot employeeDoc = task.getResult().getDocuments().get(0);

                        // Check if already registered
                        Boolean hasPassword = employeeDoc.getBoolean("hasPassword");
                        if (hasPassword != null && hasPassword) {
                            tvEmployeeInfo.setText("❌ This employee has already set up their password. Please use Sign In.");
                            tvEmployeeInfo.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                            btnRegister.setEnabled(false);
                        } else {
                            // Show employee info
                            String name = employeeDoc.getString("name");
                            String department = employeeDoc.getString("department");
                            String role = employeeDoc.getString("role");

                            tvEmployeeInfo.setText("✅ Found: " + name + " | " + department + " | " + role);
                            tvEmployeeInfo.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                            btnRegister.setEnabled(true);
                        }
                        tvEmployeeInfo.setVisibility(View.VISIBLE);
                    } else {
                        tvEmployeeInfo.setText("❌ PF Number not found in company records. Please contact HR.");
                        tvEmployeeInfo.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        tvEmployeeInfo.setVisibility(View.VISIBLE);
                        btnRegister.setEnabled(false);
                    }
                });
    }

    private void setupPassword() {
        // Get input values
        String pfNumber = etPfNumber.getText().toString().trim().toUpperCase();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        // Validation
        if (!validateInputs(pfNumber, password, confirmPassword)) {
            return;
        }

        // Show loading
        setLoading(true);

        // Verify employee exists and setup password
        db.collection("employees")
                .whereEqualTo("pfNumber", pfNumber)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot employeeDoc = task.getResult().getDocuments().get(0);

                        // Check if already has password
                        Boolean hasPassword = employeeDoc.getBoolean("hasPassword");
                        if (hasPassword != null && hasPassword) {
                            setLoading(false);
                            Toast.makeText(this, "You have already set up your password. Please use Sign In.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        // Create Firebase auth account
                        createFirebaseAccount(employeeDoc, password);
                    } else {
                        setLoading(false);
                        Toast.makeText(this, "PF Number not found. Please contact HR.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void createFirebaseAccount(DocumentSnapshot employeeDoc, String password) {
        String pfNumber = employeeDoc.getString("pfNumber");
        String email = pfNumber.toLowerCase() + "@company.internal"; // Generate internal email

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Registration success, update employee document
                            updateEmployeeDocument(employeeDoc.getId(), email);
                        } else {
                            // Registration failed
                            setLoading(false);
                            String errorMessage = "Password setup failed";
                            if (task.getException() != null) {
                                String exception = task.getException().getMessage();
                                if (exception != null && exception.contains("already in use")) {
                                    errorMessage = "Account already exists. Please use Sign In.";
                                } else {
                                    errorMessage = task.getException().getMessage();
                                }
                            }
                            Toast.makeText(RegistrationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void updateEmployeeDocument(String employeeDocId, String email) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("email", email);
        updates.put("hasPassword", true);
        updates.put("registeredAt", com.google.firebase.Timestamp.now());
        updates.put("isActive", true);

        db.collection("employees").document(employeeDocId)
                .update(updates)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        setLoading(false);
                        showSuccessMessage();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        setLoading(false);
                        Toast.makeText(RegistrationActivity.this,
                                "Failed to complete registration: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean validateInputs(String pfNumber, String password, String confirmPassword) {
        // Clear previous errors
        clearErrors();

        boolean isValid = true;

        // PF Number validation
        if (TextUtils.isEmpty(pfNumber)) {
            etPfNumber.setError("PF Number is required");
            etPfNumber.requestFocus();
            isValid = false;
        } else if (pfNumber.length() < 3) {
            etPfNumber.setError("PF Number must be at least 3 characters");
            etPfNumber.requestFocus();
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
        etPfNumber.setError(null);
        etPassword.setError(null);
        etConfirmPassword.setError(null);
    }

    private boolean isStrongPassword(String password) {
        // Check if password contains at least one letter and one number
        return Pattern.matches(".*[a-zA-Z].*", password) &&
                Pattern.matches(".*[0-9].*", password);
    }

    private void showSuccessMessage() {
        Toast.makeText(this,
                "Password setup successful! You can now sign in with your PF Number.",
                Toast.LENGTH_LONG).show();

        // Redirect to login after a short delay
        etPfNumber.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(RegistrationActivity.this, LoginActivity.class);
                intent.putExtra("pf_number", etPfNumber.getText().toString());
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